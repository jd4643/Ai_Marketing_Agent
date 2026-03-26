package com.marketing.creative.service;
import com.fasterxml.jackson.core.type.TypeReference;import com.fasterxml.jackson.databind.ObjectMapper;import com.marketing.creative.api.CreativeController.GenerateRequest;
import java.sql.*;import java.sql.Connection;import java.time.Instant;import java.time.temporal.ChronoUnit;import java.util.*;import java.util.stream.Collectors;import javax.sql.DataSource;
import okhttp3.*;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;
@Service
public class CreativeService {
 private static final Logger log = LoggerFactory.getLogger(CreativeService.class);
 private final DataSource ds; private final ObjectMapper om=new ObjectMapper(); private final OkHttpClient client;
 @Value("${openai.api-key:}") String apiKey; @Value("${openai.model:gpt-4o-mini}") String model;
 public CreativeService(DataSource ds,@Value("${openai.timeout-seconds:15}") int timeout){this.ds=ds;this.client=new OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(timeout)).build();}
 public Map<String,Object> generate(GenerateRequest req, UUID requestId){
  Map<String,Object> business=business(req.businessId()); if(business.isEmpty()) throw new IllegalArgumentException("businessId not found");
  List<String> trends=req.trendsOverride()!=null&&!req.trendsOverride().isEmpty()?req.trendsOverride():trends((String)business.get("industry"));
  List<Map<String,Object>> winners=winners(req.businessId());
  List<Map<String,Object>> assetWinnersList=assetWinners(req.businessId());
  List<Map<String,Object>> assetLosersList=assetLosers(req.businessId());
  List<Map<String,Object>> openRecs=openRecommendations(req.businessId());
  String strategySummary=req.strategyRequestId()!=null?strategy(req.strategyRequestId()):"none";
  Map<String,Object> out;
  try{out=openai(req,requestId,business,trends,winners,strategySummary,assetWinnersList,assetLosersList,openRecs);}catch(Exception e){
    log.warn("OpenAI creative generation failed, using fallback: {}", e.getMessage());
    out=fallback(req,requestId,trends,business);
  }
  enrichConceptsForGeneration(out, req.platform(), req.format());
  // Add winner signals metadata
  if (!assetWinnersList.isEmpty()) {
    out.put("basedOnWinners", true);
    out.put("winnerSignalsUsed", assetWinnersList.size());
  } else {
    out.put("basedOnWinners", false);
    out.put("winnerSignalsUsed", 0);
  }
  if (!assetLosersList.isEmpty()) {
    out.put("avoidsWeakPatterns", true);
    out.put("weakPatternsAvoided", assetLosersList.size());
  } else {
    out.put("avoidsWeakPatterns", false);
  }
  storeCreative(req.businessId(),req.platform(),req.format(), ((List<Map<String,Object>>)out.get("creativeConcepts")).get(0).get("performanceAngle").toString(), ((List<Map<String,Object>>)out.get("creativeConcepts")).get(0).get("hook").toString());
  return out;
 }
 private Map<String,Object> openai(GenerateRequest req,UUID requestId,Map<String,Object> business,List<String> trends,List<Map<String,Object>> winners,String strategy,List<Map<String,Object>> assetWinners,List<Map<String,Object>> assetLosers,List<Map<String,Object>> openRecs) throws Exception {
  String prompt = buildCreativeDirectorPrompt(req, business, trends, winners, strategy, assetWinners, assetLosers, openRecs);
  if(apiKey==null||apiKey.isBlank()) throw new IllegalStateException("OpenAI API key not configured");
  String json=om.writeValueAsString(Map.of("model",model,"response_format",Map.of("type","json_object"),"messages",List.of(Map.of("role","system","content",SYSTEM_PROMPT),Map.of("role","user","content",prompt))));
  Request r=new Request.Builder().url("https://api.openai.com/v1/chat/completions").addHeader("Authorization","Bearer "+apiKey).post(RequestBody.create(json,MediaType.get("application/json"))).build();
  try(Response rs=client.newCall(r).execute()){
    if(!rs.isSuccessful()||rs.body()==null) throw new RuntimeException("OpenAI request failed");
    Map<String,Object> root=om.readValue(rs.body().string(),new TypeReference<>(){});
    String content=(String)((Map<String,Object>)((Map<String,Object>)((List<Object>)root.get("choices")).get(0)).get("message")).get("content");
    return parse(content,requestId);
  }
 }

 private static final String SYSTEM_PROMPT =
   "You are a senior creative director at a top performance marketing agency. " +
   "You produce creative briefs that are detailed enough for a designer or AI image generator to execute immediately. " +
   "Return strict JSON only. Every concept must include all specified fields. " +
   "Each aiImagePrompt must be a complete, visually descriptive generation prompt including scene, lighting, composition, mood, product placement, and style. " +
   "Each aiVideoPrompt must describe the video concept in enough detail to produce a 15-30 second ad.";

 private String buildCreativeDirectorPrompt(GenerateRequest req, Map<String,Object> business, List<String> trends, List<Map<String,Object>> winners, String strategy, List<Map<String,Object>> assetWinners, List<Map<String,Object>> assetLosers, List<Map<String,Object>> openRecs) {
   StringBuilder sb = new StringBuilder();
   sb.append("## BUSINESS CONTEXT\n");
   sb.append("Business: ").append(business.get("businessName")).append("\n");
   sb.append("Industry: ").append(business.get("industry")).append("\n");
   if (business.get("product") != null) sb.append("Product: ").append(business.get("product")).append("\n");
   if (business.get("targetAudience") != null) sb.append("Target audience: ").append(business.get("targetAudience")).append("\n");
   sb.append("Platform: ").append(req.platform()).append("\n");
   sb.append("Format: ").append(req.format()).append("\n");
   sb.append("Objective: ").append(req.objective()).append("\n\n");

   if (!trends.isEmpty()) {
     sb.append("## CURRENT TRENDS\n");
     sb.append(String.join(", ", trends)).append("\n\n");
   }

   if (!winners.isEmpty()) {
     sb.append("## TOP PERFORMING CREATIVES\n");
     for (Map<String,Object> w : winners) sb.append("- hook: ").append(w.get("hook")).append(" | angle: ").append(w.get("angle")).append(" | score: ").append(w.get("score")).append("\n");
     sb.append("\n");
   }

   if (!assetWinners.isEmpty()) {
     sb.append("## WINNING CREATIVE ASSETS (data-backed)\n");
     sb.append("These assets have proven performance. Use their patterns as inspiration:\n");
     for (Map<String,Object> aw : assetWinners) {
       sb.append("- asset: ").append(aw.get("assetId")).append(" | platform: ").append(aw.get("platform"));
       sb.append(" | ROAS: ").append(aw.get("avgRoas")).append(" | conversions: ").append(aw.get("conversions"));
       if (aw.get("hook") != null) sb.append(" | hook: ").append(aw.get("hook"));
       if (aw.get("visualStyle") != null) sb.append(" | style: ").append(aw.get("visualStyle"));
       if (aw.get("emotionalAngle") != null) sb.append(" | angle: ").append(aw.get("emotionalAngle"));
       sb.append("\n");
     }
     sb.append("Build on these winning patterns. Iterate and improve, don't copy.\n\n");
   }

   if (assetLosers != null && !assetLosers.isEmpty()) {
     sb.append("## WEAK CREATIVE ASSETS — AVOID THESE PATTERNS\n");
     sb.append("These assets underperformed. Avoid their hooks and visual styles:\n");
     for (Map<String,Object> al : assetLosers) {
       sb.append("- asset: ").append(al.get("assetId")).append(" | platform: ").append(al.get("platform"));
       sb.append(" | ROAS: ").append(al.get("avgRoas"));
       if (al.get("hook") != null) sb.append(" | weak hook: ").append(al.get("hook"));
       if (al.get("visualStyle") != null) sb.append(" | weak style: ").append(al.get("visualStyle"));
       sb.append("\n");
     }
     sb.append("Do NOT reuse these patterns. Find fresh angles instead.\n\n");
   }

   if (!"none".equals(strategy)) {
     sb.append("## STRATEGY CONTEXT\n").append(strategy).append("\n\n");
   }

   if (openRecs != null && !openRecs.isEmpty()) {
     sb.append("## OPEN OPTIMIZATION RECOMMENDATIONS\n");
     sb.append("The analytics engine has detected these actionable opportunities. Incorporate them:\n");
     for (Map<String,Object> r : openRecs) {
       sb.append("- [").append(r.get("type")).append("/").append(r.get("priority")).append("] ").append(r.get("title")).append("\n");
     }
     sb.append("\n");
   }

   sb.append("## OUTPUT REQUIREMENTS\n");
   sb.append("Return JSON with key 'creativeConcepts' as array of 3 concepts. Each concept object must have:\n");
   sb.append("- conceptName: unique creative concept name\n");
   sb.append("- platform: target platform\n");
   sb.append("- format: ad format\n");
   sb.append("- hook: attention-grabbing opening line\n");
   sb.append("- emotionalAngle: the core emotional trigger\n");
   sb.append("- visualStyle: cinematic | minimal | bold-graphic | UGC | lifestyle | editorial\n");
   sb.append("- productFocus: what product/service element is highlighted\n");
   sb.append("- sceneDescription: detailed visual scene layout\n");
   sb.append("- compositionNotes: framing, rule-of-thirds, focal point\n");
   sb.append("- lightingNotes: lighting style (natural, studio, golden hour, etc.)\n");
   sb.append("- brandTone: tone of voice for copy\n");
   sb.append("- primaryText: main ad copy\n");
   sb.append("- headline: short headline\n");
   sb.append("- cta: call to action\n");
   sb.append("- aiImagePrompt: complete image generation prompt (detailed enough for DALL-E or Midjourney)\n");
   sb.append("- aiVideoPrompt: complete video ad script/concept description\n");
   sb.append("- performanceAngle: the marketing psychology angle\n");
   sb.append("- trendUsed: which trend influenced this concept (or 'evergreen')\n");
   sb.append("- rationale: why this concept will perform well for this business\n");
   sb.append("\nAlso include a top-level 'notes' array with strategic observations.");
   return sb.toString();
 }

 public Map<String,Object> parse(String content, UUID requestId) throws Exception {
  Map<String,Object> obj=om.readValue(content,new TypeReference<>(){});
  obj.put("requestId",requestId.toString()); obj.putIfAbsent("creativeVersion","v1");
  return obj;
 }

 @SuppressWarnings("unchecked")
 private void enrichConceptsForGeneration(Map<String,Object> response, String platform, String format) {
   Object conceptsObj = response.get("creativeConcepts");
   if (!(conceptsObj instanceof List)) return;
   List<Map<String,Object>> concepts = (List<Map<String,Object>>) conceptsObj;
   for (Map<String,Object> concept : concepts) {
     // Ensure mutable map
     if (!(concept instanceof HashMap)) continue;

     // Ensure generation-ready fields exist
     concept.putIfAbsent("platform", platform);
     concept.putIfAbsent("format", format);
     concept.putIfAbsent("emotionalAngle", "aspirational");
     concept.putIfAbsent("visualStyle", "lifestyle");
     concept.putIfAbsent("productFocus", "main product");
     concept.putIfAbsent("sceneDescription", "Product in use, natural setting");
     concept.putIfAbsent("compositionNotes", "Center-focused, clean background");
     concept.putIfAbsent("lightingNotes", "Natural soft lighting");
     concept.putIfAbsent("brandTone", "confident and approachable");
     concept.putIfAbsent("rationale", "Performance-optimized creative concept");

     // Add generation metadata fields
     List<String> recommendedTypes = new ArrayList<>();
     recommendedTypes.add("image");
     if (concept.get("aiVideoPrompt") != null && !concept.get("aiVideoPrompt").toString().isBlank()) {
       recommendedTypes.add("video");
     }
     recommendedTypes.add("carousel");
     concept.putIfAbsent("recommendedAssetTypes", recommendedTypes);
     concept.putIfAbsent("generationReady", true);

     // Build generation payload example for frontend convenience
     Map<String,Object> payloadExample = new LinkedHashMap<>();
     payloadExample.put("assetType", "image");
     payloadExample.put("platform", concept.getOrDefault("platform", platform));
     if (concept.get("aiImagePrompt") != null) {
       payloadExample.put("prompt", concept.get("aiImagePrompt"));
     }
     Map<String,Object> metadataHint = new LinkedHashMap<>();
     metadataHint.put("conceptName", concept.get("conceptName"));
     metadataHint.put("hook", concept.get("hook"));
     metadataHint.put("headline", concept.get("headline"));
     metadataHint.put("cta", concept.get("cta"));
     metadataHint.put("visualStyle", concept.get("visualStyle"));
     metadataHint.put("emotionalAngle", concept.get("emotionalAngle"));
     metadataHint.put("sceneDescription", concept.get("sceneDescription"));
     metadataHint.put("compositionNotes", concept.get("compositionNotes"));
     metadataHint.put("lightingNotes", concept.get("lightingNotes"));
     metadataHint.put("brandTone", concept.get("brandTone"));
     metadataHint.put("productFocus", concept.get("productFocus"));
     payloadExample.put("metadata", metadataHint);
     concept.putIfAbsent("generationPayloadExample", payloadExample);
   }
 }

 private Map<String,Object> fallback(GenerateRequest req,UUID requestId,List<String> trends, Map<String,Object> business){
  String trendUsed = trends.isEmpty() ? "evergreen" : trends.get(0);
  String businessName = (String) business.getOrDefault("businessName", "the brand");
  String industry = (String) business.getOrDefault("industry", "retail");
  String product = (String) business.getOrDefault("product", "featured product");

  Map<String,Object> concept1 = new LinkedHashMap<>();
  concept1.put("conceptName", "Trend-led UGC for " + businessName);
  concept1.put("platform", req.platform());
  concept1.put("format", req.format());
  concept1.put("hook", "Stop scrolling — " + businessName + " just changed the game");
  concept1.put("emotionalAngle", "curiosity and social proof");
  concept1.put("visualStyle", "UGC");
  concept1.put("productFocus", product);
  concept1.put("sceneDescription", "Close-up of " + product + " being unboxed or used in a real lifestyle setting. Authentic, relatable environment.");
  concept1.put("compositionNotes", "Tight framing on product, shallow depth of field, person's hands visible");
  concept1.put("lightingNotes", "Natural daylight, warm tones, slightly overexposed for lifestyle feel");
  concept1.put("brandTone", "authentic, relatable, confident");
  concept1.put("primaryText", "Real people. Real results. See why " + businessName + " is trending in " + industry + ".");
  concept1.put("headline", "Make the switch today");
  concept1.put("cta", "Shop Now");
  concept1.put("aiImagePrompt", "Professional UGC-style product photography of " + product + " from " + businessName + ". Close-up shot, natural daylight, warm lifestyle setting, shallow depth of field, authentic unboxing moment. " + industry + " context. Commercial quality, brand-safe.");
  concept1.put("aiVideoPrompt", "15-second UGC-style video ad for " + businessName + ". Opens with close-up of " + product + ", transitions to person using it naturally, ends with satisfied reaction and CTA overlay. Warm natural lighting, authentic feel.");
  concept1.put("performanceAngle", "social proof");
  concept1.put("trendUsed", trendUsed);
  concept1.put("rationale", "UGC content consistently outperforms polished ads for " + industry + " businesses. Leveraging current trend '" + trendUsed + "' for relevance.");

  Map<String,Object> concept2 = new LinkedHashMap<>();
  concept2.put("conceptName", "Bold Value Stack for " + businessName);
  concept2.put("platform", req.platform());
  concept2.put("format", req.format());
  concept2.put("hook", "Here's why " + businessName + " is worth every penny");
  concept2.put("emotionalAngle", "aspiration and value justification");
  concept2.put("visualStyle", "minimal");
  concept2.put("productFocus", product);
  concept2.put("sceneDescription", "Clean flat lay of " + product + " with premium props arranged on a neutral surface. Minimal, editorial style.");
  concept2.put("compositionNotes", "Overhead flat lay, rule of thirds, negative space for text overlay");
  concept2.put("lightingNotes", "Soft studio lighting, even illumination, subtle shadows for depth");
  concept2.put("brandTone", "premium, confident, aspirational");
  concept2.put("primaryText", "Quality you can feel. " + businessName + " delivers on every detail.");
  concept2.put("headline", "Premium " + industry + " delivered");
  concept2.put("cta", "Learn More");
  concept2.put("aiImagePrompt", "Minimalist overhead flat lay product photography of " + product + " by " + businessName + ". Clean neutral background, premium styling props, soft studio lighting with subtle shadows. Editorial " + industry + " aesthetic. Commercial advertising quality.");
  concept2.put("aiVideoPrompt", "20-second product showcase for " + businessName + ". Smooth cinematic reveal of " + product + " with detail close-ups, premium lighting, ending with brand logo and CTA. Minimal style, aspirational tone.");
  concept2.put("performanceAngle", "value proposition");
  concept2.put("trendUsed", trends.size() > 1 ? trends.get(1) : trendUsed);
  concept2.put("rationale", "Value-forward messaging works well for mid-to-high ticket " + industry + " products. Clean visuals build brand credibility.");

  Map<String,Object> concept3 = new LinkedHashMap<>();
  concept3.put("conceptName", "Urgency-Driven Offer for " + businessName);
  concept3.put("platform", req.platform());
  concept3.put("format", req.format());
  concept3.put("hook", "Last chance — " + businessName + " deal ends tonight");
  concept3.put("emotionalAngle", "urgency and FOMO");
  concept3.put("visualStyle", "bold-graphic");
  concept3.put("productFocus", product);
  concept3.put("sceneDescription", "Bold graphic layout with " + product + " on a vibrant colored background. Large text overlay with countdown or limited-time messaging.");
  concept3.put("compositionNotes", "Product center frame, bold typography above and below, high contrast color blocks");
  concept3.put("lightingNotes", "Bright, even product lighting against punchy colored background");
  concept3.put("brandTone", "energetic, direct, urgent");
  concept3.put("primaryText", "Don't miss out — " + businessName + "'s biggest sale is almost over. Act now.");
  concept3.put("headline", "Flash Sale: " + industry + " favorites");
  concept3.put("cta", "Get the Deal");
  concept3.put("aiImagePrompt", "Bold graphic ad design for " + businessName + " featuring " + product + ". Vibrant gradient background, product hero shot center frame, large bold white text overlay 'LIMITED TIME'. High energy, commercial ad quality, " + industry + " context.");
  concept3.put("aiVideoPrompt", "12-second fast-paced promo video for " + businessName + ". Quick cuts: product reveal, price/offer flash, countdown timer, end with bold CTA. Vibrant colors, energetic music cue described, urgency-driven.");
  concept3.put("performanceAngle", "scarcity and urgency");
  concept3.put("trendUsed", trends.size() > 2 ? trends.get(2) : "evergreen");
  concept3.put("rationale", "Urgency-based creatives drive immediate action and are essential for conversion-focused campaigns in " + industry + ".");

  return new LinkedHashMap<>(Map.of(
    "requestId", requestId.toString(),
    "creativeVersion", "v1",
    "creativeConcepts", List.of(concept1, concept2, concept3),
    "notes", List.of(
      "Fallback creative blueprint used — OpenAI was unavailable.",
      "Three distinct performance angles provided: social proof, value proposition, urgency.",
      "All concepts include generation-ready image and video prompts.",
      "Trend '" + trendUsed + "' incorporated where relevant."
    )
  ));
 }
 private Map<String,Object> business(UUID id){try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT business_name,industry,product,target_audience FROM business_profile WHERE id=?")){ps.setObject(1,id); ResultSet rs=ps.executeQuery(); if(rs.next()){ Map<String,Object> m=new LinkedHashMap<>(); m.put("businessName",rs.getString(1)); m.put("industry",rs.getString(2)); m.put("product",rs.getString(3)); m.put("targetAudience",rs.getString(4)); return m; }}catch(Exception e){throw new RuntimeException(e);} return Map.of();}
 private List<String> trends(String industry){List<String> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT keyword FROM trends WHERE (industry=? OR industry IS NULL) AND captured_at>=? ORDER BY captured_at DESC LIMIT 10")){ps.setString(1,industry); ps.setTimestamp(2,Timestamp.from(Instant.now().minus(7,ChronoUnit.DAYS))); ResultSet rs=ps.executeQuery(); while(rs.next()) out.add(rs.getString(1));}catch(Exception e){throw new RuntimeException(e);} return out;}
 private List<Map<String,Object>> winners(UUID id){List<Map<String,Object>> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT hook,angle,performance_score FROM creatives WHERE business_id=? ORDER BY performance_score DESC NULLS LAST LIMIT 5")){ps.setObject(1,id); ResultSet rs=ps.executeQuery(); while(rs.next()){Map<String,Object> m=new LinkedHashMap<>(); m.put("hook",rs.getString(1)); m.put("angle",rs.getString(2)); m.put("score",rs.getObject(3)); out.add(m);}}catch(Exception e){throw new RuntimeException(e);} return out;}
 private List<Map<String,Object>> assetWinners(UUID businessId){
  List<Map<String,Object>> out=new ArrayList<>();
  try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement(
    "SELECT cap.creative_asset_id, cap.platform, COALESCE(SUM(cap.impressions),0) as impressions, " +
    "COALESCE(SUM(cap.clicks),0) as clicks, COALESCE(SUM(cap.conversions),0) as conversions, " +
    "COALESCE(AVG(cap.roas),0) as avg_roas, ca.metadata_json " +
    "FROM creative_asset_performance cap JOIN creative_assets ca ON ca.id=cap.creative_asset_id " +
    "WHERE cap.business_id=? AND cap.recorded_at>=? " +
    "GROUP BY cap.creative_asset_id, cap.platform, ca.metadata_json " +
    "HAVING COALESCE(SUM(cap.impressions),0) >= 3000 AND COALESCE(AVG(cap.roas),0) >= 2.0 " +
    "ORDER BY avg_roas DESC LIMIT 5")){
   ps.setObject(1,businessId);
   ps.setTimestamp(2,Timestamp.from(Instant.now().minus(30,ChronoUnit.DAYS)));
   ResultSet rs=ps.executeQuery();
   while(rs.next()){
    Map<String,Object> m=new LinkedHashMap<>();
    m.put("assetId",rs.getObject(1).toString());
    m.put("platform",rs.getString(2));
    m.put("impressions",rs.getLong(3));
    m.put("clicks",rs.getLong(4));
    m.put("conversions",rs.getLong(5));
    m.put("avgRoas",rs.getBigDecimal(6));
    String metaJson=rs.getString(7);
    if(metaJson!=null&&!metaJson.isBlank()){
     try{Map<String,Object> meta=om.readValue(metaJson,new TypeReference<>(){});
      m.put("hook",meta.get("hook")); m.put("visualStyle",meta.get("visualStyle")); m.put("emotionalAngle",meta.get("emotionalAngle"));
     }catch(Exception ignored){}
    }
    out.add(m);
   }
  }catch(Exception e){log.warn("Failed to query asset winners: {}",e.getMessage());}
  return out;
 }
 private List<Map<String,Object>> assetLosers(UUID businessId){
  List<Map<String,Object>> out=new ArrayList<>();
  try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement(
    "SELECT cap.creative_asset_id, cap.platform, COALESCE(SUM(cap.impressions),0) as impressions, " +
    "COALESCE(SUM(cap.clicks),0) as clicks, COALESCE(SUM(cap.conversions),0) as conversions, " +
    "COALESCE(AVG(cap.roas),0) as avg_roas, ca.metadata_json " +
    "FROM creative_asset_performance cap JOIN creative_assets ca ON ca.id=cap.creative_asset_id " +
    "WHERE cap.business_id=? AND cap.recorded_at>=? AND cap.classification='WEAK' " +
    "GROUP BY cap.creative_asset_id, cap.platform, ca.metadata_json " +
    "ORDER BY avg_roas ASC LIMIT 5")){
   ps.setObject(1,businessId);
   ps.setTimestamp(2,Timestamp.from(Instant.now().minus(30,ChronoUnit.DAYS)));
   ResultSet rs=ps.executeQuery();
   while(rs.next()){
    Map<String,Object> m=new LinkedHashMap<>();
    m.put("assetId",rs.getObject(1).toString());
    m.put("platform",rs.getString(2));
    m.put("impressions",rs.getLong(3));
    m.put("clicks",rs.getLong(4));
    m.put("conversions",rs.getLong(5));
    m.put("avgRoas",rs.getBigDecimal(6));
    String metaJson=rs.getString(7);
    if(metaJson!=null&&!metaJson.isBlank()){
     try{Map<String,Object> meta=om.readValue(metaJson,new TypeReference<>(){});
      m.put("hook",meta.get("hook")); m.put("visualStyle",meta.get("visualStyle"));
     }catch(Exception ignored){}
    }
    out.add(m);
   }
  }catch(Exception e){log.warn("Failed to query asset losers: {}",e.getMessage());}
  return out;
 }
 private String strategy(UUID rid){ try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT response_json::text FROM strategy_history WHERE request_id=? ORDER BY created_at DESC LIMIT 1")){ps.setObject(1,rid); ResultSet rs=ps.executeQuery(); if(rs.next()) return rs.getString(1);}catch(Exception e){throw new RuntimeException(e);} return "none"; }
 private void storeCreative(UUID businessId,String platform,String format,String angle,String hook){ try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("INSERT INTO creatives(id,business_id,platform,format,angle,hook,performance_score,created_at) VALUES (?,?,?,?,?,?,NULL,?)")){ps.setObject(1,UUID.randomUUID());ps.setObject(2,businessId);ps.setString(3,platform);ps.setString(4,format);ps.setString(5,angle);ps.setString(6,hook);ps.setTimestamp(7,Timestamp.from(Instant.now())); ps.executeUpdate();}catch(Exception e){throw new RuntimeException(e);} }
 private List<Map<String,Object>> openRecommendations(UUID businessId){
  List<Map<String,Object>> out=new ArrayList<>();
  try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement(
    "SELECT recommendation_type, priority, title, creative_asset_id FROM creative_optimization_recommendations " +
    "WHERE business_id=? AND status='OPEN' ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, created_at DESC LIMIT 10")){
   ps.setObject(1,businessId);
   ResultSet rs=ps.executeQuery();
   while(rs.next()){
    Map<String,Object> m=new LinkedHashMap<>();
    m.put("type",rs.getString(1));
    m.put("priority",rs.getString(2));
    m.put("title",rs.getString(3));
    Object assetId=rs.getObject(4);
    if(assetId!=null) m.put("assetId",assetId.toString());
    out.add(m);
   }
  }catch(Exception e){log.warn("Failed to query open recommendations: {}",e.getMessage());}
  return out;
 }
}

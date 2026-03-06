package com.marketing.strategy.service;
import com.fasterxml.jackson.core.type.TypeReference;import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.strategy.model.*;
import com.marketing.strategy.service.intel.*;
import java.math.BigDecimal;import java.sql.*;import java.time.Instant;import java.time.temporal.ChronoUnit;import java.util.*;import javax.sql.DataSource;
import okhttp3.*;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;

@Service
public class StrategyService {
 private static final Logger log=LoggerFactory.getLogger(StrategyService.class);
 private final DataSource ds; private final ObjectMapper om=new ObjectMapper(); private final OkHttpClient client;
 private final StrategyTemplateRepository templateRepository; private final StrategyRunIntelRepository intelRepository;
 private final DecisionTreeSelector decisionTreeSelector; private final ConfidenceScorer confidenceScorer; private final PatternMatcher patternMatcher; private final IntelPromptBuilder promptBuilder; private final StrategyOutputValidator outputValidator;
 @Value("${openai.api-key:}") String apiKey; @Value("${openai.model:gpt-4o-mini}") String model;
 public StrategyService(DataSource ds,@Value("${openai.timeout-seconds:15}") int timeout,
   StrategyTemplateRepository templateRepository, StrategyRunIntelRepository intelRepository,
   DecisionTreeSelector decisionTreeSelector, ConfidenceScorer confidenceScorer, PatternMatcher patternMatcher, IntelPromptBuilder promptBuilder, StrategyOutputValidator outputValidator){
   this.ds=ds; this.templateRepository=templateRepository; this.intelRepository=intelRepository;
   this.decisionTreeSelector=decisionTreeSelector; this.confidenceScorer=confidenceScorer; this.patternMatcher=patternMatcher; this.promptBuilder=promptBuilder; this.outputValidator=outputValidator;
   this.client=new OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(timeout)).build();
 }
 public StrategyResponse generate(StrategyRequest req, UUID requestId){
  Map<String,Object> business=getBusiness(req.businessId());
  if(business.isEmpty()) throw new IllegalArgumentException("businessId not found");
  List<Map<String,Object>> metrics=metrics(req.businessId()); List<Map<String,Object>> winners=winners(req.businessId());
  List<String> trends = req.trends()!=null && !req.trends().isEmpty() ? req.trends() : trendsByIndustry((String) business.get("industry"));
  String perfSummary = buildPerfSummary(metrics,winners);
  Map<String,BigDecimal> split=deterministicSplit(req.monthlyBudget(), metrics);
  boolean coldStart = metrics.isEmpty();

  DecisionTreeSelector.SelectionResult selection = decisionTreeSelector.select(business, req.objective(), req.monthlyBudget());
  String chosenTemplateKey=selection.templateKey();

  StrategyRunIntelEntity latest = latestIntel(req.businessId());
  Map<String,Object> latestPerf = latest==null ? Map.of() : performanceForRequest(req.businessId(), latest.getRequestId());
  Optional<PatternMatcher.MatchResult> match = patternMatcher.match(chosenTemplateKey, req.objective(), (String)business.get("industry"), selection.motion(), selection.budgetTier(),
      metricRoas(metrics), metricConversions(metrics), latest, latestPerf);
  if(match.isPresent()) chosenTemplateKey = match.get().templateKey();

  ConfidenceScorer.ConfidenceResult confidence = confidenceScorer.score(selection.motion(), req.objective()==null?"sales":req.objective().toLowerCase(), trends.size(), metricConversions(metrics));

  saveIntel(requestId, req, chosenTemplateKey, selection.decisionPath(), confidence, match.map(PatternMatcher.MatchResult::similarityMatch).orElse(null));

  StrategyTemplateEntity template = templateRepository.findByTemplateKey(chosenTemplateKey)
      .orElseThrow(() -> new IllegalStateException("strategy template missing: "+chosenTemplateKey));
  String prompt=promptBuilder.build(
      business,
      req.objective(),
      req.monthlyBudget(),
      template,
      metrics,
      winners,
      trends,
      confidence.confidenceScore(),
      selection.decisionPath(),
      match.map(PatternMatcher.MatchResult::similarityMatch).orElse(null),
      coldStart
  );

  Map<String,Object> llmResp;
  long start=System.currentTimeMillis();
  try {
    llmResp = callOpenAi(prompt);
    llmResp = normalizeOrFallback(llmResp, requestId, split);
    if (outputValidator.isGeneric(llmResp, business)) {
      String strongerPrompt = prompt + "\nVALIDATION FAILURE: previous output was too generic. Rebuild with explicit business-specific detail and all consultant sections.";
      llmResp = normalizeOrFallback(callOpenAi(strongerPrompt), requestId, split);
      if (outputValidator.isGeneric(llmResp, business)) {
        llmResp = fallback(requestId,split,template,confidence.confidenceScore(),selection.decisionPath(),business,trends,coldStart, match.map(PatternMatcher.MatchResult::similarityMatch).orElse(null));
      }
    }
  } catch(Exception ex){
    llmResp = fallback(requestId,split,template,confidence.confidenceScore(),selection.decisionPath(),business,trends,coldStart, match.map(PatternMatcher.MatchResult::similarityMatch).orElse(null));
    saveHistory(requestId,req,prompt,llmResp,"FAILED","OPENAI_ERROR",ex.getMessage(),System.currentTimeMillis()-start);
    return toResponse(llmResp);
  }
  saveHistory(requestId,req,prompt,llmResp,"SUCCESS",null,null,System.currentTimeMillis()-start);
  return toResponse(llmResp);
 }
 public String buildPerfSummary(List<Map<String,Object>> metrics,List<Map<String,Object>> winners){return "metrics="+metrics+" winners="+winners;}
 private Map<String,BigDecimal> deterministicSplit(BigDecimal budget,List<Map<String,Object>> metrics){
  if(budget.compareTo(new BigDecimal("500"))<0) return Map.of("meta",budget,"google",BigDecimal.ZERO,"tiktok",BigDecimal.ZERO,"youtube",BigDecimal.ZERO);
  return Map.of("meta",budget.multiply(new BigDecimal("0.5")),"google",budget.multiply(new BigDecimal("0.3")),"tiktok",budget.multiply(new BigDecimal("0.2")),"youtube",BigDecimal.ZERO);
 }
 private Map<String,Object> callOpenAi(String prompt) throws Exception {
  if(apiKey==null||apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY missing");
  String json=om.writeValueAsString(Map.of("model",model,"response_format",Map.of("type","json_object"),"messages",List.of(Map.of("role","system","content","You are a marketing strategist. Return only JSON."),Map.of("role","user","content",prompt))));
  Request req=new Request.Builder().url("https://api.openai.com/v1/chat/completions").addHeader("Authorization","Bearer "+apiKey).post(RequestBody.create(json,MediaType.get("application/json"))).build();
  for(int i=0;i<4;i++){
   try(Response r=client.newCall(req).execute()){
    if(r.isSuccessful()&&r.body()!=null){
      Map<String,Object> root=om.readValue(r.body().string(),new TypeReference<>(){});
      String content=(String)((Map<String,Object>)((List<Object>)root.get("choices")).get(0) instanceof Map ? ((Map<String,Object>)((Map<String,Object>)((List<Object>)root.get("choices")).get(0)).get("message")).get("content") :"{}");
      return om.readValue(content,new TypeReference<>(){});
    }
    if(r.code()==429||r.code()>=500){ Thread.sleep((long)(Math.pow(2,i)*200+Math.random()*100)); continue; }
    throw new RuntimeException("OpenAI failed code="+r.code());
   } catch(java.net.SocketTimeoutException e){ Thread.sleep((long)(Math.pow(2,i)*200)); }
  }
  throw new RuntimeException("OpenAI retries exhausted");
 }
 private Map<String,Object> fallback(UUID requestId,Map<String,BigDecimal> split, StrategyTemplateEntity template, int confidence, List<Map<String,Object>> decisionPath, Map<String,Object> business, List<String> trends, boolean coldStart, Map<String,Object> similarityMatch){
  List<Map<String,Object>> roadmap = List.of(
      Map.of("week","Week 1","actions",List.of("Tracking + pixel setup","Launch first prospecting campaign","Publish 2 core creatives"),"measure",List.of("CTR","CPC","landing page engagement")),
      Map.of("week","Week 2","actions",List.of("Add retargeting","Test 2 new hooks","Refine audience exclusions"),"measure",List.of("lead quality","ATC rate","CPL")),
      Map.of("week","Week 3","actions",List.of("Shift 15% budget to winner ad sets","Pause low quality segments","Improve landing page proof"),"measure",List.of("CVR","ROAS trend")),
      Map.of("week","Week 4","actions",List.of("Scale proven winners by 20%","Add fresh creative variations","Document SOP"),"measure",List.of("stable CPA/ROAS","volume growth"))
  );
  List<String> checklist = new ArrayList<>(List.of("Install conversion tracking and verify events","Connect CRM/WhatsApp follow-up flow","Prepare landing page with trust signals","Configure retargeting audiences","Set UTM naming standard"));
  if (String.valueOf(business.get("websiteUrl")).isBlank()) checklist.add("Optimize Google Business Profile and call tracking");
  return new HashMap<>(Map.of(
      "requestId",requestId.toString(),
      "strategyVersion","v1",
      "platformBudgetSplit",split,
      "campaignPlan",List.of(Map.of("platform","meta","dailyBudget",split.get("meta").divide(new BigDecimal("30"), java.math.RoundingMode.HALF_UP),"templateKey",template.getTemplateKey())),
      "funnelStrategy","Deterministic consultant fallback",
      "expectedCPL","Conservative range",
      "expectedROAS","Conservative range",
      "reasoning","Fallback strategy generated from deterministic template and business context.",
      "assumptions",List.of("No reliable model output","Template="+template.getTemplateKey()),
      "businessSnapshot",Map.of("businessName",String.valueOf(business.get("businessName")),"industry",String.valueOf(business.get("industry")),"websitePresence",String.valueOf(business.get("websiteUrl"))),
      "marketAnalysis",Map.of("demandContext",trends.isEmpty()?"No major trend spikes detected":"Recent trend signals suggest active demand","seasonalConsiderations",trends),
      "customerPersona",Map.of("primaryBuyer","Value-conscious buyer in target audience","motivations",List.of("Trust","Price-value","Convenience"),"painPoints",List.of("Uncertainty","Choice overload"),"triggers",List.of("Social proof","Limited-time offer")),
      "whyThisStrategy",Map.of("primaryPlatformsWhy",List.of("Selected template aligns with business model and objective"),"lowerPriorityPlatformsWhy",List.of("Budget focus to reduce fragmentation")),
      "platformStrategy",List.of(Map.of("platform","meta","whyChosen","Strong discovery + retargeting coverage","campaignObjective", "sales","budgetAllocation", split.get("meta"),"runDuration","4 weeks","audienceType","Prospecting + warm retargeting","creativeFormat","UGC + testimonial","successMetric","Cost per qualified conversion")),
      "campaignArchitecture",Map.of("meta",Map.of("campaignThemes",List.of("Problem-solution","Offer-led"),"audienceLogic",List.of("Interest stack","Engagers retargeting"))),
      "creativeStrategy",Map.of("angles",List.of("Problem-solution","Proof","Offer"),"hooks",List.of("Stop wasting budget","See real results in weeks"),"messagingMix",Map.of("emotional",40,"offerLed",60),"contentTypes",List.of("short video","image testimonial","offer carousel")),
      "creativesNeeded",List.of(Map.of("assetType","Video","spec","15-30s UGC demo"),Map.of("assetType","Image","spec","Before-after or proof card"),Map.of("assetType","Carousel","spec","Offer + FAQ + CTA")),
      "executionRoadmap",roadmap,
      "setupChecklist",checklist,
      "landingPageRecommendations",Map.of("destination","Single focused offer page","mustHave",List.of("clear headline","social proof","strong CTA","FAQ","fast contact options")),
      "offerStrategy",Map.of("primaryOffer","Starter incentive + urgency window","cta","Book now / Shop now"),
      "measurementPlan",Map.of("kpiTargets",Map.of("ctr","improving week-over-week","cpl","within conservative target range"),"evaluationWindows",List.of("Day 3 creative check","Day 7 audience check","Day 14 structure check"),"stopLossRules",List.of("Pause ad sets with high spend and no conversions after threshold"),"scalingRules",List.of("Increase budget 15-20% on winners every 3-4 days")),
      "risksAndMitigations",List.of(Map.of("risk","Low conversion quality","mitigation","Refine audience and qualifying copy"),Map.of("risk","Creative fatigue","mitigation","Rotate hooks weekly")),
      "first14DaysLearningPlan",List.of(Map.of("days","1-3","focus","Data integrity and CTR baselines"),Map.of("days","4-7","focus","Audience and creative pruning"),Map.of("days","8-14","focus","Budget reallocation to winners")),
      "humanReadablePlanMarkdown","## 30-Day Consultant Plan\nThis strategy is tailored for **"+String.valueOf(business.get("businessName"))+"** in **"+String.valueOf(business.get("industry"))+"**.\n\nStart with focused channels, test structured creatives, and optimize weekly with clear stop-loss/scaling rules." 
  ));
 }


 public Map<String,Object> normalizeOrFallback(Map<String,Object> llmResp, UUID requestId, Map<String,BigDecimal> split){
  if (llmResp == null || llmResp.isEmpty()) {
    return new HashMap<>(Map.of("requestId",requestId.toString(),"strategyVersion","v1","platformBudgetSplit",split,"campaignPlan",List.of(),"funnelStrategy","Conservative optimization based on available data","expectedCPL","Unknown","expectedROAS","Unknown","reasoning","Generated strategy with guarded defaults","assumptions",List.of("Empty model response")));
  }
  if (llmResp.get("platformBudgetSplit") == null || llmResp.get("campaignPlan") == null) {
    Map<String,Object> m = new HashMap<>(llmResp);
    m.put("requestId",requestId.toString());
    m.put("strategyVersion","v1");
    m.put("platformBudgetSplit",split);
    m.put("campaignPlan",List.of());
    m.putIfAbsent("assumptions",List.of("Incomplete model schema"));
    return m;
  }
  Map<String,Object> normalized = new HashMap<>(llmResp);
  normalized.put("requestId", requestId.toString());
  normalized.putIfAbsent("strategyVersion", "v1");
  normalized.putIfAbsent("funnelStrategy", "Conservative optimization based on available data");
  normalized.putIfAbsent("expectedCPL", "Unknown");
  normalized.putIfAbsent("expectedROAS", "Unknown");
  normalized.putIfAbsent("reasoning", "Generated strategy with guarded defaults");
  normalized.putIfAbsent("assumptions", List.of());

  normalized.putIfAbsent("businessSnapshot", Map.of());
  normalized.putIfAbsent("marketAnalysis", Map.of());
  normalized.putIfAbsent("customerPersona", Map.of());
  normalized.putIfAbsent("whyThisStrategy", Map.of());
  normalized.putIfAbsent("platformStrategy", List.of());
  normalized.putIfAbsent("campaignArchitecture", Map.of());
  normalized.putIfAbsent("creativeStrategy", Map.of());
  normalized.putIfAbsent("creativesNeeded", List.of());
  normalized.putIfAbsent("executionRoadmap", List.of());
  normalized.putIfAbsent("setupChecklist", List.of());
  normalized.putIfAbsent("landingPageRecommendations", Map.of());
  normalized.putIfAbsent("offerStrategy", Map.of());
  normalized.putIfAbsent("measurementPlan", Map.of());
  normalized.putIfAbsent("risksAndMitigations", List.of());
  normalized.putIfAbsent("first14DaysLearningPlan", List.of());
  normalized.putIfAbsent("humanReadablePlanMarkdown", "");
  return normalized;
 }
 private StrategyResponse toResponse(Map<String,Object> m){
  return new StrategyResponse(
      UUID.fromString((String)m.get("requestId")),
      "v1",
      om.convertValue(m.get("platformBudgetSplit"),new TypeReference<>(){}),
      om.convertValue(m.get("campaignPlan"),new TypeReference<>(){}),
      (String)m.get("funnelStrategy"),
      (String)m.get("expectedCPL"),
      (String)m.get("expectedROAS"),
      (String)m.get("reasoning"),
      om.convertValue(m.get("assumptions"),new TypeReference<>(){}),
      om.convertValue(m.get("businessSnapshot"),new TypeReference<>(){}),
      om.convertValue(m.get("marketAnalysis"),new TypeReference<>(){}),
      om.convertValue(m.get("customerPersona"),new TypeReference<>(){}),
      om.convertValue(m.get("whyThisStrategy"),new TypeReference<>(){}),
      om.convertValue(m.get("platformStrategy"),new TypeReference<>(){}),
      om.convertValue(m.get("campaignArchitecture"),new TypeReference<>(){}),
      om.convertValue(m.get("creativeStrategy"),new TypeReference<>(){}),
      om.convertValue(m.get("creativesNeeded"),new TypeReference<>(){}),
      om.convertValue(m.get("executionRoadmap"),new TypeReference<>(){}),
      om.convertValue(m.get("setupChecklist"),new TypeReference<>(){}),
      om.convertValue(m.get("landingPageRecommendations"),new TypeReference<>(){}),
      om.convertValue(m.get("offerStrategy"),new TypeReference<>(){}),
      om.convertValue(m.get("measurementPlan"),new TypeReference<>(){}),
      om.convertValue(m.get("risksAndMitigations"),new TypeReference<>(){}),
      om.convertValue(m.get("first14DaysLearningPlan"),new TypeReference<>(){}),
      (String)m.get("humanReadablePlanMarkdown")
  );
 }
 private Map<String,Object> getBusiness(UUID id){ try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT business_name,industry,product,target_audience,website_url,price_range FROM business_profile WHERE id=?")){ps.setObject(1,id); ResultSet rs=ps.executeQuery(); if(rs.next()) { Map<String,Object> b=new HashMap<>(); b.put("businessName",rs.getString(1)); b.put("industry",rs.getString(2)); b.put("product",rs.getString(3)); b.put("targetAudience",rs.getString(4)); b.put("websiteUrl",rs.getString(5)); b.put("priceRange",rs.getString(6)); return b; } }catch(Exception e){throw new RuntimeException(e);} return Map.of(); }
 private List<Map<String,Object>> metrics(UUID id){List<Map<String,Object>> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT platform,COALESCE(SUM(spend),0),COALESCE(AVG(roas),0),COALESCE(SUM(conversions),0) FROM campaign_metrics WHERE business_id=? AND recorded_at>=? GROUP BY platform")){ps.setObject(1,id); ps.setTimestamp(2,Timestamp.from(Instant.now().minus(30,ChronoUnit.DAYS))); ResultSet rs=ps.executeQuery(); while(rs.next()) out.add(Map.of("platform",rs.getString(1),"spend",rs.getBigDecimal(2),"avgRoas",rs.getBigDecimal(3),"conversions",rs.getLong(4))); }catch(Exception e){throw new RuntimeException(e);} return out; }
 private List<Map<String,Object>> winners(UUID id){List<Map<String,Object>> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT platform,hook,angle,performance_score FROM creatives WHERE business_id=? ORDER BY performance_score DESC NULLS LAST LIMIT 3")){ps.setObject(1,id); ResultSet rs=ps.executeQuery(); while(rs.next()) out.add(Map.of("platform",rs.getString(1),"hook",rs.getString(2),"angle",rs.getString(3),"score",rs.getBigDecimal(4))); }catch(Exception e){throw new RuntimeException(e);} return out; }
 private List<String> trendsByIndustry(String industry){List<String> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT keyword FROM trends WHERE (industry=? OR industry IS NULL) AND captured_at>=? ORDER BY captured_at DESC LIMIT 10")){ps.setString(1,industry); ps.setTimestamp(2,Timestamp.from(Instant.now().minus(7,ChronoUnit.DAYS))); ResultSet rs=ps.executeQuery(); while(rs.next()) out.add(rs.getString(1));}catch(Exception e){throw new RuntimeException(e);} return out;}
 private StrategyRunIntelEntity latestIntel(UUID businessId){
  List<StrategyRunIntelEntity> rows=intelRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(businessId);
  return rows.isEmpty()?null:rows.get(0);
 }
 private Map<String,Object> performanceForRequest(UUID businessId, UUID requestId){
  try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT COALESCE(AVG(roas),0), COALESCE(SUM(conversions),0) FROM campaign_metrics WHERE business_id=? AND recorded_at>=?")){
    ps.setObject(1,businessId); ps.setTimestamp(2,Timestamp.from(Instant.now().minus(30,ChronoUnit.DAYS))); ResultSet rs=ps.executeQuery();
    if(rs.next()) return Map.of("roas", rs.getBigDecimal(1), "conversions", rs.getLong(2), "requestId", requestId.toString());
  }catch(Exception e){throw new RuntimeException(e);} return Map.of();
 }
 private double metricRoas(List<Map<String,Object>> metrics){ return metrics.stream().mapToDouble(m->Double.parseDouble(String.valueOf(m.get("avgRoas")))).average().orElse(0.0); }
 private long metricConversions(List<Map<String,Object>> metrics){ return metrics.stream().mapToLong(m->Long.parseLong(String.valueOf(m.get("conversions")))).sum(); }
 private void saveIntel(UUID requestId, StrategyRequest req, String key, List<Map<String,Object>> decisionPath, ConfidenceScorer.ConfidenceResult confidence, Map<String,Object> similarityMatch){
  try{
    StrategyRunIntelEntity e=new StrategyRunIntelEntity();
    e.setId(UUID.randomUUID()); e.setRequestId(requestId); e.setBusinessId(req.businessId()); e.setObjective(req.objective()); e.setMonthlyBudget(req.monthlyBudget());
    e.setChosenTemplateKey(key); e.setDecisionPathJson(om.writeValueAsString(decisionPath)); e.setConfidenceScore(confidence.confidenceScore());
    e.setScoreBreakdownJson(om.writeValueAsString(confidence.breakdown())); e.setSimilarityMatchJson(similarityMatch==null?null:om.writeValueAsString(similarityMatch)); e.setCreatedAt(Instant.now());
    intelRepository.save(e);
  }catch(Exception e){ log.error("intel save error",e); }
 }
 private void saveHistory(UUID requestId,StrategyRequest req,String prompt,Map<String,Object> response,String status,String code,String error,Long latency){
  try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("INSERT INTO strategy_history(id,request_id,business_id,objective,monthly_budget,trends_json,prompt_version,model_name,request_json,response_json,status,error_code,error_message,openai_latency_ms,created_at) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?)")){
    ps.setObject(1,UUID.randomUUID()); ps.setObject(2,requestId); ps.setObject(3,req.businessId()); ps.setString(4,req.objective()); ps.setBigDecimal(5,req.monthlyBudget()); ps.setString(6,om.writeValueAsString(req.trends()==null?List.of():req.trends())); ps.setString(7,"v1"); ps.setString(8,model); ps.setString(9,om.writeValueAsString(req)); ps.setString(10,om.writeValueAsString(response)); ps.setString(11,status); ps.setString(12,code); ps.setString(13,error); ps.setLong(14,latency); ps.setTimestamp(15,Timestamp.from(Instant.now())); ps.executeUpdate();
  }catch(Exception e){log.error("history save error",e);} }

 public List<StrategyIntelSummary> intelHistory(UUID businessId,int limit){
  return intelRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(businessId).stream().limit(limit)
    .map(i->new StrategyIntelSummary(i.getRequestId(), i.getChosenTemplateKey(), i.getConfidenceScore(), i.getMonthlyBudget(), i.getObjective(), i.getCreatedAt()))
    .toList();
 }
 public List<HistorySummary> history(UUID businessId,int limit){List<HistorySummary> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT request_id,objective,monthly_budget,status,created_at FROM strategy_history WHERE business_id=? ORDER BY created_at DESC LIMIT ?")){ps.setObject(1,businessId); ps.setInt(2,limit); ResultSet rs=ps.executeQuery(); while(rs.next()) out.add(new HistorySummary((UUID)rs.getObject(1),rs.getString(2),rs.getBigDecimal(3),rs.getString(4),rs.getTimestamp(5).toInstant()));}catch(Exception e){throw new RuntimeException(e);} return out; }
}

package com.marketing.creative.service;
import com.fasterxml.jackson.core.type.TypeReference;import com.fasterxml.jackson.databind.ObjectMapper;import com.marketing.creative.api.CreativeController.GenerateRequest;
import java.sql.*;import java.sql.Connection;import java.time.Instant;import java.time.temporal.ChronoUnit;import java.util.*;import java.util.stream.Collectors;import javax.sql.DataSource;
import okhttp3.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;
@Service
public class CreativeService {
 private final DataSource ds; private final ObjectMapper om=new ObjectMapper(); private final OkHttpClient client;
 @Value("${openai.api-key:}") String apiKey; @Value("${openai.model:gpt-4o-mini}") String model;
 public CreativeService(DataSource ds,@Value("${openai.timeout-seconds:15}") int timeout){this.ds=ds;this.client=new OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(timeout)).build();}
 public Map<String,Object> generate(GenerateRequest req, UUID requestId){
  Map<String,Object> business=business(req.businessId()); if(business.isEmpty()) throw new IllegalArgumentException("businessId not found");
  List<String> trends=req.trendsOverride()!=null&&!req.trendsOverride().isEmpty()?req.trendsOverride():trends((String)business.get("industry"));
  List<Map<String,Object>> winners=winners(req.businessId());
  String strategySummary=req.strategyRequestId()!=null?strategy(req.strategyRequestId()):"none";
  Map<String,Object> out;
  try{out=openai(req,requestId,business,trends,winners,strategySummary);}catch(Exception e){out=fallback(req,requestId,trends);}
  storeCreative(req.businessId(),req.platform(),req.format(), ((List<Map<String,Object>>)out.get("creativeConcepts")).get(0).get("performanceAngle").toString(), ((List<Map<String,Object>>)out.get("creativeConcepts")).get(0).get("hook").toString());
  return out;
 }
 private Map<String,Object> openai(GenerateRequest req,UUID requestId,Map<String,Object> business,List<String> trends,List<Map<String,Object>> winners,String strategy) throws Exception {
  String prompt="Business="+business+" trends="+trends+" winners="+winners+" strategy="+strategy+" Use Hook->Value->CTA and return strict JSON.";
  if(apiKey==null||apiKey.isBlank()) throw new IllegalStateException();
  String json=om.writeValueAsString(Map.of("model",model,"response_format",Map.of("type","json_object"),"messages",List.of(Map.of("role","system","content","Creative director. JSON only."),Map.of("role","user","content",prompt))));
  Request r=new Request.Builder().url("https://api.openai.com/v1/chat/completions").addHeader("Authorization","Bearer "+apiKey).post(RequestBody.create(json,MediaType.get("application/json"))).build();
  try(Response rs=client.newCall(r).execute()){
    if(!rs.isSuccessful()||rs.body()==null) throw new RuntimeException();
    Map<String,Object> root=om.readValue(rs.body().string(),new TypeReference<>(){});
    String content=(String)((Map<String,Object>)((Map<String,Object>)((List<Object>)root.get("choices")).get(0)).get("message")).get("content");
    return parse(content,requestId);
  }
 }
 public Map<String,Object> parse(String content, UUID requestId) throws Exception {
  Map<String,Object> obj=om.readValue(content,new TypeReference<>(){});
  obj.put("requestId",requestId.toString()); obj.putIfAbsent("creativeVersion","v1");
  return obj;
 }
 private Map<String,Object> fallback(GenerateRequest req,UUID requestId,List<String> trends){
  return Map.of("requestId",requestId.toString(),"creativeVersion","v1","creativeConcepts",List.of(Map.of("conceptName","Trend-led UGC","trendUsed",trends.isEmpty()?"evergreen":trends.get(0),"hook","Stop scrolling: this solves X","visualDirection","UGC closeup","primaryText","Value proposition + proof","headline","Make the switch today","cta","Shop Now","aiImagePrompt","Product hero with lifestyle context","aiVideoPrompt","15s UGC demo","performanceAngle","social proof")),"notes",List.of("Fallback creative blueprint used"));
 }
 private Map<String,Object> business(UUID id){try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT business_name,industry FROM business_profile WHERE id=?")){ps.setObject(1,id); ResultSet rs=ps.executeQuery(); if(rs.next()) return Map.of("businessName",rs.getString(1),"industry",rs.getString(2));}catch(Exception e){throw new RuntimeException(e);} return Map.of();}
 private List<String> trends(String industry){List<String> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT keyword FROM trends WHERE (industry=? OR industry IS NULL) AND captured_at>=? ORDER BY captured_at DESC LIMIT 10")){ps.setString(1,industry); ps.setTimestamp(2,Timestamp.from(Instant.now().minus(7,ChronoUnit.DAYS))); ResultSet rs=ps.executeQuery(); while(rs.next()) out.add(rs.getString(1));}catch(Exception e){throw new RuntimeException(e);} return out;}
 private List<Map<String,Object>> winners(UUID id){List<Map<String,Object>> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT hook,angle,performance_score FROM creatives WHERE business_id=? ORDER BY performance_score DESC NULLS LAST LIMIT 5")){ps.setObject(1,id); ResultSet rs=ps.executeQuery(); while(rs.next()) out.add(Map.of("hook",rs.getString(1),"angle",rs.getString(2),"score",rs.getObject(3)));}catch(Exception e){throw new RuntimeException(e);} return out;}
 private String strategy(UUID rid){ try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT response_json::text FROM strategy_history WHERE request_id=? ORDER BY created_at DESC LIMIT 1")){ps.setObject(1,rid); ResultSet rs=ps.executeQuery(); if(rs.next()) return rs.getString(1);}catch(Exception e){throw new RuntimeException(e);} return "none"; }
 private void storeCreative(UUID businessId,String platform,String format,String angle,String hook){ try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("INSERT INTO creatives(id,business_id,platform,format,angle,hook,performance_score,created_at) VALUES (?,?,?,?,?,?,NULL,?)")){ps.setObject(1,UUID.randomUUID());ps.setObject(2,businessId);ps.setString(3,platform);ps.setString(4,format);ps.setString(5,angle);ps.setString(6,hook);ps.setTimestamp(7,Timestamp.from(Instant.now())); ps.executeUpdate();}catch(Exception e){throw new RuntimeException(e);} }
}

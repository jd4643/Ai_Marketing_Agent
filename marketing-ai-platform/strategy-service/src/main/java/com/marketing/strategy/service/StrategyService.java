package com.marketing.strategy.service;
import com.fasterxml.jackson.core.type.TypeReference;import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.strategy.model.*;
import java.math.BigDecimal;import java.sql.*;import java.time.Instant;import java.time.temporal.ChronoUnit;import java.util.*;import javax.sql.DataSource;
import okhttp3.*;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;

@Service
public class StrategyService {
 private static final Logger log=LoggerFactory.getLogger(StrategyService.class);
 private final DataSource ds; private final ObjectMapper om=new ObjectMapper(); private final OkHttpClient client;
 @Value("${openai.api-key:}") String apiKey; @Value("${openai.model:gpt-4o-mini}") String model;
 public StrategyService(DataSource ds,@Value("${openai.timeout-seconds:15}") int timeout){this.ds=ds; this.client=new OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(timeout)).build();}
 public StrategyResponse generate(StrategyRequest req, UUID requestId){
  Map<String,Object> business=getBusiness(req.businessId());
  if(business.isEmpty()) throw new IllegalArgumentException("businessId not found");
  List<Map<String,Object>> metrics=metrics(req.businessId()); List<Map<String,Object>> winners=winners(req.businessId());
  String perfSummary = buildPerfSummary(metrics,winners);
  Map<String,BigDecimal> split=deterministicSplit(req.monthlyBudget(), metrics);
  String prompt="Business:"+business+"\nPerformance:"+perfSummary+"\nObjective:"+req.objective()+"\nBudget:"+req.monthlyBudget()+"\nReturn strict JSON schema.";
  Map<String,Object> llmResp;
  long start=System.currentTimeMillis();
  try { llmResp = callOpenAi(prompt); } catch(Exception ex){
    llmResp = fallback(requestId,split);
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
 private Map<String,Object> fallback(UUID requestId,Map<String,BigDecimal> split){
  return Map.of("requestId",requestId.toString(),"strategyVersion","v1","platformBudgetSplit",split,"campaignPlan",List.of(Map.of("platform","meta","dailyBudget",split.get("meta").divide(new BigDecimal("30"), java.math.RoundingMode.HALF_UP))),"funnelStrategy","Conservative optimization due to unavailable model response","expectedCPL","Unknown","expectedROAS","Unknown","reasoning","Fallback deterministic strategy","assumptions",List.of("No model output"));
 }
 private StrategyResponse toResponse(Map<String,Object> m){
  return new StrategyResponse(UUID.fromString((String)m.get("requestId")),"v1",om.convertValue(m.get("platformBudgetSplit"),new TypeReference<>(){}),om.convertValue(m.get("campaignPlan"),new TypeReference<>(){}),(String)m.get("funnelStrategy"),(String)m.get("expectedCPL"),(String)m.get("expectedROAS"),(String)m.get("reasoning"),om.convertValue(m.get("assumptions"),new TypeReference<>(){}));
 }
 private Map<String,Object> getBusiness(UUID id){ try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT business_name,industry,product,target_audience FROM business_profile WHERE id=?")){ps.setObject(1,id); ResultSet rs=ps.executeQuery(); if(rs.next()) return Map.of("businessName",rs.getString(1),"industry",rs.getString(2),"product",rs.getString(3),"targetAudience",rs.getString(4)); }catch(Exception e){throw new RuntimeException(e);} return Map.of(); }
 private List<Map<String,Object>> metrics(UUID id){List<Map<String,Object>> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT platform,COALESCE(SUM(spend),0),COALESCE(AVG(roas),0) FROM campaign_metrics WHERE business_id=? AND recorded_at>=? GROUP BY platform")){ps.setObject(1,id); ps.setTimestamp(2,Timestamp.from(Instant.now().minus(30,ChronoUnit.DAYS))); ResultSet rs=ps.executeQuery(); while(rs.next()) out.add(Map.of("platform",rs.getString(1),"spend",rs.getBigDecimal(2),"avgRoas",rs.getBigDecimal(3))); }catch(Exception e){throw new RuntimeException(e);} return out; }
 private List<Map<String,Object>> winners(UUID id){List<Map<String,Object>> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT platform,hook,angle,performance_score FROM creatives WHERE business_id=? ORDER BY performance_score DESC NULLS LAST LIMIT 3")){ps.setObject(1,id); ResultSet rs=ps.executeQuery(); while(rs.next()) out.add(Map.of("platform",rs.getString(1),"hook",rs.getString(2),"angle",rs.getString(3),"score",rs.getBigDecimal(4))); }catch(Exception e){throw new RuntimeException(e);} return out; }
 private void saveHistory(UUID requestId,StrategyRequest req,String prompt,Map<String,Object> response,String status,String code,String error,Long latency){
  try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("INSERT INTO strategy_history(id,request_id,business_id,objective,monthly_budget,trends_json,prompt_version,model_name,request_json,response_json,status,error_code,error_message,openai_latency_ms,created_at) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?)")){
    ps.setObject(1,UUID.randomUUID()); ps.setObject(2,requestId); ps.setObject(3,req.businessId()); ps.setString(4,req.objective()); ps.setBigDecimal(5,req.monthlyBudget()); ps.setString(6,om.writeValueAsString(req.trends()==null?List.of():req.trends())); ps.setString(7,"v1"); ps.setString(8,model); ps.setString(9,om.writeValueAsString(req)); ps.setString(10,om.writeValueAsString(response)); ps.setString(11,status); ps.setString(12,code); ps.setString(13,error); ps.setLong(14,latency); ps.setTimestamp(15,Timestamp.from(Instant.now())); ps.executeUpdate();
  }catch(Exception e){log.error("history save error",e);} }
 public List<HistorySummary> history(UUID businessId,int limit){List<HistorySummary> out=new ArrayList<>(); try(Connection c=ds.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT request_id,objective,monthly_budget,status,created_at FROM strategy_history WHERE business_id=? ORDER BY created_at DESC LIMIT ?")){ps.setObject(1,businessId); ps.setInt(2,limit); ResultSet rs=ps.executeQuery(); while(rs.next()) out.add(new HistorySummary((UUID)rs.getObject(1),rs.getString(2),rs.getBigDecimal(3),rs.getString(4),rs.getTimestamp(5).toInstant()));}catch(Exception e){throw new RuntimeException(e);} return out; }
}

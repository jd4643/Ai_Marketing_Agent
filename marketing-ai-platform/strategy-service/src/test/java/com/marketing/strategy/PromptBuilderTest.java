package com.marketing.strategy;
import com.marketing.strategy.service.StrategyService;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class PromptBuilderTest { @Test void includesInjectedSummary(){
  StrategyService s=new StrategyService(null,15);
  String out=s.buildPerfSummary(List.of(Map.of("platform","meta")),List.of(Map.of("hook","offer")));
  assertTrue(out.contains("meta")); assertTrue(out.contains("offer"));
}}

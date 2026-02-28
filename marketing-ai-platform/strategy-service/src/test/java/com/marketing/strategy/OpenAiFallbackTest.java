package com.marketing.strategy;
import com.marketing.strategy.service.StrategyService;import java.math.BigDecimal;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class OpenAiFallbackTest {
 @Test void fallbackFormat(){
  StrategyService s=new StrategyService(null,15);
  var m=s.buildPerfSummary(List.of(),List.of());
  assertNotNull(m);
 }
}

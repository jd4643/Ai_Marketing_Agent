package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marketing.strategy.service.intel.PatternMatcher;
import com.marketing.strategy.service.intel.StrategyRunIntelEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatternMatcherTest {
  @Test
  void reusesHistoricalTemplateWhenSimilarAndSuccessful() {
    PatternMatcher matcher = new PatternMatcher();
    StrategyRunIntelEntity e = new StrategyRunIntelEntity();
    e.setId(UUID.randomUUID());
    e.setRequestId(UUID.randomUUID());
    e.setBusinessId(UUID.randomUUID());
    e.setObjective("sales");
    e.setChosenTemplateKey("ONLINE_MID_TICKET_META_FUNNEL_RETARGET");
    e.setCreatedAt(Instant.now());

    var r = matcher.match("ONLINE_MID_TICKET_META_FUNNEL_RETARGET", "sales", "jewelry", "ONLINE", "MEDIUM", new BigDecimal("2.5"), 22, e, Map.of("roas", 2.8, "conversions", 30));
    assertTrue(r.isPresent());
  }
}

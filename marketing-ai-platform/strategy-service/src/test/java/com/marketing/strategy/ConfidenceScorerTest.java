package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marketing.strategy.service.intel.ConfidenceScorer;
import org.junit.jupiter.api.Test;

class ConfidenceScorerTest {
  @Test
  void scoreWithinBoundsAndBreakdownPresent() {
    var r = new ConfidenceScorer().score("ONLINE", "sales", 6, 12);
    assertTrue(r.confidenceScore() >= 0 && r.confidenceScore() <= 100);
    assertTrue(r.breakdown().containsKey("margin_strength"));
    assertTrue(r.breakdown().containsKey("trust_level"));
  }
}

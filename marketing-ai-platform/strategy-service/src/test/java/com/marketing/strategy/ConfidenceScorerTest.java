package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marketing.strategy.service.intel.ConfidenceScorer;
import org.junit.jupiter.api.Test;

class ConfidenceScorerTest {
  @Test
  void scoreWithinBoundsAndBreakdownPresent() {
    var r = new ConfidenceScorer().score("ONLINE", "sales", 6, 12, false);
    assertTrue(r.confidenceScore() >= 0 && r.confidenceScore() <= 100);
    assertTrue(r.breakdown().containsKey("margin_strength"));
    assertTrue(r.breakdown().containsKey("trust_level"));
  }

  @Test
  void coldStartCapsConfidenceAndAddsBreakdownFlag() {
    var normal = new ConfidenceScorer().score("ONLINE", "sales", 6, 12, false);
    var cold = new ConfidenceScorer().score("ONLINE", "sales", 6, 12, true);
    assertTrue(cold.confidenceScore() <= normal.confidenceScore(), "Cold start score should be <= normal score");
    assertTrue(cold.breakdown().containsKey("cold_start"));
  }
}

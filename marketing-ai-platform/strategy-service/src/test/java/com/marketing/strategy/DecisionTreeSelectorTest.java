package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marketing.strategy.service.intel.DecisionTreeSelector;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionTreeSelectorTest {
  private final DecisionTreeSelector selector = new DecisionTreeSelector();

  @Test
  void selectsOfflineHighTicketTemplate() {
    var res = selector.select(Map.of("websiteUrl", "", "priceRange", "premium"), "sales", new BigDecimal("2500"), false);
    assertEquals("OFFLINE_HIGH_TICKET_TRUST_GOOGLE_LOCAL", res.templateKey());
  }

  @Test
  void selectsOnlineLeadgenTemplate() {
    var res = selector.select(Map.of("websiteUrl", "https://acme.com", "priceRange", "mid"), "leads", new BigDecimal("1200"), false);
    assertEquals("ONLINE_HIGH_TICKET_LEADGEN_APPOINTMENT", res.templateKey());
  }

  @Test
  void coldStartSelectsSaferOfflineTemplate() {
    var res = selector.select(Map.of("websiteUrl", "", "priceRange", "premium"), "sales", new BigDecimal("2500"), true);
    assertEquals("OFFLINE_MID_TICKET_FESTIVAL_FOOTFALL", res.templateKey());
    assertTrue(res.decisionPath().stream().anyMatch(m -> "coldStart".equals(m.get("node"))));
  }

  @Test
  void coldStartSelectsSaferOnlineTemplate() {
    var res = selector.select(Map.of("websiteUrl", "https://acme.com", "priceRange", "mid"), "leads", new BigDecimal("1200"), true);
    assertEquals("ONLINE_MID_TICKET_META_FUNNEL_RETARGET", res.templateKey());
  }
}

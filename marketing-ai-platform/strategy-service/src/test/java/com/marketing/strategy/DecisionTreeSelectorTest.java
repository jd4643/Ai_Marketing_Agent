package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.marketing.strategy.service.intel.DecisionTreeSelector;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionTreeSelectorTest {
  private final DecisionTreeSelector selector = new DecisionTreeSelector();

  @Test
  void selectsOfflineHighTicketTemplate() {
    var res = selector.select(Map.of("websiteUrl", "", "priceRange", "premium"), "sales", new BigDecimal("2500"));
    assertEquals("OFFLINE_HIGH_TICKET_TRUST_GOOGLE_LOCAL", res.templateKey());
  }

  @Test
  void selectsOnlineLeadgenTemplate() {
    var res = selector.select(Map.of("websiteUrl", "https://acme.com", "priceRange", "mid"), "leads", new BigDecimal("1200"));
    assertEquals("ONLINE_HIGH_TICKET_LEADGEN_APPOINTMENT", res.templateKey());
  }
}

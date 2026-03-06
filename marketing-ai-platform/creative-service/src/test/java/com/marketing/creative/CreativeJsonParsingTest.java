
package com.marketing.creative;
import com.marketing.creative.service.CreativeService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreativeJsonParsingTest {
  @Test
  void parseJson() throws Exception {
    CreativeService s = new CreativeService(null, 15);
    var out = s.parse("{\"creativeConcepts\":[{\"hook\":\"h\",\"performanceAngle\":\"a\"}],\"notes\":[\"n\"]}", UUID.randomUUID());
    assertEquals("v1", out.get("creativeVersion"));
  }
}

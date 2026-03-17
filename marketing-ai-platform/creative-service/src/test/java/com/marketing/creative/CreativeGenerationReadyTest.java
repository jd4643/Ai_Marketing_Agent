package com.marketing.creative;

import com.marketing.creative.service.CreativeService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreativeGenerationReadyTest {

  @Test
  void parseJsonContainsGenerationReadyFields() throws Exception {
    CreativeService s = new CreativeService(null, 15);
    String json = """
        {
          "creativeConcepts": [
            {
              "conceptName": "UGC Lifestyle",
              "hook": "Stop scrolling",
              "headline": "Try it now",
              "cta": "Shop Now",
              "aiImagePrompt": "Product hero shot with lifestyle context, natural lighting",
              "aiVideoPrompt": "15s UGC demo showing product in use",
              "performanceAngle": "social proof",
              "trendUsed": "minimalist jewelry",
              "primaryText": "Value prop text",
              "visualDirection": "UGC closeup"
            }
          ],
          "notes": ["test"]
        }
        """;
    var out = s.parse(json, UUID.randomUUID());
    assertNotNull(out.get("requestId"));
    assertEquals("v1", out.get("creativeVersion"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> concepts = (List<Map<String, Object>>) out.get("creativeConcepts");
    assertNotNull(concepts);
    assertFalse(concepts.isEmpty());

    Map<String, Object> concept = concepts.get(0);
    assertEquals("UGC Lifestyle", concept.get("conceptName"));
    assertNotNull(concept.get("aiImagePrompt"));
    assertNotNull(concept.get("aiVideoPrompt"));
    assertNotNull(concept.get("hook"));
    assertNotNull(concept.get("headline"));
    assertNotNull(concept.get("cta"));
    assertNotNull(concept.get("performanceAngle"));
  }

  @Test
  void parsedConceptsRetainAllOriginalFields() throws Exception {
    CreativeService s = new CreativeService(null, 15);
    String json = """
        {
          "creativeConcepts": [
            {
              "conceptName": "Bold Value Stack",
              "platform": "meta",
              "format": "image",
              "hook": "Why settle for less",
              "emotionalAngle": "aspiration",
              "visualStyle": "minimal",
              "productFocus": "gold necklace",
              "sceneDescription": "Flat lay on marble",
              "compositionNotes": "Overhead, rule of thirds",
              "lightingNotes": "Soft studio",
              "brandTone": "premium",
              "primaryText": "Quality you can feel",
              "headline": "Premium jewelry delivered",
              "cta": "Learn More",
              "aiImagePrompt": "Minimalist flat lay product photography of gold necklace",
              "aiVideoPrompt": "20s cinematic product reveal",
              "performanceAngle": "value proposition",
              "trendUsed": "gold necklace",
              "rationale": "Value-forward messaging works well"
            }
          ],
          "notes": ["Full blueprint"]
        }
        """;
    UUID requestId = UUID.randomUUID();
    var out = s.parse(json, requestId);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> concepts = (List<Map<String, Object>>) out.get("creativeConcepts");
    Map<String, Object> c = concepts.get(0);

    assertEquals("Bold Value Stack", c.get("conceptName"));
    assertEquals("meta", c.get("platform"));
    assertEquals("image", c.get("format"));
    assertEquals("aspiration", c.get("emotionalAngle"));
    assertEquals("minimal", c.get("visualStyle"));
    assertEquals("gold necklace", c.get("productFocus"));
    assertNotNull(c.get("sceneDescription"));
    assertNotNull(c.get("compositionNotes"));
    assertNotNull(c.get("lightingNotes"));
    assertEquals("premium", c.get("brandTone"));
    assertNotNull(c.get("rationale"));
    assertEquals(requestId.toString(), out.get("requestId"));
  }
}

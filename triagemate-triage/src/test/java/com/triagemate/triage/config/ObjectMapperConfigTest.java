package com.triagemate.triage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectMapperConfigTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapperConfig().objectMapper();
    }

    @Test
    void serializesMapKeysInAlphabeticalOrder() throws Exception {
        Map<String, Object> unsorted = new LinkedHashMap<>();
        unsorted.put("zebra", "last");
        unsorted.put("alpha", "first");
        unsorted.put("middle", "between");

        String json = objectMapper.writeValueAsString(unsorted);

        assertThat(json).isEqualTo("{\"alpha\":\"first\",\"middle\":\"between\",\"zebra\":\"last\"}");
    }

    @Test
    void deterministicSerialization_sameInputProducesSameOutput() throws Exception {
        Map<String, Object> map1 = new LinkedHashMap<>();
        map1.put("b", 2);
        map1.put("a", 1);

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("a", 1);
        map2.put("b", 2);

        String json1 = objectMapper.writeValueAsString(map1);
        String json2 = objectMapper.writeValueAsString(map2);

        assertThat(json1).isEqualTo(json2);
    }

    @Test
    void serializesInstantAsIso8601() throws Exception {
        Instant instant = Instant.parse("2026-03-17T12:00:00Z");

        Map<String, Object> map = Map.of("timestamp", instant);
        String json = objectMapper.writeValueAsString(map);

        assertThat(json).contains("2026-03-17T12:00:00Z");
        assertThat(json).doesNotContain("1742212800");
    }

    @Test
    void serializesRecordPropertiesAlphabetically() throws Exception {
        record Sample(String zebra, int alpha, String middle) {}

        Sample sample = new Sample("last", 1, "between");
        String json = objectMapper.writeValueAsString(sample);

        int alphaPos = json.indexOf("\"alpha\"");
        int middlePos = json.indexOf("\"middle\"");
        int zebraPos = json.indexOf("\"zebra\"");

        assertThat(alphaPos).isLessThan(middlePos);
        assertThat(middlePos).isLessThan(zebraPos);
    }
}

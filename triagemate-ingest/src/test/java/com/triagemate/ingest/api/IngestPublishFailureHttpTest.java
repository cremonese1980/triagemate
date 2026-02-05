package com.triagemate.ingest.api;

import com.triagemate.ingest.app.IngestEventPublisher;
import com.triagemate.ingest.app.KafkaPublishFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({
        ApiExceptionHandler.class,
        IngestPublishFailureHttpTest.TestBeans.class
})
@WebMvcTest(controllers = TriagemateIngestController.class) // <-- CHANGE if your POST controller class is different
class IngestPublishFailureHttpTest {

    private final MockMvc mvc;

    @Autowired
    IngestPublishFailureHttpTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void when_kafka_publish_fails_then_returns_503() throws Exception {

        mvc.perform(post("/api/ingest/messages")
                        .header("X-Request-Id", "req-test-fail-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channel": "email",
                                  "content": "hello kafka"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        IngestEventPublisher ingestEventPublisher() {
            return (requestId, payload) -> {
                throw new KafkaPublishFailedException("boom", new RuntimeException("kafka down"));
            };
        }
    }
}

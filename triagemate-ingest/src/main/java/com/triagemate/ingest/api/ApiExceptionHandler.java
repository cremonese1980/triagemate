package com.triagemate.ingest.api;

//import com.gabriele.triagemate.common.RequestId; // only if you already have it; otherwise ignore
import com.triagemate.ingest.app.KafkaPublishFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(KafkaPublishFailedException.class)
    public ResponseEntity<Map<String, Object>> handleKafkaPublishFailed(KafkaPublishFailedException ex) {
        var body = Map.<String, Object>of(
                "timestamp", Instant.now().toString(),
                "status", 503,
                "error", "SERVICE_UNAVAILABLE",
                "message", "Temporary failure while publishing to Kafka"
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}

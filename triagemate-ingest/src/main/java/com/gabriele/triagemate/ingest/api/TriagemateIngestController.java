package com.gabriele.triagemate.ingest.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TriagemateIngestController {

    @GetMapping("/api/hello")
    public TriagemateIngestResponse hello() {
        return new TriagemateIngestResponse("hello", "Hello from your new Spring Boot 3.4.0 service!!!");
    }

    public record TriagemateIngestResponse(String status, String message) {}
}

package com.gabriele.triagemate.ingest.config;

import com.gabriele.triagemate.ingest.observability.RequestIdForwardingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class TriagemateIngestConfig {

    @Bean
    RestClient infoRestClient(
            @Value("${triagemate-triage.base-url}") String baseUrl,
            @Value("${triagemate-triage.timeout-ms}") int timeoutMs
    ) {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(timeoutMs));
        rf.setReadTimeout(Duration.ofMillis(timeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(rf)
                .requestInterceptor(new RequestIdForwardingInterceptor())
                .build();
    }
}

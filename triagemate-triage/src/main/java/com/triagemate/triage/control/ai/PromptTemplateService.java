package com.triagemate.triage.control.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Component
public class PromptTemplateService {

    private static final String TEMPLATE_PATH = "prompts/advisory/v1.0.0-decision-advisor.txt";

    private final String template;
    private final String promptHash;
    private final String promptVersion = "1.0.1";

    public PromptTemplateService() {
        this.template = loadResource(TEMPLATE_PATH);
        this.promptHash = sha256(this.template);
    }

    public String render(Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getPromptHash() {
        return promptHash;
    }

    private static String loadResource(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt template: " + path, e);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

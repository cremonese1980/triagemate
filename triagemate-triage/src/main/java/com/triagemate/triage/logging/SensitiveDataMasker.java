package com.triagemate.triage.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Masks sensitive fields for safe logging.
 *
 * <p>Uses SHA-256 truncated to 8 hex chars for deterministic,
 * non-reversible masking that preserves correlation capability.
 *
 * <p>Current domain: triage of textual inputs (emails, tickets, messages).
 * When a mailbox source is connected, fields like sender address,
 * subject and body text must never appear in plain in logs.
 */
public final class SensitiveDataMasker {

    private static final int DEFAULT_HASH_LENGTH = 8;

    private SensitiveDataMasker() {}

    // ── Generic ──────────────────────────────────────────────

    /**
     * Deterministic mask for any identifier.
     * <p>Example: {@code "abc-12345" → "a3f2e1b4"}
     *
     * @param value raw value
     * @return 8-char hex hash, or {@code "***"} if null/empty
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "***";
        }
        return sha256Truncated(value, DEFAULT_HASH_LENGTH);
    }

    // ── Email ────────────────────────────────────────────────

    /**
     * Masks an email address preserving the domain for operational context.
     * <p>Example: {@code "mario.rossi@azienda.it" → "a3f2e1b4@azienda.it"}
     * <p>The local part is replaced with a deterministic hash so the same
     * sender always produces the same masked value (correlation works),
     * but the original address cannot be recovered.
     *
     * @param email raw email address
     * @return masked email, or {@code "***"} if null/empty
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "***";
        }

        int atIndex = email.indexOf('@');
        if (atIndex < 0) {
            // Not a valid email — hash the whole thing
            return sha256Truncated(email, DEFAULT_HASH_LENGTH);
        }

        String domain = email.substring(atIndex); // includes '@'
        return sha256Truncated(email, DEFAULT_HASH_LENGTH) + domain;
    }

    // ── Free text (subject, body snippets) ───────────────────

    /**
     * Truncates free text to a safe preview length and appends "[...]".
     * <p>Useful for logging subject lines or body excerpts without
     * leaking the full content.
     * <p>Example: {@code maskFreeText("Urgent: password reset request", 10) → "Urgent: pa[...]"}
     *
     * @param text         raw text
     * @param visibleChars number of leading characters to keep visible
     * @return truncated text, or {@code "***"} if null/empty
     */
    public static String maskFreeText(String text, int visibleChars) {
        if (text == null || text.isEmpty()) {
            return "***";
        }
        if (visibleChars <= 0) {
            return "[...]";
        }
        if (text.length() <= visibleChars) {
            return text;
        }
        return text.substring(0, visibleChars) + "[...]";
    }

    // ── Tokens / secrets ─────────────────────────────────────

    /**
     * Redacts authentication tokens completely.
     */
    public static String redactToken(String token) {
        return "[REDACTED]";
    }

    // ── Internal ─────────────────────────────────────────────

    /**
     * Generates a truncated SHA-256 hash (hex encoded).
     *
     * @param input  input string
     * @param length number of hex characters to return
     * @return truncated hex hash
     */
    static String sha256Truncated(String input, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(hash.length, (length + 1) / 2); i++) {
                String h = Integer.toHexString(0xff & hash[i]);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }

            return hex.substring(0, length);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

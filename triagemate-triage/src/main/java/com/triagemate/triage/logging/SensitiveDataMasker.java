package com.triagemate.triage.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Masks sensitive fields for safe logging.
 *
 * Uses SHA-256 truncated to 8 hex chars for deterministic,
 * non-reversible masking that preserves correlation capability.
 */
public final class SensitiveDataMasker {

    private SensitiveDataMasker() {}

    /**
     * Masks patient ID with deterministic hash.
     * Example: PAT-12345 -> PAT-a3f2e1b4
     */
    public static String maskPatientId(String patientId) {
        if (patientId == null || patientId.isEmpty()) {
            return "***";
        }

        String prefix = patientId.contains("-")
                ? patientId.substring(0, patientId.indexOf('-') + 1)
                : "PAT-";

        return prefix + sha256Truncated(patientId, 8);
    }

    /**
     * Masks device serial number with deterministic hash.
     * Example: DEV-SN-987654321 -> DEV-SN-7b3e9f2a
     */
    public static String maskDeviceSerial(String serialNo) {
        if (serialNo == null || serialNo.isEmpty()) {
            return "***";
        }

        String prefix = serialNo.contains("-")
                ? serialNo.substring(0, serialNo.lastIndexOf('-') + 1)
                : "DEV-";

        return prefix + sha256Truncated(serialNo, 8);
    }

    /**
     * Redacts authentication tokens completely.
     */
    public static String redactToken(String token) {
        return "[REDACTED]";
    }

    private static String sha256Truncated(String input, int length) {
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

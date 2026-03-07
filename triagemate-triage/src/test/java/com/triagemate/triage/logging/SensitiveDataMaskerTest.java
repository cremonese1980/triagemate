package com.triagemate.triage.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    // ── mask() ───────────────────────────────────────────────

    @Test
    void mask_shouldProduceDeterministicHash() {
        String masked1 = SensitiveDataMasker.mask("some-value");
        String masked2 = SensitiveDataMasker.mask("some-value");

        assertThat(masked1).isEqualTo(masked2);
    }

    @Test
    void mask_shouldProduceEightHexChars() {
        String masked = SensitiveDataMasker.mask("some-value");

        assertThat(masked).hasSize(8);
        assertThat(masked).matches("[0-9a-f]{8}");
    }

    @Test
    void mask_differentInputsProduceDifferentHashes() {
        String masked1 = SensitiveDataMasker.mask("value-1");
        String masked2 = SensitiveDataMasker.mask("value-2");

        assertThat(masked1).isNotEqualTo(masked2);
    }

    @Test
    void mask_shouldReturnStarsForNull() {
        assertThat(SensitiveDataMasker.mask(null)).isEqualTo("***");
    }

    @Test
    void mask_shouldReturnStarsForEmpty() {
        assertThat(SensitiveDataMasker.mask("")).isEqualTo("***");
    }

    // ── maskEmail() ──────────────────────────────────────────

    @Test
    void maskEmail_shouldPreserveDomain() {
        String masked = SensitiveDataMasker.maskEmail("mario.rossi@azienda.it");

        assertThat(masked).endsWith("@azienda.it");
        assertThat(masked).doesNotContain("mario.rossi");
    }

    @Test
    void maskEmail_shouldBeDeterministic() {
        String masked1 = SensitiveDataMasker.maskEmail("user@example.com");
        String masked2 = SensitiveDataMasker.maskEmail("user@example.com");

        assertThat(masked1).isEqualTo(masked2);
    }

    @Test
    void maskEmail_differentAddressesProduceDifferentHashes() {
        String masked1 = SensitiveDataMasker.maskEmail("alice@example.com");
        String masked2 = SensitiveDataMasker.maskEmail("bob@example.com");

        assertThat(masked1).isNotEqualTo(masked2);
    }

    @Test
    void maskEmail_shouldReturnStarsForNull() {
        assertThat(SensitiveDataMasker.maskEmail(null)).isEqualTo("***");
    }

    @Test
    void maskEmail_shouldReturnStarsForEmpty() {
        assertThat(SensitiveDataMasker.maskEmail("")).isEqualTo("***");
    }

    @Test
    void maskEmail_shouldHashEntireValueWhenNoAtSign() {
        String masked = SensitiveDataMasker.maskEmail("not-an-email");

        assertThat(masked).hasSize(8);
        assertThat(masked).matches("[0-9a-f]{8}");
    }

    @Test
    void maskEmail_localPartShouldBeEightHexChars() {
        String masked = SensitiveDataMasker.maskEmail("user@example.com");
        String localPart = masked.substring(0, masked.indexOf('@'));

        assertThat(localPart).hasSize(8);
        assertThat(localPart).matches("[0-9a-f]{8}");
    }

    // ── maskFreeText() ───────────────────────────────────────

    @Test
    void maskFreeText_shouldTruncateAndAppendMarker() {
        String masked = SensitiveDataMasker.maskFreeText("Urgent: password reset request", 10);

        assertThat(masked).isEqualTo("Urgent: pa[...]");
    }

    @Test
    void maskFreeText_shouldReturnFullTextWhenShorterThanLimit() {
        String masked = SensitiveDataMasker.maskFreeText("Short", 20);

        assertThat(masked).isEqualTo("Short");
    }

    @Test
    void maskFreeText_shouldReturnFullTextWhenExactLength() {
        String masked = SensitiveDataMasker.maskFreeText("12345", 5);

        assertThat(masked).isEqualTo("12345");
    }

    @Test
    void maskFreeText_shouldReturnMarkerOnlyWhenZeroVisible() {
        String masked = SensitiveDataMasker.maskFreeText("secret stuff", 0);

        assertThat(masked).isEqualTo("[...]");
    }

    @Test
    void maskFreeText_shouldReturnStarsForNull() {
        assertThat(SensitiveDataMasker.maskFreeText(null, 10)).isEqualTo("***");
    }

    @Test
    void maskFreeText_shouldReturnStarsForEmpty() {
        assertThat(SensitiveDataMasker.maskFreeText("", 10)).isEqualTo("***");
    }

    // ── redactToken() ────────────────────────────────────────

    @Test
    void redactToken_shouldAlwaysReturnRedacted() {
        assertThat(SensitiveDataMasker.redactToken("Bearer abc123xyz"))
                .isEqualTo("[REDACTED]");
    }

    @Test
    void redactToken_shouldNotLeakOriginalValue() {
        String result = SensitiveDataMasker.redactToken("super-secret-token");

        assertThat(result).doesNotContain("super-secret-token");
        assertThat(result).isEqualTo("[REDACTED]");
    }
}

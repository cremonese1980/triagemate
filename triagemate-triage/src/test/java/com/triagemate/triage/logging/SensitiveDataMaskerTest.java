package com.triagemate.triage.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    void maskPatientId_shouldProduceDeterministicHash() {
        String masked1 = SensitiveDataMasker.maskPatientId("PAT-12345");
        String masked2 = SensitiveDataMasker.maskPatientId("PAT-12345");

        assertThat(masked1).isEqualTo(masked2);
    }

    @Test
    void maskPatientId_shouldPreservePrefix() {
        String masked = SensitiveDataMasker.maskPatientId("PAT-12345");

        assertThat(masked).startsWith("PAT-");
        assertThat(masked).doesNotContain("12345");
    }

    @Test
    void maskPatientId_shouldProduceEightHexCharsAfterPrefix() {
        String masked = SensitiveDataMasker.maskPatientId("PAT-12345");

        String hash = masked.substring("PAT-".length());
        assertThat(hash).hasSize(8);
        assertThat(hash).matches("[0-9a-f]{8}");
    }

    @Test
    void maskPatientId_shouldReturnStarsForNull() {
        assertThat(SensitiveDataMasker.maskPatientId(null)).isEqualTo("***");
    }

    @Test
    void maskPatientId_shouldReturnStarsForEmpty() {
        assertThat(SensitiveDataMasker.maskPatientId("")).isEqualTo("***");
    }

    @Test
    void maskPatientId_differentInputsProduceDifferentHashes() {
        String masked1 = SensitiveDataMasker.maskPatientId("PAT-12345");
        String masked2 = SensitiveDataMasker.maskPatientId("PAT-67890");

        assertThat(masked1).isNotEqualTo(masked2);
    }

    @Test
    void maskDeviceSerial_shouldPreservePrefix() {
        String masked = SensitiveDataMasker.maskDeviceSerial("DEV-SN-987654321");

        assertThat(masked).startsWith("DEV-SN-");
        assertThat(masked).doesNotContain("987654321");
    }

    @Test
    void maskDeviceSerial_shouldBeDeterministic() {
        String masked1 = SensitiveDataMasker.maskDeviceSerial("DEV-SN-987654321");
        String masked2 = SensitiveDataMasker.maskDeviceSerial("DEV-SN-987654321");

        assertThat(masked1).isEqualTo(masked2);
    }

    @Test
    void maskDeviceSerial_shouldReturnStarsForNull() {
        assertThat(SensitiveDataMasker.maskDeviceSerial(null)).isEqualTo("***");
    }

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

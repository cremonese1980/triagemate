package com.triagemate.triage.control.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantPolicyVersionProviderTest {

    @Test
    void returnsConfiguredVersion() {
        ConstantPolicyVersionProvider provider = new ConstantPolicyVersionProvider("2.3.1");
        assertThat(provider.currentVersion()).isEqualTo("2.3.1");
    }

    @Test
    void returnsDefaultVersion() {
        ConstantPolicyVersionProvider provider = new ConstantPolicyVersionProvider("1.0.0");
        assertThat(provider.currentVersion()).isEqualTo("1.0.0");
    }
}

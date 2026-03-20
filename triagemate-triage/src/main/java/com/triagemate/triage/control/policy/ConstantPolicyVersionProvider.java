package com.triagemate.triage.control.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConstantPolicyVersionProvider implements PolicyVersionProvider {

    private final String version;

    public ConstantPolicyVersionProvider(
            @Value("${triagemate.policy.version:1.0.0}") String version
    ) {
        this.version = version;
    }

    @Override
    public String currentVersion() {
        return version;
    }
}

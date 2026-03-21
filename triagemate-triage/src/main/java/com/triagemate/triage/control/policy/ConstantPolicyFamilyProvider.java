package com.triagemate.triage.control.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConstantPolicyFamilyProvider implements PolicyFamilyProvider {

    private final String family;

    public ConstantPolicyFamilyProvider(
            @Value("${triagemate.policy.family:basic-triage}") String family
    ) {
        this.family = family;
    }

    @Override
    public String currentFamily() {
        return family;
    }
}

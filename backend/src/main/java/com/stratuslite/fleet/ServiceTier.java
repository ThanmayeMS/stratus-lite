package com.stratuslite.fleet;

public enum ServiceTier {
    BASIC,
    STANDARD,
    PREMIUM;

    public boolean supports(ServiceTier requiredTier) {
        return ordinal() >= requiredTier.ordinal();
    }
}


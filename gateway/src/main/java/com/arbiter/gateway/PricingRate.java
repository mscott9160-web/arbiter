package com.arbiter.gateway;

import java.math.BigDecimal;
import java.util.Objects;

public record PricingRate(
        String model,
        BigDecimal inputPerMillionTokens,
        BigDecimal outputPerMillionTokens,
        BigDecimal cachedInputPerMillionTokens,
        String pricingVersion) {
    public PricingRate {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        requireNonNegative(inputPerMillionTokens, "input price");
        requireNonNegative(outputPerMillionTokens, "output price");
        if (cachedInputPerMillionTokens != null) {
            requireNonNegative(cachedInputPerMillionTokens, "cached input price");
        }
        if (pricingVersion == null || pricingVersion.isBlank()) {
            throw new IllegalArgumentException("pricing version is required");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}

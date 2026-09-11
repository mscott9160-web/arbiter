package com.arbiter.gateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class CostCalculator {
    private static final BigDecimal TOKENS_PER_MILLION = BigDecimal.valueOf(1_000_000L);
    private final Map<String, PricingRate> rates;

    public CostCalculator(Map<String, PricingRate> rates) {
        this.rates = Map.copyOf(rates);
    }

    public CostResult calculate(String model, TokenUsage usage) {
        var rate = rates.get(model);
        if (rate == null) {
            throw new IllegalArgumentException("no verified pricing exists for model: " + model);
        }
        var uncachedPromptTokens = usage.promptTokens() - usage.cachedPromptTokens();
        var inputCost = price(uncachedPromptTokens, rate.inputPerMillionTokens());
        var cachedInputCost = usage.cachedPromptTokens() == 0 || rate.cachedInputPerMillionTokens() == null
                ? BigDecimal.ZERO
                : price(usage.cachedPromptTokens(), rate.cachedInputPerMillionTokens());
        var outputCost = price(usage.completionTokens(), rate.outputPerMillionTokens());
        var total = inputCost.add(cachedInputCost).add(outputCost).setScale(12, RoundingMode.HALF_UP);
        return new CostResult(model, total, rate.pricingVersion(), inputCost, cachedInputCost, outputCost);
    }

    private BigDecimal price(int tokens, BigDecimal perMillionTokens) {
        return BigDecimal.valueOf(tokens)
                .multiply(perMillionTokens)
                .divide(TOKENS_PER_MILLION, 12, RoundingMode.HALF_UP);
    }

    public record CostResult(
            String model,
            BigDecimal totalUsd,
            String pricingVersion,
            BigDecimal inputUsd,
            BigDecimal cachedInputUsd,
            BigDecimal outputUsd) {
    }
}

package com.arbiter.gateway;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CostCalculatorTest {
    private final CostCalculator calculator = new CostCalculator(Map.of(
            "test-model", new PricingRate(
                    "test-model",
                    new BigDecimal("4.00"),
                    new BigDecimal("20.00"),
                    new BigDecimal("0.40"),
                    "test-pricing-2026-09-11")));

    @Test
    void calculatesInputOutputAndCachedInputSeparately() {
        var result = calculator.calculate("test-model", new TokenUsage(1_000_000, 100_000, 200_000));

        assertThat(result.inputUsd()).isEqualByComparingTo("3.20");
        assertThat(result.cachedInputUsd()).isEqualByComparingTo("0.08");
        assertThat(result.outputUsd()).isEqualByComparingTo("2.00");
        assertThat(result.totalUsd()).isEqualByComparingTo("5.28");
    }

    @Test
    void costIsMonotonicWhenCompletionTokensIncrease() {
        var lower = calculator.calculate("test-model", new TokenUsage(100, 10, 0));
        var higher = calculator.calculate("test-model", new TokenUsage(100, 11, 0));

        assertThat(higher.totalUsd()).isGreaterThan(lower.totalUsd());
    }

    @Test
    void unknownModelFailsInsteadOfReturningZero() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate("unverified-model", new TokenUsage(1, 1, 0)))
                .withMessageContaining("no verified pricing");
    }

    @Test
    void cachedTokensCannotExceedPromptTokens() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TokenUsage(10, 1, 11));
    }
}

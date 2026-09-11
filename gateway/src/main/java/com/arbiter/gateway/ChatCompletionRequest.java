package com.arbiter.gateway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        boolean stream,
        @JsonProperty("x_arbiter") ArbiterOptions arbiter) {
    public ChatCompletionRequest {
        if (arbiter == null) {
            arbiter = new ArbiterOptions(null, null, null, null);
        }
    }

    public record Message(String role, String content) {
    }

    public record ArbiterOptions(
            @JsonProperty("max_cost_usd") Double maxCostUsd,
            @JsonProperty("min_quality_tier") String minQualityTier,
            String cache,
            @JsonProperty("tenant_id") String tenantId) {
    }
}

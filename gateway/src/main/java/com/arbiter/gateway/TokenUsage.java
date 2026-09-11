package com.arbiter.gateway;

public record TokenUsage(int promptTokens, int completionTokens, int cachedPromptTokens) {
    public TokenUsage {
        if (promptTokens < 0 || completionTokens < 0 || cachedPromptTokens < 0) {
            throw new IllegalArgumentException("token counts must be non-negative");
        }
        if (cachedPromptTokens > promptTokens) {
            throw new IllegalArgumentException("cached prompt tokens cannot exceed prompt tokens");
        }
    }
}

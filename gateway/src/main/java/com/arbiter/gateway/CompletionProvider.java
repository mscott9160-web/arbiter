package com.arbiter.gateway;

public interface CompletionProvider {
    ProviderCompletion complete(ChatCompletionRequest request);

    record ProviderCompletion(String model, String content, int promptTokens, int completionTokens) {
    }
}

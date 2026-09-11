package com.arbiter.gateway;

import reactor.core.publisher.Flux;

public interface CompletionProvider {
    ProviderCompletion complete(ChatCompletionRequest request);

    Flux<String> stream(ChatCompletionRequest request);

    record ProviderCompletion(String model, String content, int promptTokens, int completionTokens) {
    }
}

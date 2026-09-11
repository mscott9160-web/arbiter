package com.arbiter.gateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!redis")
public final class InMemoryExactCache implements ExactCache {
    private final Map<String, ChatCompletionController.ChatCompletionResponse> entries = new ConcurrentHashMap<>();

    @Override
    public ChatCompletionController.ChatCompletionResponse get(String tenantId, ChatCompletionRequest request) {
        return entries.get(ExactCacheKey.key(tenantId, request));
    }

    @Override
    public void put(String tenantId, ChatCompletionRequest request,
            ChatCompletionController.ChatCompletionResponse response) {
        entries.put(ExactCacheKey.key(tenantId, request), response);
    }

    @Override
    public void invalidateTenant(String tenantId) {
        entries.keySet().removeIf(key -> key.startsWith("arbiter:exact:" + tenantId + ":"));
    }
}

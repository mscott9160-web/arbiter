package com.arbiter.gateway;

public interface ExactCache {
    ChatCompletionController.ChatCompletionResponse get(String tenantId, ChatCompletionRequest request);

    void put(String tenantId, ChatCompletionRequest request,
            ChatCompletionController.ChatCompletionResponse response);

    void invalidateTenant(String tenantId);
}

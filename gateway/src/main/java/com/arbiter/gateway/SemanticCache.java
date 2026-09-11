package com.arbiter.gateway;

public interface SemanticCache {
    ChatCompletionController.ChatCompletionResponse find(String tenantId, String taskClass, String prompt);

    void put(String tenantId, String taskClass, String prompt,
            ChatCompletionController.ChatCompletionResponse response);

    void invalidateTenant(String tenantId);
}

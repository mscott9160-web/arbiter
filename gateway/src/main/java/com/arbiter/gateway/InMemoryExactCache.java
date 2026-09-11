package com.arbiter.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public final class InMemoryExactCache implements ExactCache {
    private final Map<String, ChatCompletionController.ChatCompletionResponse> entries = new ConcurrentHashMap<>();

    @Override
    public ChatCompletionController.ChatCompletionResponse get(String tenantId, ChatCompletionRequest request) {
        return entries.get(key(tenantId, request));
    }

    @Override
    public void put(String tenantId, ChatCompletionRequest request,
            ChatCompletionController.ChatCompletionResponse response) {
        entries.put(key(tenantId, request), response);
    }

    @Override
    public void invalidateTenant(String tenantId) {
        entries.keySet().removeIf(key -> key.startsWith(tenantId + ":"));
    }

    private String key(String tenantId, ChatCompletionRequest request) {
        var canonical = tenantId + ":" + normalize(request.model()) + ":"
                + request.messages().stream()
                        .map(message -> normalize(message.role()) + "=" + normalize(message.content()))
                        .reduce((left, right) -> left + "|" + right)
                        .orElse("")
                + ":stream=" + request.stream()
                + ":quality=" + normalize(request.arbiter().minQualityTier())
                + ":max_cost=" + String.valueOf(request.arbiter().maxCostUsd());
        return tenantId + ":" + sha256(canonical);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}

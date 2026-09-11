package com.arbiter.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ExactCacheKey {
    private ExactCacheKey() {
    }

    public static String key(String tenantId, ChatCompletionRequest request) {
        var canonical = tenantId + ":" + normalize(request.model()) + ":"
                + request.messages().stream()
                        .map(message -> normalize(message.role()) + "=" + normalize(message.content()))
                        .reduce((left, right) -> left + "|" + right)
                        .orElse("")
                + ":stream=" + request.stream()
                + ":quality=" + normalize(request.arbiter().minQualityTier())
                + ":max_cost=" + String.valueOf(request.arbiter().maxCostUsd());
        return "arbiter:exact:" + tenantId + ":" + sha256(canonical);
    }

    public static String tenantIndexKey(String tenantId) {
        return "arbiter:exact:index:" + tenantId;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}

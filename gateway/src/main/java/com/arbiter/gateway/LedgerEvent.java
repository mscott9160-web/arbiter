package com.arbiter.gateway;

import java.util.Map;

public record LedgerEvent(String requestId, Map<String, Object> payload) {
    public LedgerEvent {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        payload = Map.copyOf(payload);
    }
}

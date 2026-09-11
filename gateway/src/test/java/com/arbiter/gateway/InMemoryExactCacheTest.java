package com.arbiter.gateway;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryExactCacheTest {
    private final InMemoryExactCache cache = new InMemoryExactCache();
    private final ChatCompletionRequest request = new ChatCompletionRequest(
            "auto",
            List.of(new ChatCompletionRequest.Message("user", " hello\r\n")),
            false,
            new ChatCompletionRequest.ArbiterOptions(null, "standard", "allow", "acme"), null, List.of());
    private final ChatCompletionController.ChatCompletionResponse response =
            new ChatCompletionController.ChatCompletionResponse("chat.completion", "id", "fake-small", List.of(), null);

    @Test
    void normalizesLineEndingsAndOuterWhitespace() {
        cache.put("acme", request, response);
        var equivalent = new ChatCompletionRequest(
                "auto", List.of(new ChatCompletionRequest.Message("user", "hello")), false, request.arbiter(),
                null, List.of());

        assertThat(cache.get("acme", equivalent)).isEqualTo(response);
    }

    @Test
    void namespacesEntriesByTenant() {
        cache.put("acme", request, response);

        assertThat(cache.get("other", request)).isNull();
    }

    @Test
    void invalidatesOnlyOneTenant() {
        cache.put("acme", request, response);
        cache.put("other", request, response);

        cache.invalidateTenant("acme");

        assertThat(cache.get("acme", request)).isNull();
        assertThat(cache.get("other", request)).isEqualTo(response);
    }
}

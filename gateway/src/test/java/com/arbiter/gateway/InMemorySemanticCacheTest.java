package com.arbiter.gateway;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySemanticCacheTest {
    @Test
    void returnsOnlyEntriesAboveThresholdAndWithinTaskClass() {
        var embeddings = Map.of(
                "source", new float[] {1, 0},
                "near", new float[] {0.999f, 0.01f},
                "far", new float[] {0, 1});
        var cache = new InMemorySemanticCache(embeddings::get, 0.95);
        var response = response("source");

        cache.put("acme", "general", "source", response);

        assertThat(cache.find("acme", "general", "near")).isEqualTo(response);
        assertThat(cache.find("acme", "general", "far")).isNull();
        assertThat(cache.find("acme", "math", "near")).isNull();
    }

    @Test
    void invalidatesTenantEntries() {
        var embeddings = Map.of("source", new float[] {1, 0});
        var cache = new InMemorySemanticCache(embeddings::get, 0.95);
        cache.put("acme", "general", "source", response("source"));

        cache.invalidateTenant("acme");

        assertThat(cache.find("acme", "general", "source")).isNull();
    }

    private ChatCompletionController.ChatCompletionResponse response(String id) {
        return new ChatCompletionController.ChatCompletionResponse("chat.completion", id, "fake-small", List.of(), null);
    }
}

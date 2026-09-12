package com.arbiter.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("semantic & !pgvector")
public final class InMemorySemanticCache implements SemanticCache {
    private final EmbeddingClient embeddingClient;
    private final double threshold;
    private final ConcurrentHashMap<String, List<Entry>> entries = new ConcurrentHashMap<>();

    public InMemorySemanticCache(
            EmbeddingClient embeddingClient,
            @Value("${arbiter.semantic-threshold:0.95}") double threshold) {
        if (threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("semantic threshold must be between 0 and 1");
        }
        this.embeddingClient = embeddingClient;
        this.threshold = threshold;
    }

    @Override
    public ChatCompletionController.ChatCompletionResponse find(String tenantId, String taskClass, String prompt) {
        var query = embeddingClient.embed(prompt);
        Entry best = null;
        var bestSimilarity = threshold;
        for (var entry : entries.getOrDefault(tenantId, List.of())) {
            if (!entry.taskClass().equals(taskClass)) {
                continue;
            }
            var similarity = cosine(query, entry.embedding());
            if (similarity >= bestSimilarity) {
                bestSimilarity = similarity;
                best = entry;
            }
        }
        return best == null ? null : best.response();
    }

    @Override
    public void put(String tenantId, String taskClass, String prompt,
            ChatCompletionController.ChatCompletionResponse response) {
        entries.computeIfAbsent(tenantId, ignored -> new ArrayList<>())
                .add(new Entry(taskClass, embeddingClient.embed(prompt), response));
    }

    @Override
    public void invalidateTenant(String tenantId) {
        entries.remove(tenantId);
    }

    private double cosine(float[] left, float[] right) {
        if (left.length != right.length || left.length == 0) {
            throw new IllegalArgumentException("embedding dimensions must match and be non-empty");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (var index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record Entry(String taskClass, float[] embedding,
            ChatCompletionController.ChatCompletionResponse response) {
    }
}

package com.arbiter.gateway;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Profile("semantic | pgvector")
public final class HttpEmbeddingClient implements EmbeddingClient {
    private final WebClient client;

    public HttpEmbeddingClient(
            WebClient.Builder builder,
            @Value("${arbiter.classifier-url:http://localhost:8001}") String classifierUrl) {
        this.client = builder.baseUrl(classifierUrl).build();
    }

    @Override
    public float[] embed(String text) {
        var response = CompletableFuture.supplyAsync(() -> client.post()
            .uri("/embed")
            .bodyValue(new EmbedRequest(text))
            .retrieve()
            .bodyToMono(EmbedResponse.class)
            .block(Duration.ofSeconds(2))).join();
        if (response == null || response.embedding() == null || response.embedding().length == 0) {
            throw new IllegalStateException("embedding service returned no vector");
        }
        return response.embedding();
    }

    private record EmbedRequest(String text) {
    }

    private record EmbedResponse(float[] embedding, String embeddingVersion) {
    }
}

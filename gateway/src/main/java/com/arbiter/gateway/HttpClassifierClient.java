package com.arbiter.gateway;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class HttpClassifierClient implements ClassifierClient {
    private final WebClient client;

    public HttpClassifierClient(
            WebClient.Builder builder,
            @Value("${arbiter.classifier-url:http://localhost:8001}") String classifierUrl) {
        this.client = builder.baseUrl(classifierUrl).build();
    }

    @Override
    public ClassificationResult classify(String prompt) {
        try {
            var response = client.post()
                    .uri("/classify")
                    .bodyValue(new ClassifyRequest(prompt))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            status -> status.createException().flatMap(error ->
                                reactor.core.publisher.Mono.error(new ResponseStatusException(
                                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                                            "classifier returned " + status.statusCode().value(), error))))
                    .bodyToMono(ClassificationResult.class)
                    .block(Duration.ofSeconds(2));
            if (response == null) {
                throw new IllegalStateException("classifier returned an empty response");
            }
            return response;
        } catch (ResponseStatusException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "classifier service unavailable", error);
        }
    }

    private record ClassifyRequest(String prompt) {
    }
}

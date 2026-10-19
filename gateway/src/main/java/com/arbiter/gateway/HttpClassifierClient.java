package com.arbiter.gateway;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
            var response = CompletableFuture.supplyAsync(() -> client.post()
                .uri("/classify")
                .bodyValue(new ClassifyRequest(prompt))
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                    status -> status.createException().flatMap(error ->
                        reactor.core.publisher.Mono.error(new ResponseStatusException(
                            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                            "classifier returned " + status.statusCode().value(), error))))
                .bodyToMono(ClassifierResponse.class)
                .block(Duration.ofSeconds(2))).join();
            if (response == null) {
                throw new IllegalStateException("classifier returned an empty response");
            }
                return new ClassificationResult(response.complexity(), response.taskClass(),
                    response.classifierVersion(), response.uncertaintyBand(), response.routeUp());
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClassifierResponse(
            double complexity,
            @JsonProperty("task_class") String taskClass,
            @JsonProperty("classifier_version") String classifierVersion,
            @JsonProperty("uncertainty_band") boolean uncertaintyBand,
            @JsonProperty("route_up") boolean routeUp,
            Map<String, Double> features) {
    }
}

package com.arbiter.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatCompletionControllerTest {
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private ClassifierClient classifierClient;

    @MockBean
    private SemanticCache semanticCache;

    @Autowired
    private ExactCache exactCache;

    @org.junit.jupiter.api.BeforeEach
    void configureClassifier() {
        exactCache.invalidateTenant("default");
        when(classifierClient.classify(anyString()))
                .thenReturn(new ClassificationResult(0.31, "general", "heuristic-test", false, false));
    }

    @Test
    void routesNonStreamingRequestThroughFakeProvider() {
        var request = new HttpEntity<>("""
                {"model":"auto","messages":[{"role":"user","content":"hello"}]}
                """, jsonHeaders());

        var response = restTemplate.exchange(
                "http://localhost:" + port + "/v1/chat/completions",
                HttpMethod.POST,
                request,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Fake provider response: hello");
        assertThat(response.getHeaders().getFirst("x-arbiter-model")).isEqualTo("fake-small");
        assertThat(response.getHeaders().getFirst("x-arbiter-cost-usd")).isEqualTo("unpriced");
        assertThat(response.getHeaders().getFirst("x-arbiter-complexity")).isEqualTo("0.31");
        assertThat(response.getHeaders().getFirst("x-arbiter-task-class")).isEqualTo("general");
        assertThat(response.getHeaders().getFirst("x-arbiter-semantic-cache")).isEqualTo("eligible");
    }

        @Test
        void servesAnExactRepeatFromCache() {
        var request = new HttpEntity<>("""
            {"model":"auto","messages":[{"role":"user","content":"cached hello"}]}
            """, jsonHeaders());

        var first = restTemplate.exchange("http://localhost:" + port + "/v1/chat/completions",
            HttpMethod.POST, request, String.class);
        var second = restTemplate.exchange("http://localhost:" + port + "/v1/chat/completions",
            HttpMethod.POST, request, String.class);

        assertThat(first.getHeaders().getFirst("x-arbiter-cache")).isEqualTo("miss");
        assertThat(second.getHeaders().getFirst("x-arbiter-cache")).isEqualTo("hit-exact");
        assertThat(second.getBody()).isEqualTo(first.getBody());
        }

    @Test
    void streamsTokensAndReportsTimingMetrics() {
        var request = new HttpEntity<>("""
                {"model":"auto","stream":true,"messages":[{"role":"user","content":"hello"}]}
                """, jsonHeaders());

        var response = restTemplate.exchange(
                "http://localhost:" + port + "/v1/chat/completions",
                HttpMethod.POST,
                request,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Type")).startsWith("text/event-stream");
        assertThat(response.getBody()).contains("chat.completion.chunk");
        assertThat(response.getBody()).contains("arbiter_metrics");
        assertThat(response.getBody()).contains("[DONE]");
    }

        @Test
        void servesEligibleRequestFromSemanticCacheAfterExactMiss() {
        var cached = new ChatCompletionController.ChatCompletionResponse(
            "chat.completion", "semantic-id", "fake-small", java.util.List.of(), null);
        when(semanticCache.find("default", "general", "semantic hello")).thenReturn(cached);
        var request = new HttpEntity<>("""
            {"model":"auto","messages":[{"role":"user","content":"semantic hello"}]}
            """, jsonHeaders());

        var response = restTemplate.exchange("http://localhost:" + port + "/v1/chat/completions",
            HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("x-arbiter-cache")).isEqualTo("hit-semantic");
        assertThat(response.getBody()).contains("semantic-id");
        verify(semanticCache).find("default", "general", "semantic hello");
        }

        @Test
        void returnsServiceUnavailableWhenClassifierCannotBeReached() {
        when(classifierClient.classify(anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "classifier unavailable"));
        var request = new HttpEntity<>("""
            {"model":"auto","messages":[{"role":"user","content":"hello"}]}
            """, jsonHeaders());

        var response = restTemplate.exchange(
            "http://localhost:" + port + "/v1/chat/completions",
            HttpMethod.POST,
            request,
            String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

    private HttpHeaders jsonHeaders() {
        var headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return headers;
    }
}

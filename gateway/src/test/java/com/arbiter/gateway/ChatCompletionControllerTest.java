package com.arbiter.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatCompletionControllerTest {
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

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
    }

    @Test
    void rejectsStreamingUntilSseMeteringIsImplemented() {
        var request = new HttpEntity<>("""
                {"model":"auto","stream":true,"messages":[{"role":"user","content":"hello"}]}
                """, jsonHeaders());

        var response = restTemplate.exchange(
                "http://localhost:" + port + "/v1/chat/completions",
                HttpMethod.POST,
                request,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private HttpHeaders jsonHeaders() {
        var headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return headers;
    }
}

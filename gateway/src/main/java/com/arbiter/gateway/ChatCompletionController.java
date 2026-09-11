package com.arbiter.gateway;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;

@RestController
public class ChatCompletionController {
    private final CompletionProvider provider;

    public ChatCompletionController(CompletionProvider provider) {
        this.provider = provider;
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<?> create(
            @RequestBody ChatCompletionRequest request,
            @RequestHeader(value = "x-arbiter-request-id", required = false) String suppliedRequestId) {
        validate(request);
        var requestId = suppliedRequestId == null || suppliedRequestId.isBlank()
                ? UUID.randomUUID().toString()
                : suppliedRequestId;
        if (request.stream()) {
            return streamResponse(request, requestId);
        }

        var completion = provider.complete(request);
        var response = new ChatCompletionResponse(
                "chat.completion",
                requestId,
                completion.model(),
                List.of(new Choice(0, new Message("assistant", completion.content()), "stop")),
                new Usage(completion.promptTokens(), completion.completionTokens(),
                        completion.promptTokens() + completion.completionTokens()));
        return new ResponseEntity<>(response, arbiterHeaders(requestId, completion.model()), HttpStatus.OK);
    }

    private ResponseEntity<Flux<String>> streamResponse(ChatCompletionRequest request, String requestId) {
        var startedAt = System.nanoTime();
        var firstTokenAt = new long[] {0L};
        var model = "fake-small";
        var chunks = provider.stream(request)
                .index()
                .map(indexed -> {
                    if (firstTokenAt[0] == 0L) {
                        firstTokenAt[0] = System.nanoTime();
                    }
                    var role = indexed.getT1() == 0 ? "\"role\":\"assistant\"," : "";
                    var content = escapeJson(indexed.getT2());
                    return "data: {\"id\":\"" + requestId
                            + "\",\"object\":\"chat.completion.chunk\",\"model\":\"" + model
                            + "\",\"choices\":[{\"index\":0,\"delta\":{" + role
                            + "\"content\":\"" + content + "\"},\"finish_reason\":null}]}\n\n";
                })
                .concatWith(Flux.defer(() -> {
                    var endedAt = System.nanoTime();
                    var ttft = firstTokenAt[0] == 0L ? 0L : (firstTokenAt[0] - startedAt) / 1_000_000;
                    var total = (endedAt - startedAt) / 1_000_000;
                    return Flux.just(
                            "data: {\"arbiter_metrics\":{\"ttft_ms\":" + ttft
                                    + ",\"total_ms\":" + total + "}}\n\n",
                            "data: [DONE]\n\n");
                }));

        var headers = arbiterHeaders(requestId, model);
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);
        headers.set("x-arbiter-stream-metrics", "final-event");
        return new ResponseEntity<>(chunks, headers, HttpStatus.OK);
    }

    private HttpHeaders arbiterHeaders(String requestId, String model) {
        var headers = new HttpHeaders();
        headers.set("x-arbiter-request-id", requestId);
        headers.set("x-arbiter-model", model);
        headers.set("x-arbiter-tier", "small");
        headers.set("x-arbiter-cache", "miss");
        headers.set("x-arbiter-complexity", "unclassified");
        headers.set("x-arbiter-escalated", "false");
        headers.set("x-arbiter-cost-usd", "unpriced");
        headers.set("x-arbiter-baseline-cost-usd", "unpriced");
        return headers;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void validate(ChatCompletionRequest request) {
        if (request.model() == null || request.model().isBlank()
                || request.messages() == null || request.messages().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "model and messages are required");
        }
        for (var message : request.messages()) {
            if (message == null || message.role() == null || message.content() == null
                    || !List.of("system", "user", "assistant", "tool").contains(message.role())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid message");
            }
        }
    }

    public record ChatCompletionResponse(
            String object,
            String id,
            String model,
            List<Choice> choices,
            Usage usage) {
    }

    public record Choice(int index, Message message, String finish_reason) {
    }

    public record Message(String role, String content) {
    }

    public record Usage(
            int prompt_tokens,
            int completion_tokens,
            int total_tokens) {
    }
}

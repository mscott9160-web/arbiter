package com.arbiter.gateway;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ChatCompletionController {
    private final CompletionProvider provider;

    public ChatCompletionController(CompletionProvider provider) {
        this.provider = provider;
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<ChatCompletionResponse> create(
            @RequestBody ChatCompletionRequest request,
            @RequestHeader(value = "x-arbiter-request-id", required = false) String suppliedRequestId) {
        validate(request);
        var requestId = suppliedRequestId == null || suppliedRequestId.isBlank()
                ? UUID.randomUUID().toString()
                : suppliedRequestId;
        var completion = provider.complete(request);
        var response = new ChatCompletionResponse(
                "chat.completion",
                requestId,
                completion.model(),
                List.of(new Choice(0, new Message("assistant", completion.content()), "stop")),
                new Usage(completion.promptTokens(), completion.completionTokens(),
                        completion.promptTokens() + completion.completionTokens()));

        var headers = new HttpHeaders();
        headers.set("x-arbiter-request-id", requestId);
        headers.set("x-arbiter-model", completion.model());
        headers.set("x-arbiter-tier", "small");
        headers.set("x-arbiter-cache", "miss");
        headers.set("x-arbiter-complexity", "unclassified");
        headers.set("x-arbiter-escalated", "false");
        headers.set("x-arbiter-cost-usd", "unpriced");
        headers.set("x-arbiter-baseline-cost-usd", "unpriced");
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    private void validate(ChatCompletionRequest request) {
        if (request.model() == null || request.model().isBlank()
                || request.messages() == null || request.messages().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "model and messages are required");
        }
        if (request.stream()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "streaming is not implemented in the fake provider slice");
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

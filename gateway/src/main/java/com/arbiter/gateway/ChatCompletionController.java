package com.arbiter.gateway;

import java.util.List;
import java.util.Optional;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;

@RestController
public class ChatCompletionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatCompletionController.class);
    private final CompletionProvider provider;
    private final ClassifierClient classifierClient;
    private final ExactCache exactCache;
    private final SemanticCachePolicy semanticCachePolicy;
    private final Optional<SemanticCache> semanticCache;

    public ChatCompletionController(
            CompletionProvider provider, ClassifierClient classifierClient, ExactCache exactCache,
            SemanticCachePolicy semanticCachePolicy, Optional<SemanticCache> semanticCache) {
        this.provider = provider;
        this.classifierClient = classifierClient;
        this.exactCache = exactCache;
        this.semanticCachePolicy = semanticCachePolicy;
        this.semanticCache = semanticCache;
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<?> create(
            @RequestBody ChatCompletionRequest request,
            @RequestHeader(value = "x-arbiter-request-id", required = false) String suppliedRequestId) {
        validate(request);
        var requestId = suppliedRequestId == null || suppliedRequestId.isBlank()
                ? UUID.randomUUID().toString()
                : suppliedRequestId;
        var classification = classifierClient.classify(lastUserPrompt(request));
        var semanticDecision = semanticCachePolicy.evaluate(request, classification);
        var tenantId = tenantId(request);
        var cacheMode = cacheMode(request);
        var cacheable = !request.stream() && !"bypass".equals(cacheMode);
        if (cacheable && !"refresh".equals(cacheMode)) {
            var cached = exactCache.get(tenantId, request);
            if (cached != null) {
                return new ResponseEntity<>(cached,
                    arbiterHeaders(requestId, cached.model(), classification, "hit-exact", semanticDecision), HttpStatus.OK);
            }
                if (semanticDecision.eligible()) {
                    var semanticCached = semanticFind(tenantId, classification.taskClass(), lastUserPrompt(request));
                if (semanticCached != null) {
                    return new ResponseEntity<>(semanticCached,
                        arbiterHeaders(requestId, semanticCached.model(), classification,
                            "hit-semantic", semanticDecision), HttpStatus.OK);
                }
                }
        }
        if (request.stream()) {
            return streamResponse(request, requestId, classification, semanticDecision);
        }

        var completion = provider.complete(request);
        var response = new ChatCompletionResponse(
                "chat.completion",
                requestId,
                completion.model(),
                List.of(new Choice(0, new Message("assistant", completion.content()), "stop")),
                new Usage(completion.promptTokens(), completion.completionTokens(),
                        completion.promptTokens() + completion.completionTokens()));
        if (cacheable) {
            exactCache.put(tenantId, request, response);
            if (semanticDecision.eligible()) {
                semanticPut(tenantId, classification.taskClass(), lastUserPrompt(request), response);
            }
        }
        var cacheResult = cacheable ? "miss" : "bypass";
        return new ResponseEntity<>(response,
            arbiterHeaders(requestId, completion.model(), classification, cacheResult, semanticDecision), HttpStatus.OK);
    }

    private ChatCompletionResponse semanticFind(String tenantId, String taskClass, String prompt) {
        try {
            return semanticCache.map(cache -> cache.find(tenantId, taskClass, prompt)).orElse(null);
        } catch (RuntimeException error) {
            LOGGER.warn("semantic cache lookup unavailable; continuing without semantic hit", error);
            return null;
        }
    }

    private void semanticPut(String tenantId, String taskClass, String prompt, ChatCompletionResponse response) {
        try {
            semanticCache.ifPresent(cache -> cache.put(tenantId, taskClass, prompt, response));
        } catch (RuntimeException error) {
            LOGGER.warn("semantic cache write unavailable; response remains valid", error);
        }
    }

    private ResponseEntity<Flux<String>> streamResponse(
            ChatCompletionRequest request, String requestId, ClassificationResult classification,
            SemanticCachePolicy.Decision semanticDecision) {
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

        var headers = arbiterHeaders(requestId, model, classification, "bypass", semanticDecision);
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);
        headers.set("x-arbiter-stream-metrics", "final-event");
        return new ResponseEntity<>(chunks, headers, HttpStatus.OK);
    }

        private HttpHeaders arbiterHeaders(
                String requestId, String model, ClassificationResult classification, String cacheResult,
                SemanticCachePolicy.Decision semanticDecision) {
        var headers = new HttpHeaders();
        headers.set("x-arbiter-request-id", requestId);
        headers.set("x-arbiter-model", model);
        headers.set("x-arbiter-tier", "small");
        headers.set("x-arbiter-cache", cacheResult);
        headers.set("x-arbiter-semantic-cache",
            semanticDecision.eligible() ? "eligible" : "denied:" + semanticDecision.reason());
        headers.set("x-arbiter-complexity", Double.toString(classification.complexity()));
        headers.set("x-arbiter-task-class", classification.taskClass());
        headers.set("x-arbiter-classifier-version", classification.classifierVersion());
        headers.set("x-arbiter-escalated", Boolean.toString(classification.routeUp()));
        headers.set("x-arbiter-cost-usd", "unpriced");
        headers.set("x-arbiter-baseline-cost-usd", "unpriced");
        return headers;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String lastUserPrompt(ChatCompletionRequest request) {
        return request.messages().stream()
                .filter(message -> "user".equals(message.role()))
                .reduce((first, second) -> second)
                .map(ChatCompletionRequest.Message::content)
                .orElse(request.messages().get(request.messages().size() - 1).content());
    }

    private String tenantId(ChatCompletionRequest request) {
        var tenantId = request.arbiter().tenantId();
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }

    private String cacheMode(ChatCompletionRequest request) {
        var cacheMode = request.arbiter().cache();
        return cacheMode == null || cacheMode.isBlank() ? "allow" : cacheMode;
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
        var cacheMode = request.arbiter().cache();
        if (cacheMode != null && !List.of("allow", "bypass", "refresh").contains(cacheMode)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid cache mode");
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

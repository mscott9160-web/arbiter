package com.arbiter.gateway;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticCachePolicyTest {
    private final SemanticCachePolicy policy = new SemanticCachePolicy();
    private final ClassificationResult general = new ClassificationResult(0.2, "general", "test", false, false);

    @Test
    void allowsStableGeneralPrompt() {
        assertThat(policy.evaluate(request("Explain HTTP caching."), general))
                .isEqualTo(new SemanticCachePolicy.Decision(true, "eligible"));
    }

    @Test
    void deniesTemperatureAbovePointThree() {
        assertThat(policy.evaluate(request("Explain HTTP caching.", 0.31), general).reason())
                .isEqualTo("temperature-above-threshold");
    }

    @Test
    void deniesTimeSensitivePrompt() {
        assertThat(policy.evaluate(request("What is the current date?"), general).reason())
                .isEqualTo("time-sensitive-prompt");
    }

    @Test
    void deniesToolCalls() {
        var request = new ChatCompletionRequest("auto", List.of(message("Lookup this.")), false,
                options(), null, List.of(new ChatCompletionRequest.ToolCall("call-1")));
        assertThat(policy.evaluate(request, general).reason()).isEqualTo("tool-calls-present");
    }

    @Test
    void deniesCodeExecution() {
        assertThat(policy.evaluate(request("Run this Python code."),
                new ClassificationResult(0.8, "code_execution", "test", false, true)).reason())
                .isEqualTo("task-class-code-execution");
    }

    @Test
    void deniesMath() {
        assertThat(policy.evaluate(request("Solve this equation."),
                new ClassificationResult(0.8, "math", "test", false, true)).reason())
                .isEqualTo("task-class-math");
    }

    private ChatCompletionRequest request(String content) {
        return request(content, null);
    }

    private ChatCompletionRequest request(String content, Double temperature) {
        return new ChatCompletionRequest("auto", List.of(message(content)), false, options(), temperature, List.of());
    }

    private ChatCompletionRequest.Message message(String content) {
        return new ChatCompletionRequest.Message("user", content);
    }

    private ChatCompletionRequest.ArbiterOptions options() {
        return new ChatCompletionRequest.ArbiterOptions(null, "standard", "allow", "acme");
    }
}

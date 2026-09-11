package com.arbiter.gateway;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;

@Component
public final class SemanticCachePolicy {
    private static final Pattern TIME_SENSITIVE = Pattern.compile(
            "\\b(today|tomorrow|yesterday|current|currently|latest|now|date|time)\\b",
            Pattern.CASE_INSENSITIVE);

    public Decision evaluate(ChatCompletionRequest request, ClassificationResult classification) {
        if (request.temperature() != null && request.temperature() > 0.3) {
            return new Decision(false, "temperature-above-threshold");
        }
        if (request.toolCalls() != null && !request.toolCalls().isEmpty()) {
            return new Decision(false, "tool-calls-present");
        }
        if ("code_execution".equals(classification.taskClass())) {
            return new Decision(false, "task-class-code-execution");
        }
        if ("math".equals(classification.taskClass())) {
            return new Decision(false, "task-class-math");
        }
        var prompt = request.messages().stream()
                .map(ChatCompletionRequest.Message::content)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        if (TIME_SENSITIVE.matcher(prompt).find()) {
            return new Decision(false, "time-sensitive-prompt");
        }
        return new Decision(true, "eligible");
    }

    public record Decision(boolean eligible, String reason) {
    }
}

package com.arbiter.gateway;

import org.springframework.stereotype.Component;

@Component
public class FakeCompletionProvider implements CompletionProvider {
    @Override
    public ProviderCompletion complete(ChatCompletionRequest request) {
        var lastMessage = request.messages().get(request.messages().size() - 1);
        var content = "Fake provider response: " + lastMessage.content();
        var promptTokens = request.messages().stream()
                .mapToInt(message -> message.content().length())
                .sum();
        return new ProviderCompletion("fake-small", content, promptTokens, content.length());
    }
}

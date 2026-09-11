package com.arbiter.gateway;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

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

    @Override
    public Flux<String> stream(ChatCompletionRequest request) {
        var completion = complete(request);
        return Flux.fromArray(completion.content().split("(?<=\\s)"))
                .delayElements(Duration.ofMillis(10), Schedulers.parallel());
    }
}

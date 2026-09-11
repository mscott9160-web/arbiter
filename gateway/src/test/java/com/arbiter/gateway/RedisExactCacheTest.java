package com.arbiter.gateway;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisExactCacheTest {
    @Test
    void missingRedisValueIsACacheMiss() {
        var redis = mock(StringRedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(ExactCacheKey.key("acme", request()))).thenReturn(null);

        var cache = new RedisExactCache(redis, new ObjectMapper(), 60);

        assertThat(cache.get("acme", request())).isNull();
    }

    private ChatCompletionRequest request() {
        return new ChatCompletionRequest(
                "auto", List.of(new ChatCompletionRequest.Message("user", "hello")), false,
                new ChatCompletionRequest.ArbiterOptions(null, "standard", "allow", "acme"), null, List.of());
    }
}

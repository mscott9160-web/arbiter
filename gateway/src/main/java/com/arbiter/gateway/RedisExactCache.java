package com.arbiter.gateway;

import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
public final class RedisExactCache implements ExactCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisExactCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${arbiter.cache-ttl-seconds:3600}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public ChatCompletionController.ChatCompletionResponse get(String tenantId, ChatCompletionRequest request) {
        var value = redis.opsForValue().get(ExactCacheKey.key(tenantId, request));
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, ChatCompletionController.ChatCompletionResponse.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("cached completion is not valid JSON", error);
        }
    }

    @Override
    public void put(String tenantId, ChatCompletionRequest request,
            ChatCompletionController.ChatCompletionResponse response) {
        try {
            var key = ExactCacheKey.key(tenantId, request);
            redis.opsForValue().set(key, objectMapper.writeValueAsString(response), ttl);
            redis.opsForSet().add(ExactCacheKey.tenantIndexKey(tenantId), key);
            redis.expire(ExactCacheKey.tenantIndexKey(tenantId), ttl);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("completion is not JSON serializable", error);
        }
    }

    @Override
    public void invalidateTenant(String tenantId) {
        var indexKey = ExactCacheKey.tenantIndexKey(tenantId);
        var keys = redis.opsForSet().members(indexKey);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        redis.delete(indexKey);
    }
}

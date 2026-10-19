package com.arbiter.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Profile("pgvector")
public final class PgVectorSemanticCache implements SemanticCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(PgVectorSemanticCache.class);
    private static final String FIND_SQL = """
            SELECT response
            FROM semantic_cache_entries
            WHERE tenant_id = ? AND task_class = ? AND expires_at > now()
              AND (embedding <=> CAST(? AS vector)) <= ?
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT 1
            """;
    private static final String PUT_SQL = """
            INSERT INTO semantic_cache_entries
                (tenant_id, task_class, prompt, prompt_hash, embedding, response, expires_at)
            VALUES (?, ?, ?, ?, CAST(? AS vector), CAST(? AS jsonb), ?::timestamptz)
            ON CONFLICT (tenant_id, prompt_hash) DO UPDATE
                SET response = EXCLUDED.response, embedding = EXCLUDED.embedding,
                    expires_at = EXCLUDED.expires_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingClient embeddingClient;
    private final double distanceThreshold;
    private final long ttlSeconds;

    public PgVectorSemanticCache(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EmbeddingClient embeddingClient,
            @Value("${arbiter.semantic-threshold:0.95}") double similarityThreshold,
            @Value("${arbiter.cache-ttl-seconds:3600}") long ttlSeconds) {
        if (similarityThreshold < 0 || similarityThreshold > 1 || ttlSeconds < 1) {
            throw new IllegalArgumentException("invalid semantic cache configuration");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingClient = embeddingClient;
        this.distanceThreshold = 1.0 - similarityThreshold;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public ChatCompletionController.ChatCompletionResponse find(String tenantId, String taskClass, String prompt) {
        try {
            var vector = vectorLiteral(embeddingClient.embed(prompt));
            var responses = jdbcTemplate.query(
                    FIND_SQL,
                    (resultSet, rowNum) -> readResponse(resultSet.getString("response")),
                    tenantId, taskClass, vector, distanceThreshold, vector);
            return responses.isEmpty() ? null : responses.get(0);
        } catch (RuntimeException error) {
            LOGGER.error("semantic cache lookup failed for tenant {}", tenantId, error);
            throw error;
        }
    }

    @Override
    public void put(String tenantId, String taskClass, String prompt,
            ChatCompletionController.ChatCompletionResponse response) {
        try {
            jdbcTemplate.update(PUT_SQL, tenantId, taskClass, prompt, sha256(prompt),
                    vectorLiteral(embeddingClient.embed(prompt)), objectMapper.writeValueAsString(response),
                    Instant.now().plusSeconds(ttlSeconds).toString());
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("completion is not JSON serializable", error);
        } catch (RuntimeException error) {
            LOGGER.error("semantic cache write failed for tenant {}: {}", tenantId, error.getMessage());
            throw error;
        }
    }

    @Override
    public void invalidateTenant(String tenantId) {
        jdbcTemplate.update("DELETE FROM semantic_cache_entries WHERE tenant_id = ?", tenantId);
    }

    private ChatCompletionController.ChatCompletionResponse readResponse(String value) {
        try {
            return objectMapper.readValue(value, ChatCompletionController.ChatCompletionResponse.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("cached completion is not valid JSON", error);
        }
    }

    private String vectorLiteral(float[] values) {
        if (values.length != 64) {
            throw new IllegalArgumentException("semantic cache embeddings must have 64 dimensions");
        }
        var norm = 0.0;
        for (var value : values) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        var builder = new StringBuilder("[");
        for (var index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values[index] / norm);
        }
        return builder.append(']').toString();
    }

    private String sha256(String value) {
        return HexFormat.of().formatHex(MessageDigestHolder.digest(value));
    }

    private static final class MessageDigestHolder {
        private static byte[] digest(String value) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException error) {
                throw new IllegalStateException("SHA-256 is unavailable", error);
            }
        }
    }
}

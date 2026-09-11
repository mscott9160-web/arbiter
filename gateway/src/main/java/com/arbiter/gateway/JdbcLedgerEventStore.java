package com.arbiter.gateway;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcLedgerEventStore implements LedgerEventStore {
    private static final String INSERT_SQL = """
            INSERT INTO request_events (request_id, ts, tenant_id, event)
            VALUES (?::uuid, ?::timestamptz, ?, ?::jsonb)
            ON CONFLICT (request_id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcLedgerEventStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void writeBatch(List<LedgerEvent> events) {
        events.forEach(event -> {
            requiredString(event.payload(), "ts");
            requiredString(event.payload(), "tenant_id");
            serialize(event.payload());
        });
        jdbcTemplate.batchUpdate(INSERT_SQL, events, events.size(), (statement, event) -> {
            statement.setString(1, event.requestId());
            statement.setString(2, requiredString(event.payload(), "ts"));
            statement.setString(3, requiredString(event.payload(), "tenant_id"));
            statement.setString(4, serialize(event.payload()));
        });
    }

    private String requiredString(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException("ledger payload requires non-empty " + key);
        }
        if ("ts".equals(key)) {
            try {
                Instant.parse(stringValue.replace("Z", "Z"));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("ledger payload ts must be RFC3339", error);
            }
        }
        return stringValue;
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("ledger payload is not JSON serializable", error);
        }
    }
}

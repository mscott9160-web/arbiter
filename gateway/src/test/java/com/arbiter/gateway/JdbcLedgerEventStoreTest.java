package com.arbiter.gateway;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcLedgerEventStoreTest {
    @Test
    void writesEventsUsingBatchInsertWithDatabaseIdempotency() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var store = new JdbcLedgerEventStore(jdbcTemplate, new ObjectMapper());
        var event = new LedgerEvent("00000000-0000-4000-8000-000000000001", Map.of(
                "ts", "2026-09-11T12:00:00Z",
                "tenant_id", "demo",
                "cost", Map.of("usd", "unpriced")));

        store.writeBatch(List.of(event));

        verify(jdbcTemplate).batchUpdate(anyString(), any(List.class), anyInt(), any(ParameterizedPreparedStatementSetter.class));
    }

    @Test
    void rejectsEventsWithoutAuditTimestampOrTenant() {
        var store = new JdbcLedgerEventStore(mock(JdbcTemplate.class), new ObjectMapper());
        var event = new LedgerEvent("00000000-0000-4000-8000-000000000001", Map.of("cost", "unpriced"));

        assertThatIllegalArgumentException().isThrownBy(() -> store.writeBatch(List.of(event)));
    }
}

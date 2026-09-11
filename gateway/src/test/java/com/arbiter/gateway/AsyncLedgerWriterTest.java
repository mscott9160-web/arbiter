package com.arbiter.gateway;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncLedgerWriterTest {
    @Test
    void batchesEventsAndLeavesNoPendingEventsAfterFlush() {
        var store = new RecordingStore();
        try (var writer = new AsyncLedgerWriter(store, 10, Duration.ofHours(1), 2)) {
            writer.submit(event("one"));
            writer.submit(event("two"));
            assertThat(store.batches).isEmpty();

            writer.flushNow();

            assertThat(store.batches).containsExactly(List.of(event("one"), event("two")));
            assertThat(writer.pendingCount()).isZero();
        }
    }

    @Test
    void retriesTransientStoreFailureAndPreservesTheBatch() {
        var store = new RecordingStore();
        store.failuresRemaining.set(1);
        try (var writer = new AsyncLedgerWriter(store, 10, Duration.ofHours(1), 2)) {
            writer.submit(event("retry-me"));
            writer.flushNow();

            assertThat(store.attempts).isEqualTo(2);
            assertThat(writer.pendingCount()).isZero();
        }
    }

    @Test
    void storeContractCanDeduplicateRequestIds() {
        var store = new RecordingStore();
        try (var writer = new AsyncLedgerWriter(store, 10, Duration.ofHours(1), 2)) {
            writer.submit(event("same-id"));
            writer.flushNow();
            writer.submit(event("same-id"));
            writer.flushNow();

            assertThat(store.persistedRequestIds).containsExactly("same-id");
        }
    }

    private LedgerEvent event(String requestId) {
        return new LedgerEvent(requestId, Map.of("status", "test"));
    }

    private static final class RecordingStore implements LedgerEventStore {
        private final List<List<LedgerEvent>> batches = new ArrayList<>();
        private final List<String> persistedRequestIds = new ArrayList<>();
        private final AtomicInteger failuresRemaining = new AtomicInteger();
        private int attempts;

        @Override
        public void writeBatch(List<LedgerEvent> events) {
            attempts++;
            if (failuresRemaining.getAndDecrement() > 0) {
                throw new IllegalStateException("transient store failure");
            }
            batches.add(events);
            events.stream()
                    .map(LedgerEvent::requestId)
                    .filter(requestId -> !persistedRequestIds.contains(requestId))
                    .forEach(persistedRequestIds::add);
        }
    }
}

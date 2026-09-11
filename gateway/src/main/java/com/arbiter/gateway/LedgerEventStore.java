package com.arbiter.gateway;

import java.util.List;

public interface LedgerEventStore {
    /**
     * Persists a batch idempotently by requestId. A successful return means the batch is durable.
     */
    void writeBatch(List<LedgerEvent> events);
}

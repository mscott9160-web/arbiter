package com.arbiter.gateway;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AsyncLedgerWriter implements AutoCloseable {
    private final LedgerEventStore store;
    private final int batchSize;
    private final int maxAttempts;
    private final LinkedBlockingQueue<LedgerEvent> queue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();

    public AsyncLedgerWriter(LedgerEventStore store, int batchSize, Duration flushInterval, int maxAttempts) {
        if (batchSize < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("batch size and max attempts must be positive");
        }
        this.store = store;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        scheduler.scheduleAtFixedRate(this::flushQuietly,
                flushInterval.toMillis(), flushInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void submit(LedgerEvent event) {
        if (closed.get()) {
            throw new IllegalStateException("ledger writer is closed");
        }
        queue.add(event);
        if (queue.size() >= batchSize) {
            flushQuietly();
        }
    }

    public synchronized void flushNow() {
        if (queue.isEmpty()) {
            return;
        }
        var batch = new ArrayList<LedgerEvent>(batchSize);
        queue.drainTo(batch, batchSize);
        try {
            writeWithRetry(batch);
        } catch (RuntimeException error) {
            batch.forEach(queue::add);
            throw error;
        }
    }

    public int pendingCount() {
        return queue.size();
    }

    private void flushQuietly() {
        try {
            flushNow();
        } catch (RuntimeException ignored) {
            // The failed batch remains queued for the next scheduled attempt.
        }
    }

    private void writeWithRetry(List<LedgerEvent> batch) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                store.writeBatch(List.copyOf(batch));
                return;
            } catch (RuntimeException error) {
                lastError = error;
            }
        }
        throw new IllegalStateException("ledger batch was not persisted after retries", lastError);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            scheduler.shutdownNow();
        }
    }
}

package com.chua.starter.sync.data.support.sync.sink;

import com.chua.common.support.sync.Position;
import com.chua.common.support.sync.Sink;
import com.chua.common.support.sync.SyncContext;
import com.chua.common.support.sync.executor.SinkExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Minimal in-memory sink used to bridge inputs and outputs when no explicit data center is configured.
 */
public class InMemorySyncSink implements Sink {

    private final BlockingQueue<SyncContext> queue;
    private final Map<String, Position> currentPositions = new ConcurrentHashMap<>();
    private final Sinks.Many<SyncContext> flux = Sinks.many().multicast().onBackpressureBuffer();

    @SuppressWarnings("unused")
    private volatile SinkExecutor executor;

    public InMemorySyncSink(int capacity) {
        this.queue = new LinkedBlockingQueue<>(Math.max(capacity, 1000));
    }

    @Override
    public void initialize() {
        // no-op
    }

    @Override
    public void receiveBatch(List<SyncContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return;
        }

        try {
            for (SyncContext context : contexts) {
                queue.put(context);
                updatePosition(context);
                flux.tryEmitNext(context);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sink write interrupted", e);
        }
    }

    @Override
    public List<SyncContext> consumeBatch(int batchSize) {
        int maxBatchSize = Math.max(batchSize, 1);
        List<SyncContext> result = new ArrayList<>(maxBatchSize);
        queue.drainTo(result, maxBatchSize);
        if (result.isEmpty()) {
            SyncContext context = queue.poll();
            if (context != null) {
                result.add(context);
            }
        }
        return result;
    }

    @Override
    public Position getCurrentPosition() {
        return currentPositions.values().stream().findFirst().orElse(null);
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public Flux<SyncContext> getFlux() {
        return flux.asFlux();
    }

    @Override
    public void sendToDeadLetter(SyncContext context, Exception e) {
        // no-op for the compatibility sink
    }

    @Override
    public void setExecutor(SinkExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void notifyInput() {
        // no-op
    }

    @Override
    public boolean isBackpressure() {
        return queue.remainingCapacity() == 0;
    }

    @Override
    public void close() {
        queue.clear();
        flux.tryEmitComplete();
    }

    private void updatePosition(SyncContext context) {
        if (context == null || context.getPosition() == null) {
            return;
        }

        String inputId = context.getInputId();
        if (inputId == null || inputId.isBlank()) {
            inputId = context.getPosition().getInputId();
        }
        if (inputId != null && !inputId.isBlank()) {
            currentPositions.put(inputId, context.getPosition());
        }
    }
}

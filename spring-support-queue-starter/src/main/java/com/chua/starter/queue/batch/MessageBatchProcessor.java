package com.chua.starter.queue.batch;

import com.chua.starter.queue.Message;
import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 消息批量处理器
 * <p>
 * 将多个消息聚合后批量处理，提高处理效率
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Data
public class MessageBatchProcessor {

    /**
     * 批量大小
     */
    private int batchSize = 100;

    /**
     * 批量超时时间
     */
    private Duration batchTimeout = Duration.ofSeconds(5);

    /**
     * 当前批次
     */
    private final List<Message> currentBatch = new ArrayList<>();

    /**
     * 最后一次添加时间
     */
    private long lastAddTime = System.currentTimeMillis();

    /**
     * 批量处理器
     */
    private Consumer<List<Message>> batchHandler;

    /**
     * 添加消息到批次
     *
     * @param message 消息
     */
    public synchronized void add(Message message) {
        currentBatch.add(message);
        lastAddTime = System.currentTimeMillis();

        if (shouldFlush()) {
            flush();
        }
    }

    /**
     * 是否应该刷新批次
     */
    private boolean shouldFlush() {
        if (currentBatch.size() >= batchSize) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - lastAddTime;
        return elapsed >= batchTimeout.toMillis();
    }

    /**
     * 刷新批次
     */
    public synchronized void flush() {
        if (currentBatch.isEmpty()) {
            return;
        }

        List<Message> batch = new ArrayList<>(currentBatch);
        currentBatch.clear();

        if (batchHandler != null) {
            batchHandler.accept(batch);
        }
    }

    /**
     * 获取当前批次大小
     */
    public synchronized int getCurrentBatchSize() {
        return currentBatch.size();
    }
}

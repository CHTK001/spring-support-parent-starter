// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.client;

import com.chua.starter.logger.event.LoggerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志事件批量推送线程：从 {@link LoggerEventCollector} 持续取事件，
 * 攒满 {@code batchSize} 或超过 {@code flushIntervalMs} 即整批序列化为
 * 单行 JSON 数组，交 {@link TcpLoggerTransport} 发送。
 *
 * <p>单独平台线程承载（非虚拟线程）：长生命周期常驻循环，避免载体线程长期被占。
 * 停止时先排空队列再退出，尽量不丢尾批。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class LoggerEventBatcher implements SmartLifecycle {

    private final String appName;
    private final String instance;
    private final LoggerEventCollector collector;
    private final TcpLoggerTransport transport;
    private final int batchSize;
    private final long flushIntervalMs;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile boolean running;
    private Thread worker;

    /**
     * @param appName         应用名（事件缺省时补戳）
     * @param instance        实例标识 pid@host（事件缺省时补戳）
     * @param collector       采集队列
     * @param transport       传输通道
     * @param batchSize       批量阈值
     * @param flushIntervalMs 刷新间隔（毫秒）
     */
    public LoggerEventBatcher(String appName, String instance,
                              LoggerEventCollector collector, TcpLoggerTransport transport,
                              int batchSize, long flushIntervalMs) {
        this.appName = appName;
        this.instance = instance;
        this.collector = collector;
        this.transport = transport;
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMs = Math.max(50L, flushIntervalMs);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        worker = Thread.ofPlatform().name("logger-event-batcher").daemon(true).start(this::loop);
        log.info("[logger] 批量推送线程已启动: batchSize={}, flushIntervalMs={}", batchSize, flushIntervalMs);
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 晚于默认生命周期启动、早于其停止，保证发送线程先于 Appender 卸载就绪
        return Integer.MAX_VALUE - 100;
    }

    private void loop() {
        List<LoggerEvent> batch = new ArrayList<>(batchSize);
        long lastFlush = System.currentTimeMillis();
        while (running) {
            try {
                LoggerEvent event = collector.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (event != null) {
                    if (batch.isEmpty()) {
                        lastFlush = System.currentTimeMillis();
                    }
                    batch.add(event);
                    if (batch.size() >= batchSize) {
                        flush(batch);
                        batch.clear();
                        lastFlush = System.currentTimeMillis();
                    }
                    continue;
                }
                if (!batch.isEmpty() && System.currentTimeMillis() - lastFlush >= flushIntervalMs) {
                    flush(batch);
                    batch.clear();
                    lastFlush = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 单轮异常不终止推送循环
            }
        }
        drain(batch);
    }

    /**
     * 停机前排空队列，尽量不丢尾批。
     */
    private void drain(List<LoggerEvent> batch) {
        LoggerEvent remaining;
        while ((remaining = collector.poll()) != null) {
            batch.add(remaining);
            if (batch.size() >= batchSize) {
                flush(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            flush(batch);
        }
    }

    private void flush(List<LoggerEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }
        for (LoggerEvent event : batch) {
            if (event.getApp() == null || event.getApp().isBlank()) {
                event.setApp(appName);
            }
            if (event.getInstance() == null || event.getInstance().isBlank()) {
                event.setInstance(instance);
            }
        }
        try {
            String payload = mapper.writeValueAsString(batch);
            transport.send(payload);
        } catch (Exception e) {
            log.debug("[logger] 批次序列化/发送失败, size={}, cause={}", batch.size(), e.getMessage());
        }
    }
}

package com.chua.starter.datasync.dbprobe;

import com.chua.chronicle.support.dispatcher.ChronicleDispatcherProvider;
import com.chua.common.support.concurrent.dispatcher.ConsumerDispatcherDefinition;
import com.chua.common.support.concurrent.dispatcher.DispatcherConfig;
import com.chua.common.support.concurrent.dispatcher.DispatcherProvider;
import com.chua.common.support.concurrent.dispatcher.provider.InMemoryDispatcherProvider;
import com.chua.common.support.concurrent.dispatcher.provider.WalDispatcherProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 四种派发提供者压测对比：InMemory / WAL / Chronicle。
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class DataSyncDispatcherBenchProbe {

    static final int ROWS = 100_000;
    static final int BATCH = 1000;

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("dispatch-bench");
        tmp.toFile().deleteOnExit();

        System.err.println("========================================================================");
        System.err.println("派发提供者压测 (" + ROWS + " 行, batch=" + BATCH + ")");
        System.err.println("========================================================================\n");
        System.err.printf("%-22s %-10s %-10s %-14s %-10s%n", "提供者", "耗时(ms)", "吞吐(行/s)", "完成", "说明");
        System.err.println("------------------------------------------------------------------------");

        List<Map<String, Object>> data = makeData(BATCH);

        // InMemory
        bench("InMemory", new InMemoryDispatcherProvider(DispatcherConfig.builder().build()), data);

        // WAL (默认 Jackson)
        bench("WAL(Jackson)", new WalDispatcherProvider(
                DispatcherConfig.builder().dataPath(tmp.resolve("wal").toString()).build()), data);

        // WAL + Fury (config.serializer 统一 SPI 初始化)
        try {
            bench("WAL+Fury", new WalDispatcherProvider(
                    DispatcherConfig.builder()
                            .dataPath(tmp.resolve("wal-fury").toString())
                            .serializer("fury")
                            .build()), data);
        } catch (Throwable t) {
            System.err.printf("%-22s %s%n", "WAL+Fury", "初始化失败: " + t.getMessage());
        }

        // Chronicle (需 --add-opens)
        try {
            bench("Chronicle", new ChronicleDispatcherProvider(
                    DispatcherConfig.builder().dataPath(tmp.resolve("chr").toString()).build()), data);
        } catch (Throwable t) {
            System.err.printf("%-22s %s%n", "Chronicle", "初始化失败: " + t.getMessage());
        }

        System.err.println("========================================================================");
    }

    static List<Map<String, Object>> makeData(int n) {
        List<Map<String, Object>> data = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", i);
            row.put("name", "user_" + i);
            row.put("age", 20 + i % 40);
            row.put("score", (i * 7) % 100 + 1.0);
            row.put("active", i % 2 == 0);
            row.put("created_at", "2026-01-01");
            data.add(row);
        }
        return data;
    }

    static void bench(String label, DispatcherProvider provider, List<Map<String, Object>> data) throws Exception {
        AtomicLong count = new AtomicLong();
        CountDownLatch done = new CountDownLatch(1);
        Consumer<List<Map<String, Object>>> consumer = rows -> {
            long c = count.addAndGet(rows.size());
            if (c >= ROWS) done.countDown();
        };
        provider.subscribe(new ConsumerDispatcherDefinition<>(consumer, List.class, List.of("bench")));

        long rounds = ROWS / BATCH;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < rounds; i++) {
            provider.publish("bench", data);
        }
        boolean ok = done.await(60, TimeUnit.SECONDS);
        long ms = System.currentTimeMillis() - t0;
        long tp = count.get() * 1000L / Math.max(1, ms);

        System.err.printf("%-22s %-10d %-10s %-14s %-10s%n",
                label, ms, String.format("%,d", tp), ok ? "PASS" : "PARTIAL(" + count.get() + ")", "");
        provider.close();
    }
}
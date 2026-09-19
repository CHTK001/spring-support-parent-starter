package com.chua.starter.datasync.dbprobe;

import com.chua.chronicle.support.dispatcher.ChronicleDispatcherProvider;
import com.chua.common.support.concurrent.dispatcher.ConsumerDispatcherDefinition;
import com.chua.common.support.concurrent.dispatcher.DispatcherConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Chronicle mmap block大小 压测：不同块大小下的 pub/sub 吞吐。
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class DataSyncBlockSizeProbe {

    static final int ROWS = 100_000; // ROWS
    static final int BATCH = 1000; // 批量

    /**
     * Main
     *
     * @param args 参数
     */
    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("chronicle-blocksize");
        tmp.toFile().deleteOnExit();

        long[] blockSizes = {1L << 20, 8L << 20, 64L << 20, 256L << 20};
        System.err.println("=========================================================================");
        System.err.println("Chronicle mmap blockSize 压测 (" + ROWS + " 行, batch=" + BATCH + ")");
        System.err.println("=========================================================================");
        System.err.printf("%-14s %-10s %-14s %-18s %-10s%n", "blockSize", "大小", "耗时(ms)", "吞吐(行/s)", "完成");
        System.err.println("-------------------------------------------------------------------------");

        List<Map<String, Object>> data = new ArrayList<>(BATCH);
        for (int i = 0; i < BATCH; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", i);
            row.put("name", "user_" + i);
            row.put("age", 20 + i % 40);
            row.put("score", (i * 7) % 100 + 1.0);
            row.put("active", i % 2 == 0);
            row.put("created_at", "2026-01-01");
            data.add(row);
        }

        for (long bs : blockSizes) {
            bench(tmp, bs, data);
            // 清理旧队列
            tmp.toFile().listFiles(f -> f.getName().contains("queue"))[0].delete();
        }
        System.err.println("=========================================================================");
    }

    /**
     * Bench
     *
     * @param tmp tmp
     * @param blockSize block大小
     * @param data 数据
     */
    static void bench(Path tmp, long blockSize, List<Map<String, Object>> data) throws Exception {
        Path dataPath = tmp.resolve("queue-" + blockSize);
        ChronicleDispatcherProvider provider = null;
        try {
            provider = new ChronicleDispatcherProvider(
                    DispatcherConfig.builder().dataPath(dataPath.toString()).blockSize(blockSize).build());
            AtomicLong count = new AtomicLong();
            CountDownLatch done = new CountDownLatch(1);
            Consumer<List<Map<String, Object>>> consumer = rows -> {
                if (count.addAndGet(rows.size()) >= 1) {
                    done.countDown();
                }
            };
            provider.subscribe(new ConsumerDispatcherDefinition<>(consumer, List.class, List.of("bench")));

            long rounds = ROWS / BATCH;
            long t0 = System.currentTimeMillis();
            for (int i = 0; i < rounds; i++) {
                provider.publish("bench", data);
            }
            boolean ok = done.await(30, TimeUnit.SECONDS);
            long ms = System.currentTimeMillis() - t0;
            long tp = count.get() * 1000L / Math.max(1, ms);

            String label = blockSize >= 1 << 20 ? (blockSize >> 20) + "MB" : (blockSize >> 10) + "KB";
            System.err.printf("%-14s %-10s %-14d %-18s %-10s%n",
                    label, (blockSize >> 20) + "MB", ms,
                    tp == 0 ? "?" : String.format("%,d", tp), ok ? "PASS" : "FAIL(" + count.get() + ")");
        } finally {
            if (provider != null) {
                provider.close();
            }
        }
    }
}
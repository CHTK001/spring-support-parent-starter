package com.chua.starter.datasync.dbprobe;

import com.chua.datasync.agent.support.DataSyncAgentSink;
import com.chua.datasync.agent.support.source.DelimitedDataSyncAgentSource;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.agent.DataSyncAgentServer;
import com.chua.starter.datasync.mapping.DefaultDataSyncMapping;
import com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager;
import com.chua.starter.datasync.model.DataSyncMapping;
import com.chua.starter.datasync.scheduler.DefaultSyncDataSchedulerManager;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据同步完整管线压测：走 服务端→调度器→执行器→Dispatch→Sink 全链路，手动触发单遍。
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class DataSyncStressProbe {

    /** Work_dir */
    private static final Path WORK_DIR = Paths.get("target/stress-full");

    /**
    * Main
    *
    * @param args 参数
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK_DIR);
        int[] rows = {100_000_000};
        int[] batches = {1000};

        System.err.println("========================================================================");
        System.err.println("数据同步完整管线压测 (全链路 + 手动单遍触发)");
        System.err.println("========================================================================");
        System.err.printf("%-12s %-10s %-12s %-14s %-12s %-16s%n",
                "行数", "batch", "耗时(ms)", "吞吐(行/s)", "sink批数", "数据校验");
        System.err.println("------------------------------------------------------------------------");

        for (int rowCount : rows) {
            Path csv = WORK_DIR.resolve("stress-" + rowCount + ".csv");
            if (!Files.exists(csv)) {
                long t0 = System.currentTimeMillis();
                generateCsv(csv, rowCount);
                System.err.println("[STRESS] 生成 " + rowCount + " 行 CSV: " + (System.currentTimeMillis() - t0) + "ms");
            }
            for (int batch : batches) {
                runFullPipeline(csv, rowCount, batch);
            }
        }
        System.err.println("========================================================================");
    }

    /**
     * 运行完整pipeline
     *
     * @param csv csv
     * @param totalRows totalrows
     * @param batchSize 批量大小
     */
    private static void runFullPipeline(Path csv, int totalRows, int batchSize) throws Exception {
        String id = "full-" + totalRows + "-" + batchSize;

        DefaultSyncDataSchedulerManager.SchedulerConfig config =
                DefaultSyncDataSchedulerManager.SchedulerConfig.builder()
                        .flatMapParallelism(10)
                        .maxBufferRows(Long.MAX_VALUE)
                        .retryMaxAttempts(0)
                        .build();

        DefaultDataSyncServer server = new DefaultDataSyncServer(
                new DataSyncAgentServer(null, null, "stress-full"), config);
        server.setMappingManager(new DefaultDataSyncMappingManager());

        CountSink sink = new CountSink(id + "-sink", totalRows);
        server.registerSource(new DelimitedDataSyncAgentSource(id + "-src", id + "-input", csv.toString(), ","));
        server.registerSink(sink);

 // 无 型：不参与 cron 轮询，仅手动触发单遍
        server.addMapping(new DefaultDataSyncMapping(id, id + "-input", id + "-src", id + "-output", id + "-sink",
                null, List.of(), batchSize, "none", "",
                Map.of(), null));

        server.start();
        long t0 = System.currentTimeMillis();
        server.schedulerManager().trigger(id);
        long triggerMs = System.currentTimeMillis() - t0;
        System.err.println("[TIMING] trigger 返回耗时: " + triggerMs + "ms");
        long start = System.currentTimeMillis();
        boolean done = sink.done.await(180, TimeUnit.SECONDS);
        long elapsedMs = System.currentTimeMillis() - start;
        server.stop();

        long tp = (sink.count.get() > 0) ? (sink.count.get() * 1000L / Math.max(1L, elapsedMs)) : 0L;
        long batches = sink.batches.get();
        boolean ok = done && sink.count.get() == totalRows;
        System.err.printf("%-12d %-10d %-12d %-14d %-12d %-16s%n",
                totalRows, batchSize, elapsedMs, tp, batches, ok ? "PASS" : "FAIL(" + sink.count.get() + ")");
    }

    /**
     * generatecsv
     *
     * @param path 路径
     * @param lines 线
     */
    private static void generateCsv(Path path, int lines) throws Exception {
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("id,name,age,score,active,created_at\n");
            for (int i = 0; i < lines; i++) {
                writer.write(i + ",user_" + i + "," + (20 + i % 40) + ","
                        + String.format("%.1f", (i * 7) % 100 + 1.0) + ","
                        + (i % 2 == 0) + ",2026-01-01\n");
            }
        }
    }

    static final class CountSink implements DataSyncAgentSink {
        /** sinkid */
        private final String sinkId;
        /** 期望 */
        private final long expected;
        final AtomicLong count = new AtomicLong(0);
        final AtomicLong batches = new AtomicLong(0);
        final AtomicLong totalDispatchMs = new AtomicLong(0);
        volatile long firstBatchMs = 0;
        volatile long lastBatchMs = 0;
        final CountDownLatch done;

        CountSink(String sinkId, long expected) {
            this.sinkId = sinkId;
            this.expected = expected;
            this.done = new CountDownLatch(1);
        }

        @Override
        /** sinkid */
        public String sinkId() {
            return sinkId;
        }

        @Override
        /** 写入 */
        public void write(Flux<Map<String, Object>> rows) {
            long t0 = System.currentTimeMillis();
            long b = batches.incrementAndGet();
            if (b == 1) {
                firstBatchMs = t0;
            }
            rows.doOnNext(m -> {
                if (count.incrementAndGet() >= expected) {
                    done.countDown();
                }
            }).subscribe();
            long cost = System.currentTimeMillis() - t0;
            totalDispatchMs.addAndGet(cost);
            lastBatchMs = System.currentTimeMillis();
            if (cost > 100) {
                System.err.println("[SLOW] batch=" + b + " cost=" + cost + "ms count=" + count.get());
            }
        }

        @Override
        /** 关闭 */
        public void close() {
        }
    }
}
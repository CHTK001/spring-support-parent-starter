package com.chua.starter.datasync.dbprobe;

import com.chua.common.support.task.scheduler.CronTrigger;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 数据同步压测探针：生成超大 CSV 文件进行真实压测。
 *
 * <p>测试不同行数、不同 batch 大小下的吞吐、内存与耗时。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class DataSyncStressProbe {

    private static final Path WORK_DIR = Paths.get("target/stress-test");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK_DIR);
        int[] rows = {10_000, 100_000};
        int[] batches = {100, 1000, 10000};

        System.out.println("========================================================================");
        System.out.println("数据同步管线压测");
        System.out.println("========================================================================");
        System.out.printf("%-12s %-10s %-12s %-12s %-12s %-12s %-20s%n",
                "行数", "batch", "总耗时(ms)", "吞吐(行/s)", "首轮(ms)", "总行数", "执行线程");
        System.out.println("------------------------------------------------------------------------");

        for (int rowCount : rows) {
            for (int batch : batches) {
                runTest(rowCount, batch);
            }
        }
        System.out.println("========================================================================");
    }

    private static void runTest(int totalRows, int batchSize) throws Exception {
        Path csv = WORK_DIR.resolve("stress-" + totalRows + ".csv");
        if (!Files.exists(csv)) {
            generateCsv(csv, totalRows);
        }

        String id = "stress-" + totalRows + "-" + batchSize;
        String srcId = id + "-src";
        String snkId = id + "-sink";

        DefaultSyncDataSchedulerManager.SchedulerConfig config =
                DefaultSyncDataSchedulerManager.SchedulerConfig.builder()
                        .flatMapParallelism(10)
                        .maxBufferRows(Long.MAX_VALUE)
                        .retryMaxAttempts(0)
                        .build();
        DefaultDataSyncServer server = new DefaultDataSyncServer(
                new DataSyncAgentServer(null, null, "stress-probe"), config);
        server.setMappingManager(new DefaultDataSyncMappingManager());

        TimerSink sink = new TimerSink(snkId);
        server.registerSource(new DelimitedDataSyncAgentSource(srcId, id + "-input", csv.toString(), ","));
        server.registerSink(sink);

        DataSyncMapping mapping = new DefaultDataSyncMapping(
                id, id + "-input", srcId, id + "-output", snkId,
                null, List.of(), batchSize, "fixed", "0/1 * * * * ?",
                Map.of(), new CronTrigger("0/1 * * * * ?", LocalDateTime.now()));
        server.addMapping(mapping);
        server.start();
        Thread.sleep(Math.max(3000, totalRows / 200));
        server.stop();

        long tp = (sink.total.sum() > 0) ? (sink.total.sum() * 1000L / Math.max(1L, sink.totalMs)) : 0L;
        System.out.printf("%-12d %-10d %-12d %-12d %-12d %-12d %-20s%n",
                totalRows, batchSize, sink.totalMs, tp, sink.firstMs, sink.total.sum(), sink.threadName);
    }

    private static void generateCsv(Path path, int lines) throws Exception {
        long t0 = System.currentTimeMillis();
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("id,name,age,score,active,created_at\n");
            for (int i = 0; i < lines; i++) {
                writer.write(i + ",user_" + i + "," + (20 + i % 40) + ","
                        + String.format("%.1f", (i * 7) % 100 + 1.0) + ","
                        + (i % 2 == 0) + ",2026-01-01\n");
            }
        }
        System.out.println("[STRESS] 生成 " + lines + " 行 CSV: " + (System.currentTimeMillis() - t0) + "ms");
    }

    static final class TimerSink implements DataSyncAgentSink {
        private final String id;
        final LongAdder total = new LongAdder();
        volatile long totalMs = 0;
        volatile long firstMs = 0;
        volatile String threadName = "-";
        private final java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);

        TimerSink(String id) { this.id = id; }

        @Override public String sinkId() { return id; }

        @Override
        public void write(Flux<Map<String, Object>> rows) {
            long t0 = System.currentTimeMillis();
            rows.doOnNext(m -> total.increment())
                    .collectList()
                    .subscribe(list -> {
                        long cost = System.currentTimeMillis() - t0;
                        totalMs += cost;
                        if (first.compareAndSet(true, false)) firstMs = cost;
                        threadName = Thread.currentThread().getName();
                    });
        }

        @Override public void close() {}
    }
}
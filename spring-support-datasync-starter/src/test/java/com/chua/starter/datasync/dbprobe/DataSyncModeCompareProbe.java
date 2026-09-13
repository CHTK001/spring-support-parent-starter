package com.chua.starter.datasync.dbprobe;
import com.chua.common.support.concurrent.dispatcher.DispatcherConfig;
import com.chua.common.support.concurrent.dispatcher.provider.InMemoryDispatcherProvider;
import com.chua.datasync.agent.support.DataSyncAgentSink;
import com.chua.datasync.agent.support.executor.ReactorDataSyncExecutor;
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
* 数据同步modecompare探针类。
*
* @author CH
* @since 4.0.0
 */
public final class DataSyncModeCompareProbe {
    static final Path CSV = Paths.get("target/stress-compare/source.csv"); // CSV
    static final int ROWS = 100_000; // ROWS
    static final int BATCH = 1000; // 批量
    /**
    * Main
    *
    * @param args 参数
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(CSV.getParent());
        if (!Files.exists(CSV)) {
            try (var w = Files.newBufferedWriter(CSV, StandardCharsets.UTF_8)) {
                w.write("id,name,age,score,active,created_at\n");
                for (int i = 0; i < ROWS; i++) {
                    w.write(i + ",u" + i + "," + (20+i%40) + ",1.0,true,2026-01-01\n");
                }
            }
        }
        System.err.println("========================================================================");
        System.err.println("三种派发模式吞吐对比 (10万行, batch=1000)");
        System.err.println("========================================================================\n");
        testDirect("1. 直连派发 (directDispatch=true)");
        testChronicle("2. Chronicle pub/sub (directDispatch=false)");
        testInMemory("3. InMemory pub/sub (InMemoryDispatcherProvider)");
        System.err.println("========================================================================");
    }
    /**
    * 测试direct
    *
    * @param label 标签
     */
    static void testDirect(String label) throws Exception {
        var cfg = DefaultSyncDataSchedulerManager.SchedulerConfig.builder()
            .directDispatch(true).retryMaxAttempts(0).memorySafeEnabled(false).build();
        var server = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "cmp"), cfg);
        server.setMappingManager(new DefaultDataSyncMappingManager());
        var sink = new CountSink("sink", ROWS);
        server.registerSource(new DelimitedDataSyncAgentSource("src", "in", CSV.toString(), ","));
        server.registerSink(sink);
        server.addMapping(new DefaultDataSyncMapping("cmp", "in", "src", "out", "sink", null, List.of(), BATCH, "none", "", Map.of(), null));
        server.start();
        long t0 = System.currentTimeMillis();
        server.schedulerManager().trigger("cmp");
        sink.done.await(30, TimeUnit.SECONDS);
        long ms = System.currentTimeMillis() - t0;
        server.stop();
        report(label, ms, sink.count.get());
    }
    /**
    * 测试chronicle
    *
    * @param label 标签
     */
    static void testChronicle(String label) throws Exception {
        try {
            var cfg = DefaultSyncDataSchedulerManager.SchedulerConfig.builder()
                .directDispatch(false).retryMaxAttempts(0).memorySafeEnabled(false).build();
            var server = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "cmp"), cfg);
 // 触发 执行器 创建，会尝试初始化 Chronicle
            var executor = server.executorManager().getExecutor("cmp-out");
            executor.start();
            // 如果 Chronicle 初始化成功，则继续
            server.setMappingManager(new DefaultDataSyncMappingManager());
            var sink = new CountSink("sink", ROWS);
            server.registerSource(new DelimitedDataSyncAgentSource("src", "in", CSV.toString(), ","));
            server.registerSink(sink);
            server.addMapping(new DefaultDataSyncMapping("cmp", "in", "src", "out", "sink", null, List.of(), BATCH, "none", "", Map.of(), null));
            server.start();
            long t0 = System.currentTimeMillis();
            server.schedulerManager().trigger("cmp");
            sink.done.await(30, TimeUnit.SECONDS);
            long ms = System.currentTimeMillis() - t0;
            server.stop();
            report(label, ms, sink.count.get());
        } catch (Exception e) {
            System.err.printf("%-45s %s (Java 25 不兼容)%n", label, e.getClass().getSimpleName());
        }
    }
    /**
    * 测试入内存
    *
    * @param label 标签
     */
    static void testInMemory(String label) throws Exception {
        var cfg = DefaultSyncDataSchedulerManager.SchedulerConfig.builder()
            .directDispatch(false).retryMaxAttempts(0).memorySafeEnabled(false).build();
        var server = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "cmp"), cfg);
 // 替换为 入内存dispatcher提供者
        var executor = (ReactorDataSyncExecutor) server.executorManager().getExecutor("out");
        executor.setDispatcherProvider(new InMemoryDispatcherProvider(DispatcherConfig.builder().build()));
        executor.start();
        server.setMappingManager(new DefaultDataSyncMappingManager());
        var sink = new CountSink("sink", ROWS);
        server.registerSource(new DelimitedDataSyncAgentSource("src", "in", CSV.toString(), ","));
        server.registerSink(sink);
        server.addMapping(new DefaultDataSyncMapping("cmp", "in", "src", "out", "sink", null, List.of(), BATCH, "none", "", Map.of(), null));
        server.start();
        long t0 = System.currentTimeMillis();
        server.schedulerManager().trigger("cmp");
        sink.done.await(30, TimeUnit.SECONDS);
        long ms = System.currentTimeMillis() - t0;
        server.stop();
        report(label, ms, sink.count.get());
    }
    /**
    * Report
    *
    * @param label 标签
    * @param ms ms
    * @param count 数量
     */
    static void report(String label, long ms, long count) {
        long tp = count * 1000L / Math.max(1, ms);
        System.err.printf("%-45s %5dms  %,d 行/s  rows=%d%n", label, ms, tp, count);
    }
    static final class CountSink implements DataSyncAgentSink {
        final String id;
        final long exp;
        final AtomicLong count = new AtomicLong();
        final CountDownLatch done;
        CountSink(String id, long exp) {
            this.id = id;
            this.exp = exp;
            this.done = new CountDownLatch(1);
        }
        @Override public String sinkId() { return id; }
        @Override public void write(Flux<Map<String, Object>> rows) {
            rows.doOnNext(m -> { if (count.incrementAndGet() >= exp) done.countDown(); }).subscribe();
        }
        @Override public void close() {}
    }
}


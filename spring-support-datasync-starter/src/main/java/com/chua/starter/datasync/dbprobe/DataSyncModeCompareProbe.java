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
public final class DataSyncModeCompareProbe {
    static final Path CSV = Paths.get("target/stress-compare/source.csv");
    static final int ROWS = 100_000;
    static final int BATCH = 1000;
    public static void main(String[] args) throws Exception {
        Files.createDirectories(CSV.getParent());
        if (!Files.exists(CSV)) {
            try (var w = Files.newBufferedWriter(CSV, StandardCharsets.UTF_8)) {
                w.write("id,name,age,score,active,created_at\n");
                for (int i = 0; i < ROWS; i++) w.write(i + ",u" + i + "," + (20+i%40) + ",1.0,true,2026-01-01\n");
            }
        }
        System.err.println("========================================================================");
        System.err.println("?????????? (10??, batch=1000)");
        System.err.println("========================================================================\n");
        test("directDispatch=true  (????)", true);
        test("directDispatch=false (Chronicle ??, ??????)", false);
        System.err.println("========================================================================");
    }
    static void test(String label, boolean direct) throws Exception {
        var cfg = DefaultSyncDataSchedulerManager.SchedulerConfig.builder()
            .directDispatch(direct).retryMaxAttempts(0).memorySafeEnabled(false).build();
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
        long tp = sink.count.get() * 1000L / Math.max(1, ms);
        System.err.printf("%-45s %5dms  %s ?/s  rowe=%d%n", label, ms, tp == 0 ? "?" : String.format("%,d", tp), sink.count.get());
    }
    static final class CountSink implements DataSyncAgentSink {
        final String id; final long exp; final AtomicLong count = new AtomicLong(); final CountDownLatch done;
        CountSink(String id, long exp) { this.id = id; this.exp = exp; this.done = new CountDownLatch(1); }
        @Override public String sinkId() { return id; }
        @Override public void write(Flux<Map<String, Object>> rows) {
            rows.doOnNext(m -> { if (count.incrementAndGet() >= exp) done.countDown(); }).subscribe();
        }
        @Override public void close() {}
    }
}

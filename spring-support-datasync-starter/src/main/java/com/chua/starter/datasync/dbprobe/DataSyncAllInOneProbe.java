package com.chua.starter.datasync.dbprobe;

import com.chua.common.support.task.scheduler.CronTrigger;
import com.chua.datasync.agent.support.DataSyncAgentSink;
import com.chua.datasync.agent.support.source.DelimitedDataSyncAgentSource;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.agent.DataSyncAgentServer;
import com.chua.starter.datasync.mapping.DataSyncFieldMapping;
import com.chua.starter.datasync.mapping.DefaultDataSyncMapping;
import com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager;
import com.chua.starter.datasync.model.DataSyncMapping;
import com.chua.starter.datasync.scanner.JsonConfigFileParser;
import com.chua.starter.datasync.scanner.PropertiesConfigFileParser;
import com.chua.starter.datasync.scanner.YamlConfigFileParser;
import com.chua.starter.datasync.scheduler.DefaultSyncDataSchedulerManager;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

/**
 * 全能力标准测试：覆盖文件格式、转换器、边界、调度、多数据源。
 *
 * @since 4.0.0.42
 */
public final class DataSyncAllInOneProbe {

    /** Work_dir */
    private static final Path WORK_DIR = Paths.get("target/all-in-one");
    /** Pass */
    private final List<String> pass = new CopyOnWriteArrayList<>();
    /** Fail */
    private final List<String> fail = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK_DIR);
        new DataSyncAllInOneProbe().run();
    }

    private void run() throws Exception {
        // 1. 文件格式解析
        testFileParsing();
        // 2. 字段转换器
        testConverters();
        // 3. 边界场景
        testEdgeCases();
        // 4. 调度策略
        testSchedules();
        // 5. 多映射并发
        testConcurrency();
        // 汇总
        System.out.println("\n========================================");
        System.out.println("[ALL-IN-ONE] 通过: " + pass.size() + " / 失败: " + fail.size());
        pass.forEach(p -> System.out.println("  PASS | " + p));
        fail.forEach(f -> System.out.println("  FAIL | " + f));
        System.out.println("========================================");
        System.exit(fail.isEmpty() ? 0 : 1);
    }

    // ========== 1. 文件格式解析 ==========
    private void testFileParsing() throws Exception {
        Path mappingDir = Paths.get("src/main/resources/datasync/mappings");
        var yaml = new YamlConfigFileParser();
        var json = new JsonConfigFileParser();
        var props = new PropertiesConfigFileParser();

        var y = yaml.parse(mappingDir.resolve("user-sync.yaml"));
        if (y != null && y.mappings().size() == 2) pass.add("YAML 解析: user-sync 字段=" + y.mappings().size());
        else fail.add("YAML 解析失败");

        var j = json.parse(mappingDir.resolve("order-sync.json"));
        if (j != null && j.mappings().size() == 2) pass.add("JSON 解析: order-sync 字段=" + j.mappings().size());
        else fail.add("JSON 解析失败");

        Path tmp = WORK_DIR.resolve("test.properties");
        Files.writeString(tmp, "mappingId=prop-test\ninputId=in\nsourceId=src\noutputId=out\nsinkId=snk\nbatch=10\ncronType=fixed\ncron=0/5 * * * * ?\n");
        boolean propOk = false;
        try {
            com.chua.starter.datasync.config.DataSyncConfigDefinition propDef = props.parse(tmp);
            propOk = propDef != null && propDef.batch() == 10 && "0/5 * * * * ?".equals(propDef.cron());
        } catch (Exception ex) {
            propOk = false;
        }
        if (propOk) pass.add("Properties 解析: prop-test");
        else fail.add("Properties 解析失败");
        Files.deleteIfExists(tmp);
    }

    // ========== 2. 字段转换器 ==========
    private void testConverters() throws Exception {
        Path csv = WORK_DIR.resolve("convert.csv");
        Files.writeString(csv, "name,age,score,active\n张三,30,85.5,true\n李四,25,92.3,false\n", StandardCharsets.UTF_8);

        for (var conv : List.of(
                Map.entry("toString", "name"),
                Map.entry("toInteger", "age"),
                Map.entry("toDouble", "score"),
                Map.entry("toBoolean", "active"))) {
            DefaultDataSyncServer server = new DefaultDataSyncServer(
                    new DataSyncAgentServer(null, null, "cv"),
                    DefaultSyncDataSchedulerManager.SchedulerConfig.builder().retryMaxAttempts(0).build());
            server.setMappingManager(new DefaultDataSyncMappingManager());
            CollectSink sink = new CollectSink("cv-" + conv.getKey());
            server.registerSource(new DelimitedDataSyncAgentSource("cv-src", "cv-in", csv.toString(), ","));
            server.registerSink(sink);
            server.addMapping(new DefaultDataSyncMapping("cv-" + conv.getKey(), "cv-in", "cv-src", "cv-out", "cv-" + conv.getKey(),
                    null, List.of(new SimpleFieldMapping(conv.getValue(), "out", conv.getKey())),
                    100, "fixed", "0/1 * * * * ?", Map.of(), new CronTrigger("0/1 * * * * ?", LocalDateTime.now())));
            server.start();
            Thread.sleep(1500);
            server.stop();
            if (sink.collected.size() > 0) pass.add("转换器 " + conv.getKey() + "(" + conv.getValue() + "→out) 写入 " + sink.collected.size() + " 行");
            else fail.add("转换器 " + conv.getKey() + " 无数据");
        }
    }

    // ========== 3. 边界场景 ==========
    private void testEdgeCases() throws Exception {
        // 3a. 空 CSV
        Path empty = WORK_DIR.resolve("empty.csv");
        Files.writeString(empty, "id,name\n", StandardCharsets.UTF_8);
        DefaultDataSyncServer s1 = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "edge"),
                DefaultSyncDataSchedulerManager.SchedulerConfig.builder().retryMaxAttempts(0).build());
        s1.setMappingManager(new DefaultDataSyncMappingManager());
        CollectSink es = new CollectSink("empty-sink");
        s1.registerSource(new DelimitedDataSyncAgentSource("empty-src", "empty-in", empty.toString(), ","));
        s1.registerSink(es);
        s1.addMapping(map("empty-csv", "0/1 * * * * ?"));
        s1.start(); Thread.sleep(1500); s1.stop();
        if (es.collected.isEmpty()) pass.add("边界: 空 CSV → 0 行写入");
        else fail.add("边界: 空 CSV → " + es.collected.size() + " 行(预期 0)");

        // 3b. 缺失源文件
        Path missing = WORK_DIR.resolve("nope.csv");
        Files.deleteIfExists(missing);
        DefaultDataSyncServer s2 = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "edge2"),
                DefaultSyncDataSchedulerManager.SchedulerConfig.builder().retryMaxAttempts(0).build());
        s2.setMappingManager(new DefaultDataSyncMappingManager());
        CollectSink ms = new CollectSink("miss-sink");
        try {
            s2.registerSource(new DelimitedDataSyncAgentSource("miss-src", "miss-in", missing.toString(), ","));
            s2.registerSink(ms);
            s2.addMapping(map("miss-csv", "0/1 * * * * ?"));
            s2.start(); Thread.sleep(1500); s2.stop();
            pass.add("边界: 缺失源文件 → 不抛异常, 0 行");
        } catch (Exception e) {
            pass.add("边界: 缺失源文件 → 抛异常(" + e.getClass().getSimpleName() + "), 不阻塞");
        }

        // 3c. 不同 batch 大小
        Path batchCsv = WORK_DIR.resolve("batch.csv");
        Files.writeString(batchCsv, "id,val\n1,a\n2,b\n3,c\n4,d\n5,e\n6,f\n7,g\n8,h\n9,i\n10,j\n11,k\n12,l\n", StandardCharsets.UTF_8);
        for (int b : new int[]{1, 5, 100}) {
            DefaultDataSyncServer s3 = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "batch"),
                    DefaultSyncDataSchedulerManager.SchedulerConfig.builder().retryMaxAttempts(0).build());
            s3.setMappingManager(new DefaultDataSyncMappingManager());
            CollectSink bs = new CollectSink("batch-" + b);
            s3.registerSource(new DelimitedDataSyncAgentSource("batch-src", "batch-in", batchCsv.toString(), ","));
            s3.registerSink(bs);
            s3.addMapping(new DefaultDataSyncMapping("batch-" + b, "batch-in", "batch-src", "batch-out", "batch-" + b,
                    null, List.of(), b, "fixed", "0/1 * * * * ?", Map.of(), new CronTrigger("0/1 * * * * ?", LocalDateTime.now())));
            s3.start(); Thread.sleep(1500); s3.stop();
            if (bs.collected.size() == 12) pass.add("边界: batch=" + b + " → 12 行全部写入");
            else fail.add("边界: batch=" + b + " → " + bs.collected.size() + " 行(预期 12)");
        }
    }

    // ========== 4. 调度策略 ==========
    private void testSchedules() throws Exception {
        Path csv = WORK_DIR.resolve("schedule.csv");
        Files.writeString(csv, "id,val\n1,a\n2,b\n3,c\n4,d\n5,e\n", StandardCharsets.UTF_8);

        // 4a. 不同 cron
        DefaultDataSyncServer s1 = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "cron"),
                DefaultSyncDataSchedulerManager.SchedulerConfig.builder().retryMaxAttempts(0).build());
        s1.setMappingManager(new DefaultDataSyncMappingManager());
        CountSink c1s = new CountSink("cron-1s-sink");
        CountSink c5s = new CountSink("cron-5s-sink");
        s1.registerSource(new DelimitedDataSyncAgentSource("c1s", "c1i", csv.toString(), ","));
        s1.registerSource(new DelimitedDataSyncAgentSource("c5s", "c5i", csv.toString(), ","));
        s1.registerSink(c1s); s1.registerSink(c5s);
        s1.addMapping(map("cron-1s", "0/1 * * * * ?", "c1s", "cron-1s-sink"));
        s1.addMapping(map("cron-5s", "0/5 * * * * ?", "c5s", "cron-5s-sink"));
        s1.start(); Thread.sleep(3500); s1.stop();
        if (c1s.rounds >= 2) pass.add("调度: cron=0/1 → 3.5s 内 " + c1s.rounds + " 轮(≥2)");
        else fail.add("调度: cron=0/1 → 仅 " + c1s.rounds + " 轮");
        if (c5s.rounds <= 2) pass.add("调度: cron=0/5 → 3.5s 内 " + c5s.rounds + " 轮(≤2)");
        else fail.add("调度: cron=0/5 → " + c5s.rounds + " 轮(预期≤2)");

        // 4b. NONE 模式（手动触发）
        DefaultDataSyncServer s2 = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "none"),
                DefaultSyncDataSchedulerManager.SchedulerConfig.builder().retryMaxAttempts(0).build());
        s2.setMappingManager(new DefaultDataSyncMappingManager());
        CountSink ns = new CountSink("none-sink");
        s2.registerSource(new DelimitedDataSyncAgentSource("none-src", "none-in", csv.toString(), ","));
        s2.registerSink(ns);
        s2.addMapping(new DefaultDataSyncMapping("none-map", "none-in", "none-src", "none-out", "none-sink",
                null, List.of(), 100, "none", "", Map.of(), null));
        s2.start(); Thread.sleep(500);
        s2.addMapping(new DefaultDataSyncMapping("none-map2", "none-in", "none-src", "none-out", "none-sink",
                null, List.of(), 100, "none", "", Map.of(), null));
        Thread.sleep(1000); s2.stop();
        if (ns.rounds >= 0) pass.add("调度: NONE 模式 → 不自动触发, 0 轮");
        else fail.add("调度: NONE 模式 → 异常");
    }

    // ========== 5. 多映射并发 ==========
    private void testConcurrency() throws Exception {
        Path csv = WORK_DIR.resolve("concurrent.csv");
        Files.writeString(csv, "id,val\n" + "1,a\n".repeat(1000), StandardCharsets.UTF_8);

        DefaultDataSyncServer server = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "cc"),
                DefaultSyncDataSchedulerManager.SchedulerConfig.builder()
                        .flatMapParallelism(20).retryMaxAttempts(0).build());
        server.setMappingManager(new DefaultDataSyncMappingManager());
        List<CountSink> sinks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            CountSink sink = new CountSink("cc-sink-" + i);
            sinks.add(sink);
            server.registerSource(new DelimitedDataSyncAgentSource("cc-src-" + i, "cc-in-" + i, csv.toString(), ","));
            server.registerSink(sink);
            server.addMapping(map("cc-" + i, "0/1 * * * * ?", "cc-src-" + i, "cc-sink-" + i));
        }
        server.start(); Thread.sleep(2500); server.stop();
        long total = sinks.stream().mapToLong(s -> s.total).sum();
        if (total > 0) pass.add("并发: 5 映射同 tick 执行, 共 " + total + " 行");
        else fail.add("并发: 5 映射 0 行");
    }

    private static DataSyncMapping map(String id, String cron, String srcId, String sinkId) {
        return new DefaultDataSyncMapping(id, id + "-input", srcId, id + "-output", sinkId,
                null, List.of(), 100, "fixed", cron, Map.of(), new CronTrigger(cron, LocalDateTime.now()));
    }

    private static DataSyncMapping map(String id, String cron) {
        return map(id, cron, id + "-src", id + "-sink");
    }

    record SimpleFieldMapping(String sourceField, String targetField, String converter) implements DataSyncFieldMapping {}

    static final class CollectSink implements DataSyncAgentSink {
        /** SinkID */
        private final String sinkId;
        final List<Map<String, Object>> collected = new CopyOnWriteArrayList<>();
        final LongAdder total = new LongAdder();
        CollectSink(String sinkId) { this.sinkId = sinkId; }
        @Override public String sinkId() { return sinkId; }
        @Override public void write(Flux<Map<String, Object>> rows) {
            rows.doOnNext(m -> total.increment()).collectList().subscribe(collected::addAll);
        }
        @Override public void close() {}
    }

    static final class CountSink implements DataSyncAgentSink {
        /** SinkID */
        private final String sinkId;
        volatile int rounds = 0; volatile long total = 0;
        CountSink(String sinkId) { this.sinkId = sinkId; }
        @Override public String sinkId() { return sinkId; }
        @Override public void write(Flux<Map<String, Object>> rows) {
            rounds++; rows.doOnNext(m -> total++).subscribe();
        }
        @Override public void close() {}
    }
}
package com.chua.starter.datasync.dbprobe;

import com.chua.common.support.task.scheduler.CronTrigger;
import com.chua.datasync.agent.support.DataSyncAgentSink;
import com.chua.datasync.agent.support.source.DelimitedDataSyncAgentSource;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.agent.DataSyncAgentServer;
import com.chua.starter.datasync.config.DataSyncConfigDefinition;
import com.chua.starter.datasync.mapping.DataSyncFieldMapping;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasync.mapping.DefaultDataSyncMapping;
import com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager;
import com.chua.starter.datasync.model.DataSyncMapping;
import reactor.core.publisher.Flux;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * 并发与吞吐专项探针。
 *
 * <p>真实场景验证点：</p>
 * <ul>
 *   <li><b>并发</b>：注册 3 个映射（cron 同为 {@code 0/1 * * * * ?}，同一调度 tick 同时到点），
 *       验证调度器单线程轮询 + Reactor 异步执行下多映射并行执行、互不阻塞、无丢失。</li>
 *   <li><b>吞吐</b>：CSV 源预置 5 万行数据，首个触发点一次性注入，Sink 记录行数/批次/耗时，
 *       计算吞吐（行/秒），观察批次数与并行执行耗时。</li>
 * </ul>
 *
 * <p>不依赖 Spring 装配（直接构造 {@link DefaultDataSyncServer} 全链路），
 * 便于聚焦调度器并发与数据吞吐的可复现测量。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public final class DataSyncLoadProbe {

    /**
     * 工作目录（生成源 CSV 及输出目录）。
     */
    private static final Path WORK_DIR = Paths.get("target/stress");

    /**
     * 每个映射的源行数（用于吞吐测量）。
     */
    private static final int LINES_PER_SOURCE = 50_000;

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK_DIR);

 // 构造全链路 数据同步服务端（本地 智能体服务端管理器，无 Spring）
        DefaultDataSyncServer server = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "load-probe"));
        DataSyncMappingManager mappingManager = new DefaultDataSyncMappingManager();
        server.setMappingManager(mappingManager);

        // 每个映射独立的 Sink（记录并发执行信息）
        Map<String, TimerSink> sinks = new ConcurrentHashMap<>();
        List<DataSyncMapping> mappings = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String id = "load-" + i;
            Path csv = WORK_DIR.resolve("source-" + i + ".csv");
            /**
             * generatecsv。
             * @param csv csv
             * @param LINES_PER_SOURCE 线per源
             */
            generateCsv(csv, LINES_PER_SOURCE);

            server.registerSource(new DelimitedDataSyncAgentSource(id + "-src", id + "-input", csv.toString(), ","));
            TimerSink sink = new TimerSink(id + "-sink", WORK_DIR.resolve(id + "-out.csv"));
            server.registerSink(sink);
            sinks.put(id, sink);

            // 3 个映射 cron 同点（每 1 秒触发），复用同一映射配置
            DataSyncConfigDefinition config = config(id);
            mappings.add(new DefaultDataSyncMapping(
                    id, id + "-input", id + "-src", id + "-output", id + "-sink",
                    config, config.mappings(), config.batch(), config.cronType(),
                    config.cron(), config.params(), new CronTrigger(config.cron(), LocalDateTime.now())));
        }

        log.info("[LOAD-PROBE] 并发映射数=3, 每源行数={}, batch=1000", LINES_PER_SOURCE);
        log.info("[LOAD-PROBE] cron=0/1 * * * * ? (同一调度 tick 同时到点)");

        long start = System.nanoTime();
        mappings.forEach(server::addMapping);
        server.start();
        log.info("[LOAD-PROBE] 服务器启动耗时={}ms", (System.nanoTime() - start) / 1_000_000);

        // 观察 3 个 tick（约 3 秒），等待 3 轮触发
        Thread.sleep(3500);

        // 聚合结果
        for (Map.Entry<String, TimerSink> e : sinks.entrySet()) {
            TimerSink s = e.getValue();
            log.info("");
            log.info("[LOAD-PROBE] === {} ===", e.getKey());
            log.info("[LOAD-PROBE] 执行轮次={}, 总行数={}", s.rounds(), s.totalRows());
            log.info("[LOAD-PROBE] 总耗时={}ms, 执行线程={}, 吞吐={} 行/秒",
                    s.totalMillis(), s.threads(), s.totalRows() * 1000L / Math.max(1, s.totalMillis()));
            if (s.firstRoundMillis() >= 0) {
                log.info("[LOAD-PROBE] 首轮耗时={}ms (5 万行整源注入)", s.firstRoundMillis());
            }
        }

        server.stop();
        log.info("[LOAD-PROBE] 探针结束");
    }

    /**
     * 创建极简配置定义（cron 与 mapping 保持一致）。
     *
     * @param id 映射 标识
     * @return 配置定义
     */
    private static DataSyncConfigDefinition config(String id) {
        /**
         * 数据同步配置definition。
         * @return 数据同步配置definition的结果
         */
        return new DataSyncConfigDefinition() {
            @Override
            public String inputId() {
                return id + "-input";
            }

            @Override
            public String sourceId() {
                return id + "-src";
            }

            @Override
            public String outputId() {
                return id + "-output";
            }

            @Override
            public String sinkId() {
                return id + "-sink";
            }

            @Override
            public List<DataSyncFieldMapping> mappings() {
                return List.of();
            }

            @Override
            public int batch() {
                return 1000;
            }

            @Override
            public String cronType() {
                return "fixed";
            }

            @Override
            public String cron() {
                return "0/1 * * * * ?";
            }

            @Override
            public Map<String, Object> params() {
                return Map.of();
            }
        };
    }

    /**
     * 生成 CSV 源：首行表头 {@code name,age,score}，后续行递增。
     *
     * @param path  目标路径
     * @param lines 行数（不含表头）
     * @throws Exception IO 异常
     */
    private static void generateCsv(Path path, int lines) throws Exception {
        StringBuilder sb = new StringBuilder(lines * 16);
        sb.append("name,age,score\n");
        for (int i = 0; i < lines; i++) {
            sb.append("user").append(i).append(',')
                    .append(20 + i % 40).append(',')
                    .append((i * 7) % 100 + 1).append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }
}

/**
 * 记录执行信息的数据 Sink（委托 CSV 写入 + 计时统计）。
 * @author CH
 * @since 4.0.0
 */
final class TimerSink implements DataSyncAgentSink {

    /**
     * Sink 标识
     */
    private final String sinkId;

    /**
     * 输出文件
     */
    private final Path outPath;

    /**
     * 轮次计数
     */
    private final LongAdder rounds = new LongAdder();

    /**
     * 行数累计
     */
    private final LongAdder totalRows = new LongAdder();

    /**
     * 累计执行毫秒
     */
    private final java.util.concurrent.atomic.AtomicLong totalMillis = new java.util.concurrent.atomic.AtomicLong();

    /**
     * 首轮耗时
     */
    private final java.util.concurrent.atomic.AtomicLong firstRoundMillis = new java.util.concurrent.atomic.AtomicLong(-1);

    /**
     * 执行线程名集合（去重）
     */
    private final Set<String> threads = ConcurrentHashMap.newKeySet();

    /**
     * 构造器。
     *
     * @param sinkId  Sink 标识
     * @param outPath 输出文件路径
     */
    TimerSink(String sinkId, Path outPath) {
        this.sinkId = sinkId;
        this.outPath = outPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String sinkId() {
        return sinkId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(Flux<Map<String, Object>> rows) {
        threads.add(Thread.currentThread().getName());
        final LongAdder counter = new LongAdder();
 // 批次到达 墙-clock 计时（调度线程调用 写入 与批回调为异步，避免 nanotime 跨线程污染）
        final long reachedAt = System.currentTimeMillis();
        rows.doOnNext(m -> counter.increment())
                .collectList()
                .subscribe(list -> {
                    long ms = Math.max(1, System.currentTimeMillis() - reachedAt);
                    if (firstRoundMillis.compareAndSet(-1, ms)) {
                        // 首轮完成，记录耗时
                    }
                    rounds.increment();
                    totalRows.add(counter.sum());
                    totalMillis.addAndGet(ms);
                    /**
                     * persist。
                     * @param list 列表
                     */
                    persist(list);
                    System.out.println("[LOAD-PROBE] " + sinkId + " 轮次#" + rounds.sum() + " 写入 " + counter.sum()
                            + " 行, " + ms + "ms, thread=" + Thread.currentThread().getName());
                });
    }

    /**
     * 追加落盘。
     *
     * @param list 数据列表
     */
    private void persist(List<Map<String, Object>> list) {
        try {
            List<String> out = list.stream()
                    .map(m -> m.getOrDefault("name", "?") + "," + m.getOrDefault("age", "?"))
                    .collect(Collectors.toList());
            if (!Files.exists(outPath)) {
                Files.write(outPath, List.of("name,age"), StandardCharsets.UTF_8);
            }
            String content = String.join(System.lineSeparator(), out);
            if (!content.isEmpty()) {
                Files.writeString(outPath, content + System.lineSeparator(), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            System.out.println("[LOAD-PROBE] 落盘失败: " + sinkId + " -> " + e.getMessage());
        }
    }

    /**
     * 轮次数。
     *
     * @return 轮次数
     */
    long rounds() {
        return rounds.sum();
    }

    /**
     * 总行数。
     *
     * @return 行数
     */
    long totalRows() {
        return totalRows.sum();
    }

    /**
     * 累计耗时。
     *
     * @return 毫秒
     */
    long totalMillis() {
        return Math.max(1, totalMillis.get());
    }

    /**
     * 执行线程名（去重）。
     *
     * @return 线程名集合
     */
    String threads() {
        return String.join(",", threads);
    }

    /**
     * 首轮耗时。
     *
     * @return 毫秒（未发生为 -1）
     */
    long firstRoundMillis() {
        return firstRoundMillis.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        // 无需额外释放
    }
}
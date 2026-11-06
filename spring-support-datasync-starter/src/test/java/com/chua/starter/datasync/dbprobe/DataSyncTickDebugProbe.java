// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 调度触发时序诊断：打印每次 Sink.写入 的时间戳与线程，定位调度超频根因。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class DataSyncTickDebugProbe {

    /**
     * 工作目录
    */
    private static final Path WORK_DIR = Paths.get("target/tick-debug");

    /**
     * 入口。
     *
     * @param args 命令行参数
     * @throws Exception 异常
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK_DIR);
        Path csv = WORK_DIR.resolve("tick.csv");
        Files.writeString(csv, "id,val\n1,a\n", StandardCharsets.UTF_8);

        DefaultDataSyncServer server = new DefaultDataSyncServer(new DataSyncAgentServer(null, null, "tick"),
                DefaultSyncDataSchedulerManager.SchedulerConfig.builder().retryMaxAttempts(0).build());
        server.setMappingManager(new DefaultDataSyncMappingManager());
        server.registerSource(new DelimitedDataSyncAgentSource("tick-src", "tick-in", csv.toString(), ","));
        CopyOnWriteArrayList<String> hits = new CopyOnWriteArrayList<>();
        server.registerSink(new DataSyncAgentSink() {
            @Override public String sinkId() { return "tick-sink"; }
            @Override public void write(Flux<Map<String, Object>> rows) {
                String ts = java.time.LocalTime.now().toString().substring(0, 12);
                hits.add(ts + " " + Thread.currentThread().getName());
                log.info("[WRITE] {} thread={}", ts, Thread.currentThread().getName());
            }
            @Override public void close() { }
        });
        DataSyncMapping mapping = new DefaultDataSyncMapping("tick-mapping", "tick-in", "tick-src",
                "tick-out", "tick-sink", null, List.of(), 100, "fixed", "0/1 * * * * ?",
                Map.of(), new CronTrigger("0/1 * * * * ?", LocalDateTime.now()));
        server.addMapping(mapping);

        long t0 = System.currentTimeMillis();
        server.start();
        Thread.sleep(5000);
        server.stop();
        log.info("[SUMMARY] 5s 窗口 write 次数={}（cron=0/1 理论 5~6 次）", hits.size());
    }
}
package com.chua.starter.job.support.log;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 文件版 {@link JobLogSink}，每个任务执行写入独立日志文件。
 *
 * <p>文件路径: {@code {basePath}/{yyyy-MM-dd}/{jobName}/{executedAtMs}.log}</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class FileJobLogSink implements JobLogSink {

    /** 日期_fmt */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    /** 基础路径 */
    private final Path basePath;

    /**
     * 构造文件日志落库。
     *
     * @param basePath 日志根目录
     */
    public FileJobLogSink(String basePath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            log.warn("[job] 创建日志根目录失败 path={} cause={}", this.basePath, e.getMessage());
        }
        log.info("[job] FileJobLogSink 初始化完成 basePath={}", this.basePath);
    }

    @Override
    /** 写入 */
    public void write(JobLogEvent event) {
        if (event == null) {
            return;
        }
        String dateDir = DATE_FMT.format(Instant.now());
        String jobName = sanitize(event.jobName() != null ? event.jobName() : "unknown");
        String fileName = event.executedAtMs() + ".log";

        Path dir = basePath.resolve(dateDir).resolve(jobName);
        Path file = dir.resolve(fileName);

        try {
            Files.createDirectories(dir);
            String content = formatLog(event);
            Files.writeString(file, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            log.warn("[job] 写入日志文件失败 path={} cause={}", file, e.getMessage());
        }
    }

    /**
     * 获取文件路径 (用于写入 DB)。
     *
     * @param event 事件
     * @return 相对路径字符串
     */
    public String resolveRelativePath(JobLogEvent event) {
        String dateDir = DATE_FMT.format(Instant.now());
        String jobName = sanitize(event.jobName() != null ? event.jobName() : "unknown");
        return dateDir + "/" + jobName + "/" + event.executedAtMs() + ".log";
    }

    /**
     * 格式化记录日志
     *
     * @param event 事件
     * @return 格式化日志的结果
     */
    private String formatLog(JobLogEvent event) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("=== JOB EXECUTION LOG ===\n");
        sb.append("Job Name    : ").append(event.jobName()).append('\n');
        sb.append("Status      : ").append(event.status()).append('\n');
        sb.append("Cost (ms)   : ").append(event.costMs()).append('\n');
        sb.append("Node ID     : ").append(event.nodeId()).append('\n');
        sb.append("Executed At : ").append(event.executedAtMs()).append('\n');
        if (event.exception() != null) {
            sb.append("Exception   : ").append(event.exception()).append('\n');
        }
        sb.append("--- Log Content ---\n");
        if (event.logContent() != null && !event.logContent().isEmpty()) {
            sb.append(event.logContent());
        } else {
            sb.append("(no log content)\n");
        }
        sb.append("=== END ===\n");
        return sb.toString();
    }

    /**
     * Sanitize
     *
     * @param name 名称
     * @return sanitize的结果
     */
    private static String sanitize(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
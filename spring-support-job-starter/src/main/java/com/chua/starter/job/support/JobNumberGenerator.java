package com.chua.starter.job.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Job 编号生成器。
 * <p>
 * 统一生成任务编号和日志编号，避免业务层长期依赖数据库自增 ID。
 * 目前采用时间戳 + 进程内序列号，满足单机唯一和可读性要求。
 * </p>
 */
public final class JobNumberGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicInteger SEQUENCE = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000, 9999));

    private JobNumberGenerator() {
    }

    /**
     * 生成任务编号。
     */
    public static String nextJobNo() {
        return next("JOB");
    }

    /**
     * 生成日志编号。
     */
    public static String nextJobLogNo() {
        return next("JOBLOG");
    }

    private static String next(String prefix) {
        int suffix = Math.floorMod(SEQUENCE.incrementAndGet(), 10_000);
        return prefix + FORMATTER.format(LocalDateTime.now()) + String.format("%04d", suffix);
    }
}

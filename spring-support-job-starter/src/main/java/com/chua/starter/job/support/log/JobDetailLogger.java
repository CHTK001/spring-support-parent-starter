package com.chua.starter.job.support.log;

import com.chua.common.support.lang.json.Json;
import com.chua.starter.job.support.thread.JobContext;

/**
 * 任务详细日志静态门面 (骨架版)。
 * <p>
 * 当前保留 API 形状以便未来重构接回结构化日志写入,
 * 但所有方法退化为 no-op,不再依赖已删除的 JobLogDetailService。
 * </p>
 */
public final class JobDetailLogger {

    private JobDetailLogger() {
    }

    public static boolean info(String content) {
        return false;
    }

    public static boolean info(String content, String phase, Integer progress) {
        return false;
    }

    public static boolean warn(String content) {
        return false;
    }

    public static boolean error(String content) {
        return false;
    }

    public static boolean error(String content, Throwable throwable) {
        if (JobContext.getJobContext() == null) {
            return false;
        }
        return true;
    }

    public static boolean detail(String level, String content, String phase, Integer progress) {
        if (JobContext.getJobContext() == null) {
            return false;
        }
        return true;
    }

    public static boolean detailObject(String level, String title, Object value, String phase, Integer progress) {
        @SuppressWarnings("unused")
        String payload;
        try {
            payload = value == null ? "null" : Json.toJson(value);
        } catch (Exception ignored) {
            payload = String.valueOf(value);
        }
        if (JobContext.getJobContext() == null) {
            return false;
        }
        return true;
    }
}

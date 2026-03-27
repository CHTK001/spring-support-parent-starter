package com.chua.starter.job.support.log;

import com.chua.common.support.text.json.Json;
import com.chua.starter.job.support.scheduler.JobConfig;
import com.chua.starter.job.support.thread.JobContext;

/**
 * 任务详细日志静态门面。
 * <p>
 * 业务 Job 可以直接调用该类向任务日志详情表和任务日志文件同时写入结构化明细。
 * </p>
 */
public final class JobDetailLogger {

    private JobDetailLogger() {
    }

    public static boolean info(String content) {
        return detail("INFO", content, null, null);
    }

    public static boolean info(String content, String phase, Integer progress) {
        return detail("INFO", content, phase, progress);
    }

    public static boolean warn(String content) {
        return detail("WARN", content, null, null);
    }

    public static boolean error(String content) {
        return detail("ERROR", content, null, null);
    }

    public static boolean error(String content, Throwable throwable) {
        JobLogDetailService service = JobConfig.getInstance().jobLogDetailService();
        if (service == null || JobContext.getJobContext() == null) {
            return false;
        }
        service.error(JobContext.getJobContext(), content, throwable);
        return true;
    }

    public static boolean detail(String level, String content, String phase, Integer progress) {
        JobLogDetailService service = JobConfig.getInstance().jobLogDetailService();
        if (service == null || JobContext.getJobContext() == null) {
            return false;
        }
        service.log(JobContext.getJobContext(), level, content, phase, progress);
        return true;
    }

    public static boolean detailObject(String level, String title, Object value, String phase, Integer progress) {
        String payload;
        try {
            payload = value == null ? "null" : Json.toJson(value);
        } catch (Exception ignored) {
            payload = String.valueOf(value);
        }
        return detail(level, title + ": " + payload, phase, progress);
    }
}

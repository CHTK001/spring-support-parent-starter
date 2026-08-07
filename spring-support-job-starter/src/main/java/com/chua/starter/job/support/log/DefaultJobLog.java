package com.chua.starter.job.support.log;

import cn.hutool.core.date.DateUtil;
import com.chua.starter.job.support.thread.JobContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 默认任务日志实现 (骨架版)。
 * <p>
 * 仅写入文件/控制台,不再依赖已删除的 JobConfig / JobLogDetailService。
 * 后续重构可重新接入结构化日志。
 * </p>
 */
@Slf4j
public class DefaultJobLog implements JobLog {

    public static boolean log(String appendLogPattern) {
        StackTraceElement callInfo = new Throwable().getStackTrace()[1];
        return logDetail(callInfo, appendLogPattern);
    }

    private static boolean logDetail(StackTraceElement callInfo, String appendLog) {
        JobContext jobContext = JobContext.getJobContext();
        if (jobContext == null) {
            return false;
        }

        String formatAppendLog = DateUtil.formatDateTime(new Date()) + " " +
                "[" + callInfo.getClassName() + "#" + callInfo.getMethodName() + "]" + "-" +
                "[" + callInfo.getLineNumber() + "]" + "-" +
                "[" + Thread.currentThread().getName() + "]" + " " +
                (appendLog != null ? appendLog : "");

        String logFileName = jobContext.getJobLogFileName();
        if (logFileName != null && !logFileName.trim().isEmpty()) {
            JobFileAppender.appendLog(logFileName, formatAppendLog);
            return true;
        }
        log.info(">>>>>>>>>>> {}", formatAppendLog);
        return false;
    }
}

package com.chua.starter.job.support.log;

import cn.hutool.core.date.DateUtil;
import com.chua.starter.job.support.thread.JobContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 默认任务日志实现
 * <p>
 * 提供任务执行过程中的日志记录功能。
 * 日志会同时写入到文件和控制台（当无文件时）。
 * </p>
 *
 * <h3>日志格式</h3>
 * <pre>
 * 2024-03-11 10:30:00 [类名#方法名]-[行号]-[线程名] 日志内容
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 在任务执行中记录日志
 * DefaultJobLog.log("开始执行任务...");
 * DefaultJobLog.log("处理数据: " + count + " 条");
 * }</pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see JobFileAppender
 * @see JobLog
 */
@Slf4j
public class DefaultJobLog implements JobLog {

    /**
     * 记录日志
     *
     * @param appendLogPattern 日志内容
     * @return 是否成功
     */
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

        // 追加日志
        String logFileName = jobContext.getJobLogFileName();

        if (logFileName != null && !logFileName.trim().isEmpty()) {
            JobFileAppender.appendLog(logFileName, formatAppendLog);
            return true;
        } else {
            log.info(">>>>>>>>>>> {}", formatAppendLog);
            return false;
        }
    }
}

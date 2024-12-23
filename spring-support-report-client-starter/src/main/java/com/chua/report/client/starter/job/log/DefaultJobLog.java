package com.chua.report.client.starter.job.log;

import cn.hutool.core.date.DateUtil;
import com.chua.report.client.starter.job.thread.JobContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 作业日志
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Slf4j
public class DefaultJobLog implements JobLog{


    public static boolean log(String appendLogPattern) {
        StackTraceElement callInfo = new Throwable().getStackTrace()[1];
        return logDetail(callInfo, appendLogPattern);
    }
    private static boolean logDetail(StackTraceElement callInfo, String appendLog) {
        JobContext xxlJobContext = JobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return false;
        }

        /*// "yyyy-MM-dd HH:mm:ss [ClassName]-[MethodName]-[LineNumber]-[ThreadName] log";
        StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();
        StackTraceElement callInfo = stackTraceElements[1];*/

        String formatAppendLog = DateUtil.formatDateTime(new Date()) + " " +
                "[" + callInfo.getClassName() + "#" + callInfo.getMethodName() + "]" + "-" +
                "[" + callInfo.getLineNumber() + "]" + "-" +
                "[" + Thread.currentThread().getName() + "]" + " " +
                (appendLog != null ? appendLog : "");

        // appendlog
        String logFileName = xxlJobContext.getJobLogFileName();

        if (logFileName!=null && logFileName.trim().length()>0) {
            JobFileAppender.appendLog(logFileName, formatAppendLog);
            return true;
        } else {
            log.info(">>>>>>>>>>> {}", formatAppendLog);
            return false;
        }
    }
    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public void error(String message, Throwable e) {

    }

    @Override
    public void error(String message, Object... args) {

    }

    @Override
    public void debug(String message, Object... args) {

    }

    @Override
    public void trace(String message, Object... args) {

    }

    @Override
    public void warn(String message, Object... args) {

    }

    @Override
    public void info(String message, Object... args) {

    }
}

package com.chua.report.client.starter.job.log;

import cn.hutool.core.date.DateUtil;
import com.chua.report.client.starter.job.thread.JobContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 作业日志
 *
 * @since 2024/03/11
 * @author CH
 */
@Slf4j
public class DefaultJobLog implements JobLog{



    /**
     * 记录日志
     *
     * @param appendLogPattern 追加日志模式
     * @return 日志的结果
     */
    public static boolean log(String appendLogPattern) {
        StackTraceElement callInfo = new Throwable().getStackTrace()[1];
        return logDetail(callInfo, appendLogPattern);
    }
    /**
     * 记录日志Detail
     *
     * @param callInfo call信息
     * @param appendLog 追加日志
     * @return 日志detail的结果
     */
    private static boolean logDetail(StackTraceElement callInfo, String appendLog) {
        JobContext xxlJobContext = JobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return false;
        }

        /* // "yyyy-MM-dd HH:mm:ss [类名称]-[方法名称]-[线数字]-[thread名称] 日志";
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
}

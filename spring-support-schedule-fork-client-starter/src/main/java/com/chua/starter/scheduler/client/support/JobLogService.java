package com.chua.starter.scheduler.client.support;

import com.chua.common.support.log.Log;
import com.xxl.job.core.context.XxlJobHelper;

/**
 * 日志服务
 * @author CH
 */
public class JobLogService implements Log {


    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public void error(String message, Throwable e) {
        XxlJobHelper.log(e);
    }

    @Override
    public void error(String message, Object... args) {
        XxlJobHelper.log("[ERROR]" + message, args);
    }

    @Override
    public void debug(String message, Object... args) {
        XxlJobHelper.log("[DEBUG]" + message, args);
    }

    @Override
    public void trace(String message, Object... args) {
        XxlJobHelper.log("[TRACE]" + message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        XxlJobHelper.log("[WARN ]" + message, args);
    }

    @Override
    public void info(String message, Object... args) {
        XxlJobHelper.log("[INFO ]" + message, args);
    }
}

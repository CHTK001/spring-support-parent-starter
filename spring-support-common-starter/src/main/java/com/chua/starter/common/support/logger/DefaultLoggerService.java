package com.chua.starter.common.support.logger;

import com.chua.common.support.log.Log;

/**
 * 日志服务
 *
 * @author CH
 */
public class DefaultLoggerService implements LoggerService {

    static final DefaultLoggerService INSTANCE = new DefaultLoggerService();
    private static final Log log = Log.getLogger(LoggerService.class);

    public static DefaultLoggerService getInstance() {
        return INSTANCE;
    }

    @Override
    public void save(SysLog sysLog) {
    }
}

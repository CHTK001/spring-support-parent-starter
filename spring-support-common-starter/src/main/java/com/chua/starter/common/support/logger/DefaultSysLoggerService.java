package com.chua.starter.common.support.logger;

import com.chua.common.support.log.Log;

/**
 * 日志服务
 *
 * @author CH
 */
public class DefaultSysLoggerService implements SysLoggerService {

    static final DefaultSysLoggerService INSTANCE = new DefaultSysLoggerService();
    private static final Log log = Log.getLogger(SysLoggerService.class);

    public static DefaultSysLoggerService getInstance() {
        return INSTANCE;
    }

    @Override
    public void save(SysLoggerInfo sysLog) {
    }
}

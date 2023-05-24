package com.chua.starter.common.support.logger;

/**
 * 日志服务
 *
 * @author CH
 */
public interface LoggerService {
    /**
     * 保存日志
     *
     * @param sysLog 日志对象
     */
    void save(SysLog sysLog);
}

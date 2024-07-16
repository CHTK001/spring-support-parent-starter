package com.chua.starter.monitor.server.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.server.entity.MonitorSysGen;

import java.util.Date;

/**
 * 数据库备份
 * @author CH
 * @since 2024/7/9
 */
public interface MonitorGenBackupService {
    /**
     * 开启备份
     *
     * @param monitorSysGen monitorSysGen
     * @return ReturnResult<Boolean>
     */
    ReturnResult<Boolean> start(MonitorSysGen monitorSysGen);

    /**
     * 关闭备份
     *
     * @param monitorSysGen monitorSysGen
     * @return ReturnResult<Boolean>
     */
    ReturnResult<Boolean> stop(MonitorSysGen monitorSysGen);

    /**
     * 下载备份
     *
     * @param genId    genId
     * @param startDay 开始时间
     * @param endDay   结束时间
     * @return byte[]
     */
    byte[] downloadBackup(Integer genId, Date startDay, Date endDay);
}

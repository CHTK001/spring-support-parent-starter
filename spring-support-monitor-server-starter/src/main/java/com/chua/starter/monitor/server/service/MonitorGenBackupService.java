package com.chua.starter.monitor.server.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.redis.support.search.SearchResultItem;
import com.chua.starter.monitor.server.entity.MonitorSysGen;
import com.chua.starter.monitor.server.query.LogTimeQuery;

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

    /**
     * 根据指定的时间查询条件和监控系统配置，查询日志信息。
     *
     * 本函数旨在通过提供的具体时间范围和监控系统配置，检索符合要求的日志数据。
     * 它返回一个包含搜索结果的容器，每个结果项都是一个 SearchResultItem 对象。
     *
     * @param timeQuery 包含查询时间范围的实体类，用于指定要查询的日志的时间段。
     * @param monitorSysGen 监控系统配置实体类，包含与监控系统相关的配置信息，用于指导查询。
     * @return 返回一个封装了查询结果的 ReturnResult 对象，其中包含一个 SearchResultItem 的列表。
     */
    ReturnResult<SearchResultItem> queryForLog(LogTimeQuery timeQuery, MonitorSysGen monitorSysGen);
}

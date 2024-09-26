package com.chua.report.server.starter.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.redis.support.search.SearchResultItem;
import com.chua.report.server.starter.entity.MonitorSysGen;
import com.chua.report.server.starter.query.LogTimeQuery;

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

    /**
     * 尝试升级系统生成配置。
     *
     * 本方法用于接收一个新的系统生成配置对象，并尝试将其应用为当前系统的配置。升级过程可能涉及读取、验证新配置，
     * 并与旧配置进行对比，确定是否需要以及如何进行升级。升级结果将通过返回值进行表示。
     *
     * @param newSysGen 新的系统生成配置对象，包含了拟应用的配置信息。
     * @return 返回一个包含升级结果的ReturnResult对象。其中，Boolean值表示升级是否成功，
     *         可能附带额外的说明信息或错误详情。
     */
    ReturnResult<Boolean> upgrade(MonitorSysGen newSysGen);
}

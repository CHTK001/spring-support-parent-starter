package com.chua.starter.sync.data.support.service;

import com.chua.starter.sync.data.support.entity.MonitorSyncStatistics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 同步统计服务接口
 *
 * @author System
 * @since 2026/03/09
 */
public interface SyncStatisticsService {

    /**
     * 生成日统计数据
     *
     * @param taskId 任务ID
     * @param date 统计日期
     * @return 统计数据
     */
    MonitorSyncStatistics generateDailyStatistics(Long taskId, LocalDate date);

    /**
     * 查询统计数据
     *
     * @param taskId 任务ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 统计数据列表
     */
    List<MonitorSyncStatistics> queryStatistics(Long taskId, LocalDate startDate, LocalDate endDate);

    /**
     * 分析统计趋势
     *
     * @param taskId 任务ID
     * @param days 天数
     * @return 趋势数据
     */
    Map<String, Object> analyzeTrend(Long taskId, int days);
}

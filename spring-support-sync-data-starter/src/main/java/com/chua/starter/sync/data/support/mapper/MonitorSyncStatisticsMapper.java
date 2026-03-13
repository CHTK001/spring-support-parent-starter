package com.chua.starter.sync.data.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.sync.data.support.entity.MonitorSyncStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 同步统计 Mapper 接口
 *
 * @author System
 * @since 2026/03/09
 */
@Mapper
public interface MonitorSyncStatisticsMapper extends BaseMapper<MonitorSyncStatistics> {

    /**
     * 查询指定任务的统计数据（按日期范围）
     *
     * @param syncTaskId 任务ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 统计数据列表
     */
    @Select("SELECT * FROM monitor_sync_statistics WHERE sync_task_id = #{syncTaskId} " +
            "AND stat_date BETWEEN #{startDate} AND #{endDate} ORDER BY stat_date DESC")
    List<MonitorSyncStatistics> selectByTaskIdAndDateRange(
            @Param("syncTaskId") Long syncTaskId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}

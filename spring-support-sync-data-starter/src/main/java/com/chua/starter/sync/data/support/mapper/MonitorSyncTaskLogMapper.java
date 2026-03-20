package com.chua.starter.sync.data.support.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.sync.data.support.entity.MonitorSyncTaskLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 同步任务日志 Mapper 接口
 *
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorSyncTaskLogMapper extends BaseMapper<MonitorSyncTaskLog> {

    String[] COMPATIBLE_SELECT_COLUMNS = {
            "sync_log_id",
            "sync_task_id",
            "sync_log_status",
            "sync_log_trigger_type",
            "sync_log_read_count",
            "sync_log_write_count",
            "sync_log_success_count",
            "sync_log_fail_count",
            "sync_log_retry_count",
            "sync_log_dead_letter_count",
            "sync_log_filter_count",
            "sync_log_start_time",
            "sync_log_end_time",
            "sync_log_cost",
            "sync_log_avg_process_time",
            "sync_log_throughput",
            "sync_log_message",
            "sync_log_stack_trace"
    };

    default QueryWrapper<MonitorSyncTaskLog> compatibleQuery() {
        return Wrappers.<MonitorSyncTaskLog>query().select(COMPATIBLE_SELECT_COLUMNS);
    }

    default MonitorSyncTaskLog selectCompatibleById(@Param("logId") Long logId) {
        if (logId == null) {
            return null;
        }
        return selectOne(compatibleQuery().eq("sync_log_id", logId).last("LIMIT 1"));
    }

    default List<MonitorSyncTaskLog> selectCompatibleList(QueryWrapper<MonitorSyncTaskLog> wrapper) {
        QueryWrapper<MonitorSyncTaskLog> queryWrapper = wrapper == null ? Wrappers.query() : wrapper;
        queryWrapper.select(COMPATIBLE_SELECT_COLUMNS);
        return selectList(queryWrapper);
    }

    default Page<MonitorSyncTaskLog> selectCompatiblePage(Page<MonitorSyncTaskLog> page, QueryWrapper<MonitorSyncTaskLog> wrapper) {
        QueryWrapper<MonitorSyncTaskLog> queryWrapper = wrapper == null ? Wrappers.query() : wrapper;
        queryWrapper.select(COMPATIBLE_SELECT_COLUMNS);
        return selectPage(page, queryWrapper);
    }

    /**
     * 根据任务ID查询日志列表
     *
     * @param taskId 任务ID
     * @return 日志列表
     */
    default List<MonitorSyncTaskLog> selectByTaskId(@Param("taskId") Long taskId) {
        return selectCompatibleList(compatibleQuery()
                .eq("sync_task_id", taskId)
                .orderByDesc("sync_log_start_time"));
    }

    /**
     * 根据任务ID删除所有日志
     *
     * @param taskId 任务ID
     * @return 删除数量
     */
    default int deleteByTaskId(@Param("taskId") Long taskId) {
        return delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MonitorSyncTaskLog>()
                .eq(MonitorSyncTaskLog::getSyncTaskId, taskId));
    }

    /**
     * 查询任务最新一条日志
     *
     * @param taskId 任务ID
     * @return 最新日志
     */
    default MonitorSyncTaskLog selectLatestByTaskId(@Param("taskId") Long taskId) {
        return selectOne(compatibleQuery()
                .eq("sync_task_id", taskId)
                .orderByDesc("sync_log_start_time")
                .last("LIMIT 1"));
    }
}

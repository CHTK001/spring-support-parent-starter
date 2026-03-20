package com.chua.starter.sync.data.support.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.sync.data.support.entity.MonitorSyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 同步任务 Mapper 接口
 *
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorSyncTaskMapper extends BaseMapper<MonitorSyncTask> {

    String[] COMPATIBLE_SELECT_COLUMNS = {
            "sync_task_id",
            "sync_task_name",
            "sync_task_desc",
            "sync_task_status",
            "sync_task_batch_size",
            "sync_task_consume_timeout",
            "sync_task_retry_count",
            "sync_task_retry_interval",
            "sync_task_sync_interval",
            "sync_task_ack_enabled",
            "sync_task_transaction_enabled",
            "sync_task_cron",
            "sync_task_layout",
            "sync_task_last_run_time",
            "sync_task_last_run_status",
            "sync_task_run_count",
            "sync_task_success_count",
            "sync_task_fail_count",
            "sync_task_create_time",
            "sync_task_update_time",
            "sync_task_transform_config",
            "sync_task_filter_config",
            "sync_task_sync_mode",
            "sync_task_incremental_field",
            "sync_task_conflict_strategy",
            "sync_task_max_memory_mb",
            "sync_task_thread_pool_size"
    };

    String COMPATIBLE_SELECT_SQL = "sync_task_id, sync_task_name, sync_task_desc, sync_task_status, "
            + "sync_task_batch_size, sync_task_consume_timeout, sync_task_retry_count, sync_task_retry_interval, "
            + "sync_task_sync_interval, sync_task_ack_enabled, sync_task_transaction_enabled, sync_task_cron, "
            + "sync_task_layout, sync_task_last_run_time, sync_task_last_run_status, sync_task_run_count, "
            + "sync_task_success_count, sync_task_fail_count, sync_task_create_time, sync_task_update_time, "
            + "sync_task_transform_config, sync_task_filter_config, sync_task_sync_mode, "
            + "sync_task_incremental_field, sync_task_conflict_strategy, sync_task_max_memory_mb, "
            + "sync_task_thread_pool_size";

    default QueryWrapper<MonitorSyncTask> compatibleQuery() {
        return Wrappers.<MonitorSyncTask>query().select(COMPATIBLE_SELECT_COLUMNS);
    }

    default MonitorSyncTask selectCompatibleById(@Param("taskId") Long taskId) {
        if (taskId == null) {
            return null;
        }
        return selectOne(compatibleQuery().eq("sync_task_id", taskId).last("LIMIT 1"));
    }

    default List<MonitorSyncTask> selectCompatibleBatchIds(Collection<?> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(compatibleQuery().in("sync_task_id", taskIds));
    }

    default List<MonitorSyncTask> selectCompatibleList(QueryWrapper<MonitorSyncTask> wrapper) {
        QueryWrapper<MonitorSyncTask> queryWrapper = wrapper == null ? Wrappers.query() : wrapper;
        queryWrapper.select(COMPATIBLE_SELECT_COLUMNS);
        return selectList(queryWrapper);
    }

    default Page<MonitorSyncTask> selectCompatiblePage(Page<MonitorSyncTask> page, QueryWrapper<MonitorSyncTask> wrapper) {
        QueryWrapper<MonitorSyncTask> queryWrapper = wrapper == null ? Wrappers.query() : wrapper;
        queryWrapper.select(COMPATIBLE_SELECT_COLUMNS);
        return selectPage(page, queryWrapper);
    }

    /**
     * 查询运行中的任务（用于应用启动时恢复）
     *
     * @return 运行中的任务列表
     */
    @Select("SELECT " + COMPATIBLE_SELECT_SQL + " FROM monitor_sync_task WHERE sync_task_status = 'RUNNING'")
    List<MonitorSyncTask> selectRunningTasks();
}

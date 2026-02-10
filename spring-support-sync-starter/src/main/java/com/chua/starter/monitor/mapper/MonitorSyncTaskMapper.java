package com.chua.starter.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.entity.MonitorSyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 同步任务 Mapper 接口
 *
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorSyncTaskMapper extends BaseMapper<MonitorSyncTask> {

    /**
     * 查询运行中的任务（用于应用启动时恢复）
     *
     * @return 运行中的任务列表
     */
    @Select("SELECT * FROM monitor_sync_task WHERE sync_task_status = 'RUNNING' AND sync_task_enabled = 1")
    List<MonitorSyncTask> selectRunningTasks();
}

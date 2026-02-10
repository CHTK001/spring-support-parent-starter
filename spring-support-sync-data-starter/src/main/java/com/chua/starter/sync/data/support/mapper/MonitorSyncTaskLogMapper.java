package com.chua.starter.sync.data.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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

    /**
     * 根据任务ID查询日志列表
     *
     * @param taskId 任务ID
     * @return 日志列表
     */
    default List<MonitorSyncTaskLog> selectByTaskId(@Param("taskId") Long taskId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MonitorSyncTaskLog>()
                .eq(MonitorSyncTaskLog::getSyncTaskId, taskId)
                .orderByDesc(MonitorSyncTaskLog::getSyncLogStartTime));
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
}

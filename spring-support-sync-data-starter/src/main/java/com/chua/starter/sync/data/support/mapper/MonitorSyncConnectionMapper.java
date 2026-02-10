package com.chua.starter.sync.data.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.sync.data.support.entity.MonitorSyncConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 同步连线 Mapper 接口
 *
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorSyncConnectionMapper extends BaseMapper<MonitorSyncConnection> {

    /**
     * 根据任务ID查询所有连线
     *
     * @param taskId 任务ID
     * @return 连线列表
     */
    default List<MonitorSyncConnection> selectByTaskId(@Param("taskId") Long taskId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MonitorSyncConnection>()
                .eq(MonitorSyncConnection::getSyncTaskId, taskId));
    }

    /**
     * 根据任务ID删除所有连线
     *
     * @param taskId 任务ID
     * @return 删除数量
     */
    default int deleteByTaskId(@Param("taskId") Long taskId) {
        return delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MonitorSyncConnection>()
                .eq(MonitorSyncConnection::getSyncTaskId, taskId));
    }
}

package com.chua.starter.sync.data.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

    String[] COMPATIBLE_SELECT_COLUMNS = {
            "sync_connection_id",
            "sync_task_id",
            "source_node_id",
            "source_node_key",
            "source_handle",
            "target_node_id",
            "target_node_key",
            "target_handle",
            "connection_type",
            "connection_label",
            "sync_connection_create_time"
    };

    /**
     * 根据任务ID查询所有连线
     *
     * @param taskId 任务ID
     * @return 连线列表
     */
    default List<MonitorSyncConnection> selectByTaskId(@Param("taskId") Long taskId) {
        return selectList(Wrappers.<MonitorSyncConnection>query()
                .select(COMPATIBLE_SELECT_COLUMNS)
                .eq("sync_task_id", taskId));
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

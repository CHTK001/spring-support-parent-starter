package com.chua.starter.sync.data.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.sync.data.support.entity.MonitorSyncNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 同步节点 Mapper 接口
 *
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorSyncNodeMapper extends BaseMapper<MonitorSyncNode> {

    String[] COMPATIBLE_SELECT_COLUMNS = {
            "sync_node_id",
            "sync_task_id",
            "sync_node_type",
            "sync_node_spi_name",
            "sync_node_name",
            "sync_node_key",
            "sync_node_config",
            "sync_node_position",
            "sync_node_order",
            "sync_node_enabled",
            "sync_node_desc",
            "sync_node_create_time",
            "sync_node_update_time"
    };

    /**
     * 根据任务ID查询所有节点
     *
     * @param taskId 任务ID
     * @return 节点列表
     */
    default List<MonitorSyncNode> selectByTaskId(@Param("taskId") Long taskId) {
        return selectList(Wrappers.<MonitorSyncNode>query()
                .select(COMPATIBLE_SELECT_COLUMNS)
                .eq("sync_task_id", taskId)
                .orderByAsc("sync_node_order"));
    }

    /**
     * 根据任务ID删除所有节点
     *
     * @param taskId 任务ID
     * @return 删除数量
     */
    default int deleteByTaskId(@Param("taskId") Long taskId) {
        return delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MonitorSyncNode>()
                .eq(MonitorSyncNode::getSyncTaskId, taskId));
    }
}

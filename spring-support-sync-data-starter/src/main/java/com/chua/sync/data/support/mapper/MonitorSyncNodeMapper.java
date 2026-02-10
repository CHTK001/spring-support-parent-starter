package com.chua.sync.data.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.sync.data.support.entity.MonitorSyncNode;
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

    /**
     * 根据任务ID查询所有节点
     *
     * @param taskId 任务ID
     * @return 节点列表
     */
    default List<MonitorSyncNode> selectByTaskId(@Param("taskId") Long taskId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MonitorSyncNode>()
                .eq(MonitorSyncNode::getSyncTaskId, taskId)
                .orderByAsc(MonitorSyncNode::getSyncNodeOrder));
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

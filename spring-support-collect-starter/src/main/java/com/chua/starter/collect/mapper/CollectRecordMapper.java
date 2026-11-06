package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 结果记录 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectRecordMapper extends BaseMapper<CollectRecord> {

    /**
     * 按业务主键定位已有记录，用于同键覆盖写入。
     *
     * @param flowId 流程ID
     * @param nodeId 节点ID
     * @param key    业务主键值
     * @return 记录主键，不存在返回 null
     */
    Long selectIdByKey(@Param("flowId") Integer flowId,
                       @Param("nodeId") String nodeId,
                       @Param("key") String key);

    /**
     * 本流程内该主键已存在的记录数。
     *
     * @param flowId 流程ID
     * @param key    业务主键值
     * @return 记录数
     */
    int countByFlowKey(@Param("flowId") Integer flowId, @Param("key") String key);

    /**
     * 跨流程该主键已存在的记录数。
     *
     * @param key 业务主键值
     * @return 记录数
     */
    int countByKey(@Param("key") String key);
}

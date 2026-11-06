package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectFlowVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程版本 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectFlowVersionMapper extends BaseMapper<CollectFlowVersion> {

    /**
     * 查询某流程的最新版本号。
     *
     * @param flowId 流程ID
     * @return 最新版本号，没有时返回 0
     */
    int maxVersionNo(@Param("flowId") Integer flowId);
}

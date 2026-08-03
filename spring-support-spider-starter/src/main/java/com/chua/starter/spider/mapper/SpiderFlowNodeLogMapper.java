package com.chua.starter.spider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.spider.entity.SpiderFlowNodeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程节点执行日志 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SpiderFlowNodeLogMapper extends BaseMapper<SpiderFlowNodeLog> {
}

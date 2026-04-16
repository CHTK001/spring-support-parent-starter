package com.chua.starter.spider.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.spider.support.domain.SpiderNodeExecutionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 节点执行日志 Mapper
 */
@Mapper
public interface SpiderNodeExecutionLogMapper extends BaseMapper<SpiderNodeExecutionLog> {
}

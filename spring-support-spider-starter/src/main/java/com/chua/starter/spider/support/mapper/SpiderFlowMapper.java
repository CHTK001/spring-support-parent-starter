package com.chua.starter.spider.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 爬虫编排 Mapper
 */
@Mapper
public interface SpiderFlowMapper extends BaseMapper<SpiderFlowDefinition> {
}

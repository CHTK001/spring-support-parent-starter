package com.chua.starter.spider.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 爬虫任务 Mapper
 */
@Mapper
public interface SpiderTaskMapper extends BaseMapper<SpiderTaskDefinition> {
}

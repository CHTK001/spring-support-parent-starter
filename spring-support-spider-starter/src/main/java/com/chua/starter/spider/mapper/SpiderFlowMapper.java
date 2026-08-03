package com.chua.starter.spider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.spider.entity.SpiderFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 爬虫流程定义 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SpiderFlowMapper extends BaseMapper<SpiderFlow> {
}

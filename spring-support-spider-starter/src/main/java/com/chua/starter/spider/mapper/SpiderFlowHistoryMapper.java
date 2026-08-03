package com.chua.starter.spider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.spider.entity.SpiderFlowHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程历史版本 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SpiderFlowHistoryMapper extends BaseMapper<SpiderFlowHistory> {
}

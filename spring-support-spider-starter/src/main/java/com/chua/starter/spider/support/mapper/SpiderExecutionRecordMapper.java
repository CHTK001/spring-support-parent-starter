package com.chua.starter.spider.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.spider.support.domain.SpiderExecutionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 执行记录 Mapper
 */
@Mapper
public interface SpiderExecutionRecordMapper extends BaseMapper<SpiderExecutionRecord> {
}

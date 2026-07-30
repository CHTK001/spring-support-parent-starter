package com.chua.spider.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.spider.support.entity.SpiderDataRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集数据记录 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SpiderDataRecordMapper extends BaseMapper<SpiderDataRecord> {
}
package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectDatasource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集数据源 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectDatasourceMapper extends BaseMapper<CollectDatasource> {
}

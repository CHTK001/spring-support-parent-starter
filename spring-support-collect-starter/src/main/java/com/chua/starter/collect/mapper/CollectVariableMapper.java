package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectVariable;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集全局变量 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectVariableMapper extends BaseMapper<CollectVariable> {
}

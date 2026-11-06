package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectFunction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集自定义函数 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectFunctionMapper extends BaseMapper<CollectFunction> {
}

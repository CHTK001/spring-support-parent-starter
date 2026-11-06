package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectField;
import org.apache.ibatis.annotations.Mapper;

/**
 * 结果字段定义 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectFieldMapper extends BaseMapper<CollectField> {
}

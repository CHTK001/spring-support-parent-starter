package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectTaskLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 节点执行日志 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectTaskLogMapper extends BaseMapper<CollectTaskLog> {
}

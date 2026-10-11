package com.chua.starter.datasourcehub.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.datasourcehub.support.entity.MsDatasourceOperLog;

/**
 * 数据源中心操作日志 Mapper。
 *
 * <p>只提供 MyBatis-Plus 的基础增删查：日志的写入是单条 insert，
 * 查询条件全部落在 {@code (datasource_id, create_time)} 这条索引上，
 * 没有需要手写 SQL 的地方。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public interface MsDatasourceOperLogMapper extends BaseMapper<MsDatasourceOperLog> {
}
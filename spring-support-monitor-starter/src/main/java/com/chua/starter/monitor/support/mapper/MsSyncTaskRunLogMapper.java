package com.chua.starter.monitor.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.support.entity.MsSyncTaskRunLog;

/**
 * 同步执行日志映射器。
 *
 * <p>只用 MyBatis-Plus 的通用 CRUD，不写自定义 SQL，因此不需要配套的 {@code *Mapper.xml}；
 * 本模块规定 SQL 只能落在 {@code *Mapper.xml}，不写即不违反。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public interface MsSyncTaskRunLogMapper extends BaseMapper<MsSyncTaskRunLog> {
}

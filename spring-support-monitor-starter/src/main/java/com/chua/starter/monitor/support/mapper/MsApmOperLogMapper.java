package com.chua.starter.monitor.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.support.entity.MsApmOperLog;

/**
 * APM 操作日志 Mapper。
 *
 * <p>只提供 MyBatis-Plus 的基础增删查：写入是单条 insert，
 * 查询条件全部落在 {@code create_time} 与 {@code action} 上，
 * 没有需要手写 SQL 的地方。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
public interface MsApmOperLogMapper extends BaseMapper<MsApmOperLog> {
}

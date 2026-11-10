package com.chua.starter.monitor.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.support.entity.MsServerOperLog;

/**
 * 服务管理节点操作日志 Mapper。
 *
 * <p>只提供 MyBatis-Plus 的基础增删查：日志的写入是单条 insert，
 * 查询条件全部落在 {@code (node_id, create_time)} 这条索引上，
 * 没有需要手写 SQL 的地方。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
public interface MsServerOperLogMapper extends BaseMapper<MsServerOperLog> {
}

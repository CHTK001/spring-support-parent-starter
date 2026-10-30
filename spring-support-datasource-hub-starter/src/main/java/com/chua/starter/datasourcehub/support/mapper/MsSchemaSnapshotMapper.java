package com.chua.starter.datasourcehub.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.datasourcehub.support.entity.MsSchemaSnapshot;

/**
 * 表结构版本快照 Mapper。
 *
 * <p>只提供 MyBatis-Plus 的基础增删查：快照的写入是单条 insert，
 * 查询条件全部落在 {@code (ds_id, table, create_time)} 这条复合索引上，
 * 不需要手写 SQL。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface MsSchemaSnapshotMapper extends BaseMapper<MsSchemaSnapshot> {
}

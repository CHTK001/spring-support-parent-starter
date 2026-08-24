package com.chua.starter.ai.support.nomifun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.nomifun.store.StoredMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * nomifun 前端消息表（sys_nomifun_message）Mapper。
 *
 * <p>继承 {@link BaseMapper}，提供消息的增删改查能力（MyBatis-Plus 标准 CRUD）；
 * 复杂查询可在此接口补充自定义 SQL。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface NomifunMessageMapper extends BaseMapper<StoredMessage> {
}
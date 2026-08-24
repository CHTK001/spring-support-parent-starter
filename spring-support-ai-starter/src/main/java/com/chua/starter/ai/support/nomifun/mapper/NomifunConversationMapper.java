package com.chua.starter.ai.support.nomifun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.nomifun.store.Conversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * nomifun 前端会话表（sys_nomifun_conversation）Mapper。
 *
 * <p>继承 {@link BaseMapper}，提供会话的增删改查能力（MyBatis-Plus 标准 CRUD）；
 * 复杂查询可在此接口补充自定义 SQL。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface NomifunConversationMapper extends BaseMapper<Conversation> {
}
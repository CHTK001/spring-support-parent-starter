package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiConversationUuid;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会话 UUID 映射 Mapper。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiConversationUuidMapper extends BaseMapper<SysAiConversationUuid> {

    /**
     * 根据 UUID 查找对话 ID
     */
    @Select("SELECT dialog_id FROM sys_ai_conversation_uuid WHERE uuid = #{uuid}")
    Long findDialogIdByUuid(@Param("uuid") String uuid);

    /**
     * 根据对话 ID 查找 UUID
     */
    @Select("SELECT uuid FROM sys_ai_conversation_uuid WHERE dialog_id = #{dialogId}")
    String findUuidByDialogId(@Param("dialogId") Long dialogId);
}

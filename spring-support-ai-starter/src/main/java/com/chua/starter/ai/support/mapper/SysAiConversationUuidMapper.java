package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiConversationUuid;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
    Long findDialogIdByUuid(@Param("uuid") String uuid);

    /**
     * 根据对话 ID 查找 UUID
     */
    String findUuidByDialogId(@Param("dialogId") Long dialogId);
}

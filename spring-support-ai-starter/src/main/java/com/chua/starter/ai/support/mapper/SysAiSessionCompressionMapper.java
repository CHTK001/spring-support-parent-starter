package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiSessionCompression;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 会话压缩上下文 Mapper。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiSessionCompressionMapper extends BaseMapper<SysAiSessionCompression> {

    /**
     * 按会话 ID 查询压缩上下文。
     *
     * @param sessionId 会话 ID
     * @return 压缩上下文实体，不存在时返回 {@code null}
     */
    @Select("SELECT * FROM sys_ai_session_compression WHERE sys_ai_session_id = #{sessionId} LIMIT 1")
    SysAiSessionCompression selectBySessionId(@Param("sessionId") Long sessionId);
}

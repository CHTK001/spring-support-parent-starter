package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiSessionCompression;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
* AI 会话压缩上下文 映射器。
*
* @since 4.0.0.42
* @author CH
 */
@Mapper
public interface SysAiSessionCompressionMapper extends BaseMapper<SysAiSessionCompression> {

    /**
    * 按会话 标识 查询压缩上下文。
    *
    * @param sessionId 会话 标识
    * @return 压缩上下文实体，不存在时返回 {@code null}
    * @param @Param("sessionId" @参数("会话标识"
     */
    SysAiSessionCompression selectBySessionId(@Param("sessionId") Long sessionId);
}

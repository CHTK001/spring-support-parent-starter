package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiSessionCompression;

/**
* AI 会话压缩上下文服务。
*
* <p>负责读写会话的压缩上下文，供聊天链路在「有上下文用上下文，没有用历史对话记录」时使用。</p>
*
* @since 4.0.0.42
* @author CH
 */
public interface SysAiSessionCompressionService {

    /**
    * 按会话 标识 查询压缩上下文。
    *
    * @param sessionId 会话 标识
    * @return 压缩上下文实体，不存在时返回 {@code null}
     */
    SysAiSessionCompression getBySessionId(Long sessionId);

    /**
    * 保存（新增或更新）会话的压缩上下文。
    *
    * @param compression 压缩上下文实体
     */
    void saveOrUpdate(SysAiSessionCompression compression);

    /**
    * 删除会话的压缩上下文。
    *
    * @param sessionId 会话 标识
     */
    void deleteBySessionId(Long sessionId);
}

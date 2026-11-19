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
     * 按主键查询压缩上下文（带归属校验）。
     *
     * @param compressionId 压缩上下文主键
     * @return 压缩上下文实体；不存在或归属他人时返回 {@code null}
     */
    SysAiSessionCompression getById(Integer compressionId);

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

    /**
     * 改写某条压缩上下文的摘要正文（前端「编辑摘要」）。
     *
     * @param compressionId 压缩上下文主键
     * @param context       新的摘要正文
     * @return 写入结果：成功 / 不存在 / 无权（非归属人或只读账号）
     */
    WriteResult updateContext(Integer compressionId, String context);

    /**
     * 按主键删除单条压缩上下文（前端「删除摘要」）。
     *
     * @param compressionId 压缩上下文主键
     * @return 删除结果：成功 / 不存在 / 无权（非归属人或只读账号）
     */
    WriteResult deleteById(Integer compressionId);

    /**
     * 压缩上下文的写操作结果。
     *
     * <p>用三态而不是布尔：前端要区分「记录已不存在」（404）与「无权访问他人摘要」（403），
     * 布尔会把两种完全不同的处置方式压成同一个失败。</p>
     */
    enum WriteResult {
        /**
         * 写入成功
         */
        OK,
        /**
         * 记录不存在
         */
        NOT_FOUND,
        /**
         * 当前账号无权写入该记录
         */
        FORBIDDEN
    }
}

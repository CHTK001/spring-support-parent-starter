package com.chua.starter.ai.support.configuration.rag;

/**
 * RAG 全局设置变更事件
 *
 * @param group      变更的分组
 * @param userScoped 是否由<b>用户级覆盖</b>写入触发（共享的嵌入/向量实例只承载全局配置，收到时应跳过重建）
 * @param userId     触发变更的用户标识（仅用户级写入时有值）
 * @since 4.0.0.42
 * @author CH
 */
public record RagSettingsChangedEvent(String group, boolean userScoped, String userId) {

    /**
     * 构造事件（全局写入）。
     *
     * @param group 变更的分组
     */
    public RagSettingsChangedEvent(String group) {
        this(group, false, null);
    }

    /**
     * 构造事件。
     *
     * @param group      变更的分组
     * @param userScoped 是否由用户级覆盖写入触发
     */
    public RagSettingsChangedEvent(String group, boolean userScoped) {
        this(group, userScoped, null);
    }
}

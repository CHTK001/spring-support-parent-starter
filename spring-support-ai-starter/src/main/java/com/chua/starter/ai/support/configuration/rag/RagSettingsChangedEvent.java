package com.chua.starter.ai.support.configuration.rag;

/**
 * RAG 全局设置变更事件
 *
 * @since 4.0.0.42
 * @author CH
 */
public class RagSettingsChangedEvent {

    /**
     * 变更的分组
     */
    private final String group;

    /**
     * 是否由**用户级覆盖**写入触发（共享的嵌入/向量实例只承载全局配置，收到时应跳过重建）。
     */
    private final boolean userScoped;

    /**
     * 触发变更的用户标识（仅用户级写入时有值）。
     */
    private final String userId;

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

    /**
     * 构造事件（用户级写入，携带归属用户）。
     *
     * @param group      变更的分组
     * @param userScoped 是否由用户级覆盖写入触发
     * @param userId     触发变更的用户标识，可为空
     */
    public RagSettingsChangedEvent(String group, boolean userScoped, String userId) {
        this.group = group;
        this.userScoped = userScoped;
        this.userId = userId;
    }

    /**
     * 获取变更分组。
     *
     * @return 分组
     */
    public String getGroup() {
        return group;
    }

    /**
     * 是否由用户级覆盖写入触发。
     *
     * @return 用户级写入返回 {@code true}
     */
    public boolean isUserScoped() {
        return userScoped;
    }

    /**
     * 触发变更的用户标识。
     *
     * @return 用户标识，全局写入时为空
     */
    public String getUserId() {
        return userId;
    }
}

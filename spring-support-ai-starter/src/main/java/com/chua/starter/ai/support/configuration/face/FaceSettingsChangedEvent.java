package com.chua.starter.ai.support.configuration.face;

/**
 * 全局设置变更事件 —— 分组配置更新后发布。
 *
 * <p>由 {@link com.chua.starter.ai.support.service.impl.SysAiGlobalSettingServiceImpl#replace}
 * 在写入键值表后发布，携带变更分组；{@link FaceClientManager} 监听并热重建人脸实例，
 * 实现"全局配置实时生效"。事件在配置变更事务提交后仍可安全监听（重建为内存操作）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class FaceSettingsChangedEvent {

    /**
     * 变更的分组
     */
    private final String group;

    /**
     * 是否由**用户级覆盖**写入触发。
     *
     * <p>共享实例（{@code FaceClientManager} 的检测器 / 分类器等单例字段）只承载全局配置，
     * 收到用户级写入应当**跳过重建**：重建它们既不会让该用户的配置生效（它们不是按用户的），
     * 又会把触发者的配置烘进共享实例、串掉其他用户。真正需要按用户生效的是
     * {@code FaceStores} 里**按账号**构建的管线，它收到任何一次该分组变更都重建
     * （重建时会绑定各账号自己的身份）。</p>
     */
    private final boolean userScoped;

    /**
     * 触发变更的用户标识（仅用户级写入时有值；全局写入为 空）。
     *
     * <p>按账号构建的实例（{@code FaceStores} 的账户管线、{@code ImageSearchStores} 的账户库）
     * 据此**只重建受影响账号**，避免在某一用户的身份下把其配置串到其他账号。</p>
     */
    private final String userId;

    /**
     * 构造事件（全局写入）。
     *
     * @param group 变更的分组
     */
    public FaceSettingsChangedEvent(String group) {
        this(group, false, null);
    }

    /**
     * 构造事件。
     *
     * @param group     变更的分组
     * @param userScoped 是否由用户级覆盖写入触发
     */
    public FaceSettingsChangedEvent(String group, boolean userScoped) {
        this(group, userScoped, null);
    }

    /**
     * 构造事件（用户级写入，携带归属用户）。
     *
     * @param group      变更的分组
     * @param userScoped 是否由用户级覆盖写入触发
     * @param userId     触发变更的用户标识，可为空
     */
    public FaceSettingsChangedEvent(String group, boolean userScoped, String userId) {
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

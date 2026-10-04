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
     * 构造事件（全局写入）。
     *
     * @param group 变更的分组
     */
    public FaceSettingsChangedEvent(String group) {
        this(group, false);
    }

    /**
     * 构造事件。
     *
     * @param group     变更的分组
     * @param userScoped 是否由用户级覆盖写入触发
     */
    public FaceSettingsChangedEvent(String group, boolean userScoped) {
        this.group = group;
        this.userScoped = userScoped;
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
}

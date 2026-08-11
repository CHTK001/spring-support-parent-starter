package com.chua.starter.ai.support.configuration.face;

/**
 * 全局设置变更事件 —— 分组配置更新后发布。
 *
 * <p>由 {@link com.chua.starter.ai.support.service.impl.SysAiGlobalSettingServiceImpl#replace}
 * 在写入键值表后发布，携带变更分组；{@link FaceClientManager} 监听并热重建人脸实例，
 * 实现"全局配置实时生效"。事件在配置变更事务提交后仍可安全监听（重建为内存操作）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class FaceSettingsChangedEvent {

    /**
     * 变更的分组
     */
    private final String group;

    /**
     * 构造事件。
     *
     * @param group 变更的分组
     */
    public FaceSettingsChangedEvent(String group) {
        this.group = group;
    }

    /**
     * 获取变更分组。
     *
     * @return 分组
     */
    public String getGroup() {
        return group;
    }
}

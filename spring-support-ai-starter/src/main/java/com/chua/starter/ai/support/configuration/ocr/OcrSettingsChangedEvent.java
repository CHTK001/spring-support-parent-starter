package com.chua.starter.ai.support.configuration.ocr;

/**
 * OCR 全局设置变更事件，在 {@link com.chua.starter.ai.support.service.impl.SysAiGlobalSettingServiceImpl#replace}
 * 写入 ocr 分组值后发布给监听者；{@link com.chua.starter.ai.support.configuration.rag.RagClientManager}
 * 接收事件后重建 OCR 文本提取器，实现"全局设置实时生效"。
 *
 * @since 4.0.0.42
 * @author CH
 */
public class OcrSettingsChangedEvent {

    /**
     * 变更的分组名
    */
    private final String group;

    /**
     * 是否由**用户级覆盖**写入触发（共享 OCR 实例只承载全局配置，收到时应跳过重建）。
     */
    private final boolean userScoped;

    /**
     * 触发变更的用户标识（仅用户级写入时有值）。
     */
    private final String userId;

    /**
     * ocrsettings改变事件。
     * @param group 群体
     */
    public OcrSettingsChangedEvent(String group) {
        this(group, false, null);
    }

    /**
     * ocrsettings改变事件。
     * @param group 群体
     * @param userScoped 是否由用户级覆盖写入触发
     */
    public OcrSettingsChangedEvent(String group, boolean userScoped) {
        this(group, userScoped, null);
    }

    /**
     * ocrsettings改变事件。
     * @param group 群体
     * @param userScoped 是否由用户级覆盖写入触发
     * @param userId 触发变更的用户标识，可为空
     */
    public OcrSettingsChangedEvent(String group, boolean userScoped, String userId) {
        this.group = group;
        this.userScoped = userScoped;
        this.userId = userId;
    }

    /**
     * 获取群体。
     * @return 获取群体的结果
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

package com.chua.starter.ai.support.configuration.paper;

/**
 * 试卷全局设置变更事件，在
 * {@link com.chua.starter.ai.support.service.impl.SysAiGlobalSettingServiceImpl#replace}
 * 写入 {@code paper} 分组值后发布给监听者；
 * {@link com.chua.starter.ai.support.configuration.paper.PaperClientManager}
 * 接收事件后重建试卷识别管线，实现「全局设置实时生效」。
 *
 * @param group      变更的分组名
 * @param userScoped 是否由<b>用户级覆盖</b>写入触发（共享试卷实例只承载全局配置，收到时应跳过重建）
 * @param userId     触发变更的用户标识（仅用户级写入时有值）
 * @since 4.0.0.42
 * @author CH
 */
public record PaperSettingsChangedEvent(String group, boolean userScoped, String userId) {

    /**
     * 全局写入。
     *
     * @param group 变更的分组
     */
    public PaperSettingsChangedEvent(String group) {
        this(group, false, null);
    }

    /**
     * 构造事件。
     *
     * @param group      变更的分组
     * @param userScoped 是否由用户级覆盖写入触发
     */
    public PaperSettingsChangedEvent(String group, boolean userScoped) {
        this(group, userScoped, null);
    }
}
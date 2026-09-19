package com.chua.starter.ai.support.manage.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * 设置变更事件
 * 当用户在前端修改全局设置时发布，用于触发热更新
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public class SettingChangedEvent extends ApplicationEvent {

    /**
     * 设置分组
     */
    private final String group;

    /**
     * 变更的字段
     */
    private final Map<String, Object> changedSettings;

    /**
     * 构造设置变更事件
     *
     * @param source          事件源
     * @param group           设置分组名称
     * @param changedSettings 本次变更的字段集合
     */
    public SettingChangedEvent(Object source, String group, Map<String, Object> changedSettings) {
        super(source);
        this.group = group;
        this.changedSettings = changedSettings;
    }
}

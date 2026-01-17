package com.chua.starter.common.support.application;

import lombok.extern.slf4j.Slf4j;
/**
 * 模块环境注册器
 *
 * <p>用于将各个模块的开关和核心配置注册到全局环境中，统一管理与观测。</p>
 *
 * @author CH
 * @since 2026/01/14
 */
@Slf4j
public class ModuleEnvironmentRegistration {
        /**
     * 使用配置对象类型作为分组标识，将配置注册到全局环境中。
     *
     * @param config 配置对象
     */
    public ModuleEnvironmentRegistration(Object config) {
        this(config != null ? config.getClass().getName() : null, config, true);
    }

    /**
     * 使用指定分组标识和启用状态注册配置。
     *
     * @param group   分组标识（例如配置前缀）
     * @param config  配置对象
     * @param enabled 是否启用
     */
    public ModuleEnvironmentRegistration(String group, Object config, boolean enabled) {
        if (config == null || group == null || group.isEmpty()) {
            return;
        }
        GlobalSettingFactory globalSettingFactory = GlobalSettingFactory.getInstance();
        globalSettingFactory.register(group, config, enabled);
        log.info("[环境配置]已注册模块配置, 组: {}, 启用: {}, 类型: {}", group, enabled, config.getClass().getName());
    }
}


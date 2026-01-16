package com.chua.starter.common.support.configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.chua.starter.common.support.application.GlobalSettingFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 全局环境配置
 *
 * <p>提供全局环境 Bean，并在应用启动完成后输出所有模块注册的开关和核心配置。</p>
 *
 * @author CH
 * @since 2026/01/14
 */
@Configuration
public class GlobalEnvironmentConfiguration implements ApplicationListener<ApplicationReadyEvent> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalEnvironmentConfiguration.class);


    /**
     * 全局环境工厂 Bean
     *
     * @return 全局环境工厂
     */
    @Bean
    public GlobalSettingFactory globalSettingFactory() {
        return GlobalSettingFactory.getInstance();
    }

    /**
     * 应用启动完成后打印所有已注册的环境配置
     *
     * @param event 启动完成事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        GlobalSettingFactory globalSettingFactory = GlobalSettingFactory.getInstance();
        Map<String, List<Object>> allGroup = globalSettingFactory.getAllGroup();
        if (allGroup == null || allGroup.isEmpty()) {
            log.info("[环境配置]当前未注册任何模块环境配置");
            return;
        }

        Map<String, Boolean> allGroupEnabled = globalSettingFactory.getAllGroupEnabled();
        log.info("[环境配置]应用启动完成, 已注册模块环境配置组数量: {}", allGroup.size());
        for (Map.Entry<String, List<Object>> entry : allGroup.entrySet()) {
            String group = entry.getKey();
            List<Object> configs = entry.getValue();
            if (configs == null || configs.isEmpty()) {
                log.info("[环境配置]{}: [关闭] (无配置对象)", group);
                continue;
            }
            boolean enabled = Boolean.TRUE.equals(allGroupEnabled.getOrDefault(group, Boolean.TRUE));
            String statusLabel = enabled ? "开启" : "关闭";
            log.info("[环境配置]{}: [{}] (配置数量: {})", group, statusLabel, configs.size());
            for (int i = 0; i < configs.size(); i++) {
                Object config = configs.get(i);
                if (config == null) {
                    continue;
                }
                log.info("[环境配置]  ├─ 顺序: {}, 类型: {}, 配置内容: {}", i, config.getClass().getName(), config);
            }
        }
    }
}


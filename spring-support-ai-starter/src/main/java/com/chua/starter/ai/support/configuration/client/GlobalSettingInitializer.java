package com.chua.starter.ai.support.configuration.client;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 全局设置初始化器——启动时通过 SPI 发现 {@link GlobalSettingProvider}，
 * 自动将各模块的默认配置项注册到 {@code sys_ai_global_setting} 表（仅首次）。
 *
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class GlobalSettingInitializer implements ApplicationRunner {

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 启动时注册所有 SPI 模块的默认全局配置项（仅首次）。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        ServiceProvider<GlobalSettingProvider> provider = ServiceProvider.of(GlobalSettingProvider.class);
        List<GlobalSettingProvider> providers = provider.collect();

        if (providers.isEmpty()) {
            log.debug("[GlobalSettingInitializer] 未发现 GlobalSettingProvider SPI 实现");
            return;
        }

        for (GlobalSettingProvider gsp : providers) {
            try {
                String group = gsp.group();
                // 检查是否已有配置项
                List<Map<String, Object>> existing = globalSettingService.list(group);
                if (!existing.isEmpty()) {
                    log.debug("[GlobalSettingInitializer] 分组 '{}' 已有 {} 项配置，跳过", group, existing.size());
                    continue;
                }

                // 构造默认配置项
                List<Map<String, Object>> fields = gsp.getItems().stream()
                        .map(item -> {
                            Map<String, Object> field = new java.util.LinkedHashMap<>();
                            field.put("field", item.field());
                            field.put("value", item.defaultValue());
                            field.put("valueType", item.valueType());
                            field.put("description", item.description());
                            field.put("sort", item.sort());
                            return field;
                        })
                        .toList();

                Map<String, Object> request = new java.util.LinkedHashMap<>();
                request.put("group", group);
                request.put("fields", fields);

                globalSettingService.replace(request);
                log.info("[GlobalSettingInitializer] 已注册分组 '{}' ({}): {} 项配置",
                        group, gsp.groupName(), fields.size());
            } catch (Exception e) {
                log.warn("[GlobalSettingInitializer] 注册分组 '{}' 失败: {}", gsp.group(), e.getMessage());
            }
        }
    }
}

package com.chua.starter.ai.support.configuration;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.model.SysAiGlobalSettingSaveRequest;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

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
public class GlobalSettingInitializer implements ApplicationRunner {

    private final SysAiGlobalSettingService globalSettingService;

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
                List<SysAiGlobalSetting> settings = gsp.getItems().stream()
                        .map(item -> {
                            SysAiGlobalSetting setting = new SysAiGlobalSetting();
                            setting.setSysAiGlobalSettingGroup(group);
                            setting.setSysAiGlobalSettingField(item.field());
                            setting.setSysAiGlobalSettingValue(item.defaultValue());
                            setting.setSysAiGlobalSettingValueType(item.valueType());
                            setting.setSysAiGlobalSettingDescription(item.description());
                            setting.setSysAiGlobalSettingSort(item.sort());
                            setting.setSysAiGlobalSettingStatus(1);
                            return setting;
                        })
                        .toList();

                SysAiGlobalSettingSaveRequest request = new SysAiGlobalSettingSaveRequest();
                request.setSysAiGlobalSettingGroup(group);
                request.setSettings(settings);

                globalSettingService.replace(request);
                log.info("[GlobalSettingInitializer] 已注册分组 '{}' ({}): {} 项配置",
                        group, gsp.groupName(), settings.size());
            } catch (Exception e) {
                log.warn("[GlobalSettingInitializer] 注册分组 '{}' 失败: {}", gsp.group(), e.getMessage());
            }
        }
    }
}

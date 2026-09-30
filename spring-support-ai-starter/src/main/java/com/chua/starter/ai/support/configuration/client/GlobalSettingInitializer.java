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
 * @since 4.0.0
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
     * 分组定义缓存：启动期在这里预热，请求线程直接读，绕开 SPI 发现在请求线程返回空的问题
     */
    private final GlobalSettingDefinitions globalSettingDefinitions;

    /**
     * 启动时同步所有 SPI 模块的默认全局配置项。
     *
     * <p>动态同步语义：分组已有部分配置时，仅补充 provider 定义但 DB 缺失的配置项，
     * 不覆盖用户已配置的值；分组为空时全量注册。</p>
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        // 预热分组定义缓存：本线程（main）上的 SPI 发现是正常的，而请求线程里会返回空，
        // 详细原因见 GlobalSettingDefinitions 的说明。必须在 collect() 之前调用，
        // 让请求线程从一开始就有数据可读。
        globalSettingDefinitions.warmUp();

        ServiceProvider<GlobalSettingProvider> provider = ServiceProvider.of(GlobalSettingProvider.class);
        List<GlobalSettingProvider> providers = provider.collect();

        if (providers.isEmpty()) {
            log.debug("[GlobalSettingInitializer] 未发现 GlobalSettingProvider SPI 实现");
            return;
        }

        for (GlobalSettingProvider gsp : providers) {
            try {
                String group = gsp.group();
                List<Map<String, Object>> existing = globalSettingService.list(group);
                List<GlobalSettingProvider.SettingItem> items = gsp.getItems();
                if (items.isEmpty()) {
                    continue;
                }
                if (existing.isEmpty()) {
                    // 全量注册
                    List<Map<String, Object>> fields = items.stream()
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
                    continue;
                }

                // 增量同步：仅补缺失项，不覆盖已有值
                java.util.Set<String> existingFields = existing.stream()
                        .map(m -> String.valueOf(m.get("field")))
                        .collect(java.util.stream.Collectors.toSet());
                int added = 0;
                for (GlobalSettingProvider.SettingItem item : items) {
                    if (existingFields.contains(item.field())) {
                        continue;
                    }
                    globalSettingService.upsertField(group, item.field(), item.defaultValue(),
                            item.valueType(), item.description(), item.sort());
                    added++;
                }
                // 描述同步：文案随 provider 迭代修正，但绝不覆盖用户已保存的值
                int synced = globalSettingService.syncDescriptions(group, items.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                GlobalSettingProvider.SettingItem::field,
                                GlobalSettingProvider.SettingItem::description,
                                (a, b) -> a)));
                log.info("[GlobalSettingInitializer] 分组 '{}' ({}): 已有 {} 项，新增 {} 项，同步描述 {} 项",
                        group, gsp.groupName(), existing.size(), added, synced);
            } catch (Exception e) {
                log.warn("[GlobalSettingInitializer] 注册分组 '{}' 失败: {}", gsp.group(), e.getMessage());
            }
        }
    }
}

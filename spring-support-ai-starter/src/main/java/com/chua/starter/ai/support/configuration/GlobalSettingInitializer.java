package com.chua.starter.ai.support.configuration;

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
     * 环境变量：智谱 GLM API Key（联调时注入，不硬编码敏感信息）
     */
    private static final String ENV_ZHIPU_API_KEY = "AI_ZHIPU_API_KEY";

    /**
     * 启动时注册所有 SPI 模块的默认全局配置项（仅首次）。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        // 优先注入 runtime 分组 LLM 配置（不依赖 SPI providers，保证总能执行）
        initRuntimeLlms();

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

    /**
     * 联调辅助：通过环境变量注入 runtime 分组的 LLM 提供者配置（provider/factory/baseUrl/apiKey）。
     *
     * <p>未设置对应环境变量时跳过，不影响生产默认行为。</p>
     */
    private void initRuntimeLlms() {
        String zhipuApiKey = System.getenv(ENV_ZHIPU_API_KEY);
        if (zhipuApiKey == null || zhipuApiKey.isBlank()) {
            log.debug("[GlobalSettingInitializer] 未设置环境变量 {}，跳过 runtime LLM 注入", ENV_ZHIPU_API_KEY);
            return;
        }
        List<Map<String, Object>> existing = globalSettingService.list("runtime");
        if (!existing.isEmpty()) {
            log.debug("[GlobalSettingInitializer] runtime 分组已有 {} 项配置，跳过 LLM 注入", existing.size());
            return;
        }
        // 使用智谱原生 ChatClient（ZhipuChatClient，SPI: zhipu/glm），避免 OpenAI 兼容层兼容性问题
        List<Map<String, Object>> fields = List.of(
                Map.of("field", "provider", "value", "zhipu", "valueType", "STRING", "description", "LLM 提供者"),
                Map.of("field", "factory", "value", "zhipu", "valueType", "STRING", "description", "LLM 工厂类型"),
                Map.of("field", "provider_api_key", "value", zhipuApiKey, "valueType", "STRING", "description", "LLM API Key"),
                Map.of("field", "default_model", "value", "glm-4-flash", "valueType", "STRING", "description", "默认模型")
        );
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("group", "runtime");
        request.put("fields", fields);
        globalSettingService.replace(request);
        log.info("[GlobalSettingInitializer] 已注入 runtime 分组 LLM 配置（provider=zhipu, model=glm-4-flash）");
    }
}

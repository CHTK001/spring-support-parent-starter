package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.configuration.client.GlobalChatClientManager;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 对话路由配置管理控制器。
 *
 * <p>提供 {@code runtime} 分组全局配置的读取与更新（LLM 供应商、API Key、默认模型、超时等），
 * 配置数据完全来自 {@code sys_ai_global_setting} 表。更新后通过
 * {@link GlobalChatClientManager#rebuild()} 热重建全局 ChatClient，实时生效。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/runtime/config")
@RequiredArgsConstructor
@Tag(name = "AI 对话路由 - 配置管理")
public class AiRuntimeSettingController {

    /**
     * 对话路由配置分组
     */
    private static final String GROUP = "runtime";

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService settingService;

    /**
     * 全局 ChatClient 管理器（用于配置更新后热重建）
     */
    private final GlobalChatClientManager globalChatClientManager;

    /**
     * 获取对话路由配置项定义与当前值。
     *
     * @return 配置列表（field/value/valueType/description/sort）
     */
    @Permission("admin")
    @Operation(summary = "获取对话路由配置", description = "返回 runtime 分组全部配置项及当前值（LLM 供应商/API Key/默认模型等）")
    @GetMapping
    public ReturnResult<List<Map<String, Object>>> config() {
        List<Map<String, Object>> fields = new ArrayList<>();

        List<Map<String, Object>> current = settingService.list(GROUP);
        Map<String, Object> valueMap = current.stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.get("field")), m -> m, (a, b) -> a));

        List<GlobalSettingProvider> providers = ServiceProvider.of(GlobalSettingProvider.class).collect();
        for (GlobalSettingProvider provider : providers) {
            if (!GROUP.equals(provider.group())) {
                continue;
            }
            for (GlobalSettingProvider.SettingItem item : provider.getItems()) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("field", item.field());
                field.put("valueType", item.valueType());
                field.put("description", item.description());
                field.put("sort", item.sort());
                Map<String, Object> existing = (Map<String, Object>) valueMap.get(item.field());
                field.put("value", existing != null ? existing.get("value") : item.defaultValue());
                fields.add(field);
            }
        }

        // 补充数据库中已存在但 SPI 未声明的字段（保留兼容）
        for (Map<String, Object> m : current) {
            String f = String.valueOf(m.get("field"));
            if (fields.stream().noneMatch(x -> f.equals(x.get("field")))) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("field", f);
                field.put("valueType", m.get("valueType"));
                field.put("description", m.get("description"));
                field.put("sort", m.get("sort"));
                field.put("value", m.get("value"));
                fields.add(field);
            }
        }

        fields.sort((a, b) -> Integer.compare(toInt(a.get("sort")), toInt(b.get("sort"))));
        return ReturnResult.ok(fields);
    }

    /**
     * 更新对话路由配置（增量更新，触发热重建）。
     *
     * <p>逐字段写入，避免整组替换删除未提交的其它配置项。</p>
     *
     * @param body 配置请求（fields 列表）
     * @return 更新结果
     */
    @Permission("admin")
    @Operation(summary = "更新对话路由配置", description = "增量更新 runtime 配置并触发热重建全局 ChatClient")
    @PutMapping
    public ReturnResult<?> update(@RequestBody Map<String, Object> body) {
        Object fieldsObj = body.getOrDefault("fields", body);
        if (!(fieldsObj instanceof List<?> fields) || fields.isEmpty()) {
            return ReturnResult.ok("配置字段列表为空");
        }
        // 逐字段增量写入，避免整组替换删除未提交的其它配置
        for (Object item : fields) {
            if (!(item instanceof Map<?, ?> field)) {
                continue;
            }
            Object fieldNameObj = field.get("field");
            if (fieldNameObj == null) {
                continue;
            }
            String fieldName = fieldNameObj.toString();
            if (fieldName.isBlank()) {
                continue;
            }
            Object valueObj = field.get("value");
            String value = valueObj != null ? valueObj.toString() : "";
            Object descObj = field.get("description");
            String description = descObj != null ? descObj.toString() : "";
            settingService.upsertValue(GROUP, fieldName, value, description);
        }
        try {
            globalChatClientManager.rebuild();
        } catch (Exception e) {
            log.warn("[RuntimeConfig] runtime 分组配置已更新但热重建异常: {}", e.getMessage());
        }
        log.info("[RuntimeConfig] runtime 分组配置已更新并触发热重建");
        return ReturnResult.ok("对话路由配置已更新");
    }

    /**
     * 安全转换整数。
     *
     * @param obj 值
     * @return int，非法返回 0
     */
    private static int toInt(Object obj) {
        if (obj instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return 0;
        }
    }
}

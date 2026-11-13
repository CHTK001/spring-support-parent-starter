package com.chua.starter.ai.support.configuration.actuator;

import com.chua.starter.ai.support.configuration.client.GlobalSettingDefinitions;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 生效配置端点：{@code GET /actuator/ai-config}。
 *
 * <p>把「当前每个能力到底用了哪个模型、值从哪来」一次性摊开：
 * 分组 → 字段 → 生效值 / 默认值 / 来源（YAML / DB / DEFAULT）。
 * 凭据类字段（api_key / secret / token / password 等）统一脱敏为 {@code ***}，
 * 可以放心给运维和前端排障用。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Endpoint(id = "ai-config")
@RequiredArgsConstructor
public class AiConfigEndpoint {

    /**
     * YAML 覆盖层前缀，与 {@code AiSettingsYamlProperties} 保持一致
     */
    private static final String YAML_PREFIX = "plugin.ai.settings.";

    /**
     * 脱敏占位
     */
    private static final String MASK = "***";

    /**
     * 命中即脱敏的字段名关键字
     */
    private static final String[] SECRET_HINTS = {
            "api_key", "apikey", "app_key", "appkey", "app_secret", "secret",
            "token", "password", "credential"
    };

    /**
     * 分组定义（启动期预热缓存）
     */
    private final GlobalSettingDefinitions definitions;

    /**
     * 全局设置服务（当前库值）
     */
    private final SysAiGlobalSettingService settingService;

    /**
     * 环境（判断某字段是否被 YAML 覆盖）
     */
    private final Environment environment;

    /**
     * 读取全部生效配置。
     *
     * @return 分组 → { groupName, fields: { field → { value, defaultValue, source, masked, adminOnly } } }
     */
    @ReadOperation
    public Map<String, Object> read() {
        Map<String, Object> groups = new LinkedHashMap<>();
        for (Map<String, Object> definition : definitions.definitions()) {
            String group = String.valueOf(definition.get("group"));
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("groupName", definition.get("groupName"));
            node.put("fields", fields(definition, group));
            groups.put(group, node);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupCount", groups.size());
        result.put("groups", groups);
        return result;
    }

    /**
     * 渲染一个分组下的全部字段。
     *
     * @param definition 分组定义
     * @param group      分组标识
     * @return 字段 → 生效值信息
     */
    private Map<String, Object> fields(Map<String, Object> definition, String group) {
        Map<String, String> current = currentValues(group);
        Map<String, Object> fields = new LinkedHashMap<>();
        Object rawItems = definition.get("items");
        if (!(rawItems instanceof List<?> items)) {
            return fields;
        }
        for (Object raw : items) {
            if (!(raw instanceof Map<?, ?> item)) {
                continue;
            }
            String field = String.valueOf(item.get("field"));
            String defaultValue = item.get("defaultValue") == null ? "" : String.valueOf(item.get("defaultValue"));
            String yamlKey = YAML_PREFIX + group + "." + field;
            boolean fromYaml = environment.containsProperty(yamlKey);
            boolean fromDb = current.containsKey(field);
            String value = fromYaml
                    ? environment.getProperty(yamlKey)
                    : fromDb ? current.get(field) : defaultValue;
            boolean secret = isSecret(field);
            boolean masked = secret && value != null && !value.isEmpty();

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("value", masked ? MASK : value == null ? "" : value);
            node.put("defaultValue", secret && !defaultValue.isEmpty() ? MASK : defaultValue);
            node.put("source", fromYaml ? "YAML" : fromDb ? "DB" : "DEFAULT");
            node.put("masked", masked);
            node.put("adminOnly", Boolean.TRUE.equals(item.get("adminOnly")));
            fields.put(field, node);
        }
        return fields;
    }

    /**
     * 取一个分组当前库值。
     *
     * @param group 分组标识
     * @return field → value
     */
    private Map<String, String> currentValues(String group) {
        Map<String, String> current = new LinkedHashMap<>();
        for (Map<String, Object> row : settingService.list(group)) {
            Object field = row.get("field");
            if (field == null) {
                continue;
            }
            Object value = row.get("value");
            current.put(String.valueOf(field), value == null ? "" : String.valueOf(value));
        }
        return current;
    }

    /**
     * 字段是否属于凭据类。
     *
     * @param field 字段名
     * @return true 表示需要脱敏
     */
    private static boolean isSecret(String field) {
        String lower = field.toLowerCase(Locale.ROOT);
        for (String hint : SECRET_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}

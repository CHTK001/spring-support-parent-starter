package com.chua.starter.ai.support.configuration.actuator;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.properties.DefaultModelResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认模型端点：{@code GET /actuator/ai-models}。
 *
 * <p>回答「每个能力最终用哪个模型」：</p>
 * <ul>
 *   <li>{@code runtime}：当前对话路由实际生效的 provider / 默认模型 / 压缩模型；</li>
 *   <li>{@code resolved}：{@link DefaultModelResolver} 解析出的能力 → 模型（页面配置优先，
 *       其次 {@code ai.default-model.*}）；该解析器使用独立的 {@code models} 分组，
 *       未在该页配置过时为空，属正常。</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Endpoint(id = "ai-models")
@RequiredArgsConstructor
public class AiModelsEndpoint {

    /**
     * 默认模型解析器（页面 > YAML）
     */
    private final DefaultModelResolver defaultModelResolver;

    /**
     * 配置读取（runtime 分组的对话路由）
     */
    private final GlobalSettingReader settingReader;

    /**
     * 读取各能力最终生效的模型。
     *
     * @return runtime + resolved
     */
    @ReadOperation
    public Map<String, Object> read() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runtime", runtime());
        result.put("chat", defaultModelResolver.chat());
        Map<String, String> resolved = defaultModelResolver.resolveAll();
        result.put("resolvedCount", resolved.size());
        result.put("resolved", resolved);
        return result;
    }

    /**
     * 对话路由的实际配置（runtime 分组）。
     *
     * @return provider / defaultModel / compressionModel
     */
    private Map<String, Object> runtime() {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("provider", valueOrEmpty("provider"));
        runtime.put("defaultModel", valueOrEmpty("default_model"));
        runtime.put("compressionModel", valueOrEmpty("compression_model"));
        runtime.put("timeoutMillis", valueOrEmpty("timeout_millis"));
        return runtime;
    }

    /**
     * 读取 runtime 字段，未配置返回空串。
     *
     * @param field 字段名
     * @return 值或空串
     */
    private String valueOrEmpty(String field) {
        try {
            String value = settingReader.getStr("runtime", field);
            return value == null ? "" : value;
        } catch (Throwable ex) {
            return "";
        }
    }
}

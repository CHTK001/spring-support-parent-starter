package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ChannelImportEntry;
import com.chua.starter.account.spi.ChannelImportFile;
import com.chua.starter.account.spi.ChannelImportModelDraft;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Claude Code settings.json 解析。
 *
 * <p>结构：{@code { env: { ANTHROPIC_AUTH_TOKEN, ANTHROPIC_BASE_URL,
 * ANTHROPIC_DEFAULT_SONNET_MODEL, ... } }}；兼容把 {@code ANTHROPIC_*} 直接写在
 * 根上的写法（env 字段优先，根级不覆盖 env）。单文件只描述一条上游，返回 0 或
 * 1 个条目；地址与密钥全空 → 空清单（调用方提示「没有解析出供应商配置」）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("claude-settings")
public class ClaudeSettingsConfigImportParser extends AbstractChannelConfigImportParser {

    @Override
    public List<ChannelImportEntry> parse(List<ChannelImportFile> files) {
        JsonNode root = parseJsonc(firstText(files));
        // env 优先在 env 字段里找，其次兼容直接铺在根上的 ANTHROPIC_*（后者不覆盖前者）
        Map<String, String> env = new LinkedHashMap<>();
        collectAnthropicKeys(root, env);
        collectAnthropicKeys(obj(root.path("env")), env);

        String baseUrl = env.getOrDefault("ANTHROPIC_BASE_URL", "");
        String apiKey = firstNonBlank(env.get("ANTHROPIC_AUTH_TOKEN"), env.get("ANTHROPIC_API_KEY"));
        String modelId = firstNonBlank(env.get("ANTHROPIC_DEFAULT_SONNET_MODEL"),
                env.get("ANTHROPIC_DEFAULT_OPUS_MODEL"), env.get("ANTHROPIC_DEFAULT_HAIKU_MODEL"),
                env.get("ANTHROPIC_MODEL"));
        if (baseUrl.isEmpty() && apiKey.isEmpty()) {
            return new ArrayList<>();
        }

        List<ChannelImportModelDraft> models = new ArrayList<>();
        if (!modelId.isEmpty()) {
            models.add(draft(modelId, modelId, null, null, "chat"));
        }
        String name = nameFromUrl(baseUrl);
        ChannelImportEntry entry = new ChannelImportEntry();
        entry.setKey("claude-settings:main");
        entry.setSource("claude-settings");
        entry.setName(name.isEmpty() ? "Claude 配置" : name);
        entry.setPlatform("anthropic");
        entry.setBaseUrl(baseUrl);
        entry.setApiKey(apiKey);
        entry.setHasKey(!apiKey.isEmpty());
        entry.setModels(models);
        if (baseUrl.isEmpty()) {
            entry.setSkip("未配置 ANTHROPIC_BASE_URL，无法导入");
        }
        return List.of(entry);
    }

    /**
     * 收集对象里 ANTHROPIC_* 开头的键（Claude settings 兼容根级写法）。
     */
    private void collectAnthropicKeys(JsonNode node, Map<String, String> env) {
        if (node == null || !node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(field -> {
            String key = field.getKey();
            if (key != null && key.startsWith("ANTHROPIC_")) {
                String value = str(field.getValue());
                if (!value.isEmpty()) {
                    env.put(key, value);
                }
            }
        });
    }
}

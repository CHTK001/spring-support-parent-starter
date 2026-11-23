package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ChannelImportEntry;
import com.chua.starter.account.spi.ChannelImportFile;
import com.chua.starter.account.spi.ChannelImportModelDraft;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Codex CLI 配置解析（{@code config.toml} + {@code auth.json}）。
 *
 * <p>auth.json：{@code { OPENAI_API_KEY: "sk-…" }}；config.toml：根节
 * {@code model = "…"}、{@code model_provider = "…"}，以及
 * {@code [model_providers.<id>]} 节里的 {@code base_url}。指定了
 * model_provider 时优先取对应节的 base_url，否则取第一个带 base_url 的
 * model_providers 节。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("codex-cli")
public class CodexCliConfigImportParser extends AbstractChannelConfigImportParser {

    @Override
    public List<ChannelImportEntry> parse(List<ChannelImportFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("请提供 Codex CLI 的 config.toml 与 auth.json");
        }
        ChannelImportFile tomlFile = findBySuffix(files, ".toml");
        ChannelImportFile authFile = findBySuffix(files, ".json");
        if (tomlFile == null && authFile == null) {
            throw new IllegalArgumentException("未找到 config.toml 或 auth.json（请同时选择两个文件）");
        }

        String apiKey = "";
        if (authFile != null) {
            JsonNode auth = parseJsonc(new String(authFile.getBytes() == null
                    ? new byte[0] : authFile.getBytes(), java.nio.charset.StandardCharsets.UTF_8));
            apiKey = str(auth.path("OPENAI_API_KEY"));
        }

        String baseUrl = "";
        String modelId = "";
        if (tomlFile != null) {
            Map<String, Map<String, String>> toml = parseToml(new String(
                    tomlFile.getBytes() == null ? new byte[0] : tomlFile.getBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
            Map<String, String> root = toml.getOrDefault("", Map.of());
            modelId = root.getOrDefault("model", "");
            String modelProvider = root.get("model_provider");
            if (modelProvider != null && !modelProvider.isEmpty()) {
                Map<String, String> section = toml.get("model_providers." + modelProvider);
                if (section != null) {
                    baseUrl = section.getOrDefault("base_url", "");
                }
            }
            if (baseUrl.isEmpty()) {
                for (Map.Entry<String, Map<String, String>> entry : toml.entrySet()) {
                    if (entry.getKey().startsWith("model_providers.")
                            && entry.getValue().containsKey("base_url")) {
                        baseUrl = entry.getValue().get("base_url");
                        break;
                    }
                }
            }
        }

        if (baseUrl.isEmpty() && apiKey.isEmpty()) {
            return new ArrayList<>();
        }

        List<ChannelImportModelDraft> models = new ArrayList<>();
        if (!modelId.isEmpty()) {
            models.add(draft(modelId, modelId, null, null, "chat"));
        }
        String name = nameFromUrl(baseUrl);
        ChannelImportEntry entry = new ChannelImportEntry();
        entry.setKey("codex-cli:main");
        entry.setSource("codex-cli");
        entry.setName(name.isEmpty() ? "Codex CLI" : name);
        entry.setPlatform("custom");
        entry.setBaseUrl(baseUrl);
        entry.setApiKey(apiKey);
        entry.setHasKey(!apiKey.isEmpty());
        entry.setModels(models);
        if (baseUrl.isEmpty()) {
            entry.setSkip("未配置 base_url，无法导入");
        }
        return List.of(entry);
    }
}

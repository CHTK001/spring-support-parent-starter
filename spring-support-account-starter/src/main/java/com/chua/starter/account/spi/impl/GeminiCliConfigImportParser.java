package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ChannelImportEntry;
import com.chua.starter.account.spi.ChannelImportFile;
import com.chua.starter.account.spi.ChannelImportModelDraft;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini CLI settings.json 解析（best-effort）。
 *
 * <p>Gemini CLI 的 API 密钥通常存放在系统钥匙串 / OAuth 凭据文件 /
 * GEMINI_API_KEY 环境变量里，settings.json 多数只有模型名等偏好设置。
 * 本解析器尽力读取 settings.json 中可导出的字段：{@code model}（对象或字符串）、
 * {@code env.GEMINI_API_KEY / GOOGLE_API_KEY}、根级密钥与 baseUrl 字段。
 * 全部缺失时返回空清单。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("gemini-cli")
public class GeminiCliConfigImportParser extends AbstractChannelConfigImportParser {

    @Override
    public List<ChannelImportEntry> parse(List<ChannelImportFile> files) {
        JsonNode root = parseJsonc(firstText(files));
        JsonNode env = obj(root.path("env"));

        String apiKey = firstNonBlank(
                str(env.path("GEMINI_API_KEY")), str(env.path("GOOGLE_API_KEY")),
                str(root.path("GEMINI_API_KEY")), str(root.path("apiKey")));
        String baseUrl = firstNonBlank(
                str(env.path("GEMINI_BASE_URL")), str(root.path("baseUrl")),
                str(root.path("apiBaseUrl")));
        // model 字段：新版是 { name, summarizerToolName }，旧版是字符串
        JsonNode modelNode = root.path("model");
        String modelId = modelNode.isObject() ? str(modelNode.path("name")) : str(modelNode);

        if (baseUrl.isEmpty() && apiKey.isEmpty() && modelId.isEmpty()) {
            return new ArrayList<>();
        }

        List<ChannelImportModelDraft> models = new ArrayList<>();
        if (!modelId.isEmpty()) {
            models.add(draft(modelId, modelId, null, null, "chat"));
        }
        ChannelImportEntry entry = new ChannelImportEntry();
        entry.setKey("gemini-cli:main");
        entry.setSource("gemini-cli");
        entry.setName("Gemini CLI");
        entry.setPlatform("custom");
        entry.setBaseUrl(baseUrl);
        entry.setApiKey(apiKey);
        entry.setHasKey(!apiKey.isEmpty());
        entry.setModels(models);
        if (baseUrl.isEmpty()) {
            entry.setSkip("未识别到上游地址；Gemini CLI 的密钥通常存于系统钥匙串或"
                    + " GEMINI_API_KEY 环境变量，settings.json 可导出的配置有限");
        }
        return List.of(entry);
    }
}

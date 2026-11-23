package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ChannelImportEntry;
import com.chua.starter.account.spi.ChannelImportFile;
import com.chua.starter.account.spi.ChannelImportModelDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * .env 环境变量文件解析。
 *
 * <p>识别口径：出现 {@code ANTHROPIC_*} → platform=anthropic，读
 * ANTHROPIC_BASE_URL / ANTHROPIC_AUTH_TOKEN（或 ANTHROPIC_API_KEY）；否则按
 * OpenAI 兼容网关读 OPENAI_BASE_URL / OPENAI_API_BASE / API_BASE_URL / BASE_URL
 * 与 OPENAI_API_KEY / API_KEY → platform=custom。模型取 ANTHROPIC_*_MODEL /
 * OPENAI_MODEL / MODEL。地址与密钥全无 → 空清单。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("env")
public class EnvConfigImportParser extends AbstractChannelConfigImportParser {

    @Override
    public List<ChannelImportEntry> parse(List<ChannelImportFile> files) {
        Map<String, String> env = parseEnvText(firstText(files));
        boolean isAnthropic = env.containsKey("ANTHROPIC_BASE_URL")
                || env.containsKey("ANTHROPIC_AUTH_TOKEN") || env.containsKey("ANTHROPIC_API_KEY");
        String baseUrl = isAnthropic
                ? env.getOrDefault("ANTHROPIC_BASE_URL", "")
                : firstNonBlank(env.get("OPENAI_BASE_URL"), env.get("OPENAI_API_BASE"),
                        env.get("API_BASE_URL"), env.get("BASE_URL"));
        String apiKey = isAnthropic
                ? firstNonBlank(env.get("ANTHROPIC_AUTH_TOKEN"), env.get("ANTHROPIC_API_KEY"))
                : firstNonBlank(env.get("OPENAI_API_KEY"), env.get("API_KEY"));
        if (baseUrl.isEmpty() && apiKey.isEmpty()) {
            return new ArrayList<>();
        }
        String modelId = isAnthropic
                ? firstNonBlank(env.get("ANTHROPIC_DEFAULT_SONNET_MODEL"), env.get("ANTHROPIC_MODEL"))
                : firstNonBlank(env.get("OPENAI_MODEL"), env.get("MODEL"));

        List<ChannelImportModelDraft> models = new ArrayList<>();
        if (!modelId.isEmpty()) {
            models.add(draft(modelId, modelId, null, null, "chat"));
        }
        String name = nameFromUrl(baseUrl);
        ChannelImportEntry entry = new ChannelImportEntry();
        entry.setKey("env:main");
        entry.setSource("env");
        entry.setName(name.isEmpty() ? (isAnthropic ? "Anthropic（.env）" : "OpenAI 兼容（.env）") : name);
        entry.setPlatform(isAnthropic ? "anthropic" : "custom");
        entry.setBaseUrl(baseUrl);
        entry.setApiKey(apiKey);
        entry.setHasKey(!apiKey.isEmpty());
        entry.setModels(models);
        if (baseUrl.isEmpty()) {
            entry.setSkip("未配置上游地址（BASE_URL），无法导入");
        }
        return List.of(entry);
    }
}

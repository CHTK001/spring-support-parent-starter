package com.chua.starter.datasearch.provider.spi.impl;

import com.chua.starter.datasearch.provider.model.ProviderModelCatalog;
import com.chua.starter.datasearch.provider.model.ProviderModelInfo;
import com.chua.starter.datasearch.provider.spi.ProviderModelCatalogProvider;
import com.chua.starter.datasearch.provider.support.ProviderPricings;
import com.chua.starter.datasearch.provider.support.TokenRhythmPricingSource;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基元律动（TokenRhythm）模型目录实现：实时抓控制台公开模型目录。
 *
 * <p>tokenrhythm 未被 models.dev 快照收录，没有本实现时目录同步
 * （{@code POST /providers/{id}/sync}）会在「公开目录未收录该服务商」处提前返回，
 * 价格规则随之失去出口。本实现补上这条链路：模型清单、上下文窗口、能力位取自
 * {@code GET https://tokenrhythm.studio/api/models}；价格不在 USD 字段里谎报
 * （TokenRhythm 计价币种是 CNY），统一走定价规则（{@code pricing_rules}，币种 CNY）。</p>
 *
 * <p>与 models.dev 目录的口径一致：{@code streaming} / {@code asyncSupported} 不在这里
 * 下结论，留空由同步链路按 {@code ProviderProtocolCapabilities}（流式走协议、异步只认
 * 实测端点）补位。抓取失败返回 {@code null}，调用方按「未收录」处理。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
@Slf4j
public class TokenRhythmModelCatalogProvider implements ProviderModelCatalogProvider {

    /**
     * 数据源标识
     */
    private static final String SOURCE = "tokenrhythm.studio";

    /**
     * 目录展示名
     */
    private static final String DISPLAY_NAME = "基元律动";

    /**
     * 官方文档入口
     */
    private static final String DOC_URL = "https://tokenrhythm.studio/";

    @Override
    public String name() {
        return TokenRhythmPricingSource.PLATFORM;
    }

    @Override
    public boolean supports(String platform, String baseUrl) {
        return TokenRhythmPricingProvider.matches(platform, baseUrl);
    }

    @Override
    public ProviderModelCatalog query(String platform, String baseUrl) {
        if (!TokenRhythmPricingProvider.matches(platform, baseUrl)) {
            return null;
        }
        JsonNode models;
        try {
            models = TokenRhythmPricingSource.fetchModels();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("[provider] tokenrhythm 模型目录抓取失败: {}", e.toString());
            return null;
        }
        ProviderModelCatalog catalog = new ProviderModelCatalog();
        catalog.setProviderId(TokenRhythmPricingSource.PLATFORM);
        catalog.setName(DISPLAY_NAME);
        catalog.setDoc(DOC_URL);
        catalog.setSource(SOURCE);
        List<ProviderModelInfo> items = new ArrayList<>(models.size());
        for (JsonNode model : models) {
            ProviderModelInfo info = parseModel(model);
            if (info != null) {
                items.add(info);
            }
        }
        catalog.setModels(items);
        // 只读缓存（不出网）；同步链路会用 ProviderPricings.refresh 的实时结果覆盖
        catalog.setPricingRules(ProviderPricings.of(platform, baseUrl));
        return catalog;
    }

    /**
     * 解析单个模型节点。
     *
     * <p>目录里没有的字段留空，不猜值；USD 价格字段一律不填（币种是 CNY，
     * 价格经定价规则暴露）。</p>
     *
     * @param node 模型节点
     * @return 模型信息；缺 id 返回 null
     */
    private static ProviderModelInfo parseModel(JsonNode node) {
        String id = node.path("id").asText("").trim();
        if (id.isEmpty()) {
            return null;
        }
        ProviderModelInfo model = new ProviderModelInfo();
        model.setId(id);
        model.setName(node.path("name").asText(id));
        if (node.path("contextWindow").isNumber()) {
            model.setContextWindow(node.path("contextWindow").asLong());
        }
        if (node.path("maxOutputTokens").isNumber()) {
            model.setMaxOutputTokens(node.path("maxOutputTokens").asLong());
        }
        JsonNode capabilities = node.path("capabilities");
        if (capabilities.isObject()) {
            if (capabilities.path("reasoning").isBoolean()) {
                model.setReasoning(capabilities.path("reasoning").asBoolean());
            }
            if (capabilities.path("tools").isBoolean()) {
                model.setToolCall(capabilities.path("tools").asBoolean());
            }
            if (capabilities.path("vision").isBoolean()) {
                model.setAttachment(capabilities.path("vision").asBoolean());
            }
        }
        model.setInputModalities(strings(node.path("modalities")));
        return model;
    }

    /**
     * 读字符串数组。
     *
     * @param node 数组节点
     * @return 值（无有效项时为 null，表示「未收录」）
     */
    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> list = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                list.add(item.asText());
            }
        }
        return list.isEmpty() ? null : list;
    }
}

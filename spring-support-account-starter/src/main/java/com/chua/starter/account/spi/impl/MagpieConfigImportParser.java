package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ChannelImportEntry;
import com.chua.starter.account.spi.ChannelImportFile;
import com.chua.starter.account.spi.ChannelImportModelDraft;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * magpie providers.json 解析。
 *
 * <p>结构：{@code { providers: [ { id, name, key, keys?:[{key}], chat?: URL,
 * baseAPI?: "chat" | URL, website?: URL, models?: [id] | [{id,name,…}] } ] }}。
 * 没有统一 baseUrl 字段：{@code baseAPI} 是 URL 则用之；是字段名引用（如
 * "chat"）则取该字段的 URL；再退回 chat / website 字段中的 URL。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("magpie")
public class MagpieConfigImportParser extends AbstractChannelConfigImportParser {

    @Override
    public List<ChannelImportEntry> parse(List<ChannelImportFile> files) {
        JsonNode root = parseJsonc(firstText(files));
        JsonNode providers = root.path("providers");
        List<JsonNode> items = new ArrayList<>();
        if (providers.isArray()) {
            providers.forEach(items::add);
        } else if (providers.isObject()) {
            providers.fields().forEachRemaining(field -> items.add(field.getValue()));
        }

        List<ChannelImportEntry> out = new ArrayList<>();
        int index = 0;
        for (JsonNode item : items) {
            JsonNode p = obj(item);
            index++;
            String id = str(p.path("id"));
            if (id.isEmpty()) {
                id = "provider-" + index;
            }
            String name = str(p.path("name"));
            String apiKey = str(p.path("key"));
            if (apiKey.isEmpty()) {
                JsonNode keys = p.path("keys");
                if (keys.isArray() && keys.size() > 0) {
                    apiKey = str(keys.get(0).path("key"));
                }
            }

            List<ChannelImportModelDraft> models = new ArrayList<>();
            JsonNode modelsNode = p.path("models");
            if (modelsNode.isArray()) {
                for (JsonNode rawModel : modelsNode) {
                    if (rawModel == null || rawModel.isNull()) {
                        continue;
                    }
                    if (rawModel.isValueNode()) {
                        String modelId = rawModel.asText().trim();
                        if (!modelId.isEmpty()) {
                            models.add(draft(modelId, modelId, null, null, inferTask(modelId, modelId)));
                        }
                    } else if (rawModel.isObject()) {
                        String modelId = str(rawModel.path("id"));
                        if (!modelId.isEmpty()) {
                            String modelName = str(rawModel.path("name"));
                            models.add(draft(modelId, modelName.isEmpty() ? modelId : modelName,
                                    numOrNull(rawModel.path("contextWindow")),
                                    numOrNull(firstNonBlankNode(rawModel, "maxOutputTokens", "maxTokens")),
                                    inferTask(modelId, modelName, rawModel.path("input"))));
                        }
                    }
                }
            }

            String baseUrl = magpieBaseUrl(p);
            ChannelImportEntry entry = new ChannelImportEntry();
            entry.setKey("magpie:" + id);
            entry.setSource("magpie");
            entry.setName(name.isEmpty() ? id : name);
            entry.setPlatform("custom");
            entry.setBaseUrl(baseUrl);
            entry.setApiKey(apiKey);
            entry.setHasKey(!apiKey.isEmpty());
            entry.setModels(models);
            if (baseUrl.isEmpty()) {
                entry.setSkip("未配置上游地址，无法导入");
            }
            out.add(entry);
        }
        dedupeEntryNames(out);
        return out;
    }

    /**
     * magpie 条目的上游地址推断：baseAPI URL → baseAPI 字段引用 → chat → website。
     */
    private String magpieBaseUrl(JsonNode p) {
        String baseApi = str(p.path("baseAPI"));
        if (isHttpUrl(baseApi)) {
            return baseApi;
        }
        if (!baseApi.isEmpty()) {
            String viaField = str(p.path(baseApi));
            if (isHttpUrl(viaField)) {
                return viaField;
            }
        }
        String chat = str(p.path("chat"));
        if (isHttpUrl(chat)) {
            return chat;
        }
        String website = str(p.path("website"));
        if (isHttpUrl(website)) {
            return website;
        }
        return "";
    }

    /**
     * 依次取第一个非空子字段节点（供 numOrNull 等数值解析使用）。
     */
    private JsonNode firstNonBlankNode(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value != null && !value.isNull() && !value.isMissingNode()
                    && !value.asText("").trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}

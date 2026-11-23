package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ChannelImportEntry;
import com.chua.starter.account.spi.ChannelImportFile;
import com.chua.starter.account.spi.ChannelImportModelDraft;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * opencode 配置解析（{@code opencode.json} / {@code opencode.jsonc}）。
 *
 * <p>结构：{@code provider.<id>.{name,npm,options:{baseURL,apiKey},models:{<mid>:{
 * name,limit:{context,output}}}}}。npm 包名含 anthropic → Anthropic 协议，
 * 其余按 OpenAI 兼容网关（custom）处理。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("opencode")
public class OpencodeConfigImportParser extends AbstractChannelConfigImportParser {

    @Override
    public List<ChannelImportEntry> parse(List<ChannelImportFile> files) {
        JsonNode root = parseJsonc(firstText(files));
        // 语义对齐前端 root.provider ?? root.providers（仅 null/缺失才回退）
        JsonNode providerNode = root.path("provider");
        if (providerNode.isMissingNode() || providerNode.isNull()) {
            providerNode = root.path("providers");
        }
        JsonNode providers = obj(providerNode);

        List<ChannelImportEntry> out = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = providers.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String id = field.getKey();
            JsonNode p = obj(field.getValue());
            JsonNode opts = obj(p.path("options"));

            String baseUrl = firstNonBlank(
                    str(opts.path("baseURL")), str(opts.path("baseUrl")), str(opts.path("base_url")),
                    str(p.path("baseURL")), str(p.path("baseUrl")));
            String apiKey = firstNonBlank(
                    str(opts.path("apiKey")), str(opts.path("api_key")), str(p.path("apiKey")));
            String npm = str(p.path("npm")).toLowerCase();
            String platform = npm.contains("anthropic") ? "anthropic" : "custom";

            List<ChannelImportModelDraft> models = new ArrayList<>();
            JsonNode modelsNode = obj(p.path("models"));
            Iterator<Map.Entry<String, JsonNode>> modelFields = modelsNode.fields();
            while (modelFields.hasNext()) {
                Map.Entry<String, JsonNode> modelField = modelFields.next();
                String modelId = modelField.getKey();
                if (modelId == null || modelId.isEmpty()) {
                    continue;
                }
                JsonNode m = obj(modelField.getValue());
                JsonNode limit = obj(m.path("limit"));
                String modelName = str(m.path("name"));
                models.add(draft(modelId, modelName.isEmpty() ? modelId : modelName,
                        numOrNull(limit.path("context")), numOrNull(limit.path("output")),
                        inferTask(modelId, modelName)));
            }

            String name = str(p.path("name"));
            ChannelImportEntry entry = new ChannelImportEntry();
            entry.setKey("opencode:" + id);
            entry.setSource("opencode");
            entry.setName(name.isEmpty() ? id : name);
            entry.setPlatform(platform);
            entry.setBaseUrl(baseUrl);
            entry.setApiKey(apiKey);
            entry.setHasKey(!apiKey.isEmpty());
            entry.setModels(models);
            if (baseUrl.isEmpty()) {
                entry.setSkip("未配置 baseURL，无法导入");
            }
            out.add(entry);
        }
        dedupeEntryNames(out);
        return out;
    }
}

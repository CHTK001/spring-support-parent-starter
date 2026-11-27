package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ChannelImportEntry;
import com.chua.starter.account.spi.ChannelImportFile;
import com.chua.starter.account.spi.ChannelImportModelDraft;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import com.chua.common.support.utils.StringUtils;

/**
 * cc-switch SQLite（{@code cc-switch.db} / {@code .sqlite}）解析。
 *
 * <p>读取 {@code providers} 表（id / app_type / name / settings_config /
 * website_url / category）。settings_config 三种形态：</p>
 * <ul>
 *   <li>claude：{@code {env:{ANTHROPIC_AUTH_TOKEN,ANTHROPIC_BASE_URL,ANTHROPIC_DEFAULT_SONNET_MODEL}}}</li>
 *   <li>codex：{@code {auth:{OPENAI_API_KEY},config:"<TOML：model / [model_providers.*] base_url>"}}</li>
 *   <li>openclaw：{@code {baseUrl,api,models:[{id,name,contextWindow,input[]}]}}（无密钥）</li>
 * </ul>
 * <p>official 种子条目（env/auth 全空）→ skip，不导入。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("cc-switch")
public class CcSwitchConfigImportParser extends AbstractChannelConfigImportParser {

    @Override
    public List<ChannelImportEntry> parse(List<ChannelImportFile> files) {
        byte[] db = firstBytes(files);
        return withSqlite(db, this::readRows);
    }

    /**
     * 读 providers 表并转换为导入草稿。
     */
    private List<ChannelImportEntry> readRows(Connection conn) throws Exception {
        if (!tableExists(conn, "providers")) {
            throw new IllegalArgumentException("不是有效的 cc-switch 数据库：未找到 providers 表");
        }
        List<JsonNode> configs = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        List<String> apps = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, app_type, name, settings_config, website_url, category FROM providers")) {
            while (rs.next()) {
                ids.add(columnText(rs, "id"));
                apps.add(columnText(rs, "app_type"));
                names.add(columnText(rs, "name"));
                categories.add(columnText(rs, "category"));
                configs.add(safeParseObject(columnText(rs, "settings_config")));
            }
        }

        List<ChannelImportEntry> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            out.add(toEntry(ids.get(i), apps.get(i), names.get(i), categories.get(i), configs.get(i), i));
        }
        dedupeEntryNames(out);
        return out;
    }

    /**
     * providers 行 → 导入草稿（口径与前端 ccSwitchRowsToEntries 一致）。
     */
    private ChannelImportEntry toEntry(String id, String appType, String rawName,
            String category, JsonNode cfg, int index) {
        String app = appType == null ? "" : appType.toLowerCase();
        String display = StringUtils.isNotEmpty(rawName)
                && !"default".equalsIgnoreCase(rawName) ? rawName : appLabel(app);

        String baseUrl = "";
        String apiKey = "";
        String platform = "custom";
        List<ChannelImportModelDraft> models = new ArrayList<>();

        if ("claude".equals(app) || "claude-desktop".equals(app)) {
            JsonNode env = obj(cfg.path("env"));
            baseUrl = str(env.path("ANTHROPIC_BASE_URL"));
            apiKey = str(env.path("ANTHROPIC_AUTH_TOKEN"));
            platform = "anthropic";
            String modelId = str(env.path("ANTHROPIC_DEFAULT_SONNET_MODEL"));
            if (!modelId.isEmpty()) {
                models.add(draft(modelId, modelId, null, null, "chat"));
            }
        } else if ("codex".equals(app)) {
            apiKey = str(obj(cfg.path("auth")).path("OPENAI_API_KEY"));
            String toml = str(cfg.path("config"));
            baseUrl = match1(toml, "base_url\\s*=\\s*\"([^\"]+)\"");
            String modelId = match1(toml, "(^|\\n)\\s*model\\s*=\\s*\"([^\"]+)\"", 2);
            if (!modelId.isEmpty()) {
                models.add(draft(modelId, modelId, null, null, "chat"));
            }
        } else if ("openclaw".equals(app)) {
            baseUrl = firstNonBlank(str(cfg.path("baseUrl")), str(cfg.path("base_url")));
            JsonNode list = cfg.path("models");
            if (list.isArray()) {
                for (JsonNode rawModel : list) {
                    JsonNode m = obj(rawModel);
                    String modelId = str(m.path("id"));
                    if (modelId.isEmpty()) {
                        continue;
                    }
                    String modelName = str(m.path("name"));
                    models.add(draft(modelId, modelName.isEmpty() ? modelId : modelName,
                            numOrNull(m.path("contextWindow")),
                            numOrNull(m.path("maxOutputTokens").isMissingNode()
                                    ? m.path("maxTokens") : m.path("maxOutputTokens")),
                            inferTask(modelId, modelName, m.path("input"))));
                }
            }
        }

        ChannelImportEntry entry = new ChannelImportEntry();
        entry.setKey("cc-switch:" + app + ":" + (StringUtils.isNotEmpty(id) ? id
                : (StringUtils.isNotEmpty(rawName) ? rawName : String.valueOf(index))));
        entry.setSource("cc-switch");
        entry.setName(display);
        entry.setPlatform(platform);
        entry.setBaseUrl(baseUrl);
        entry.setApiKey(apiKey);
        entry.setHasKey(!apiKey.isEmpty());
        entry.setModels(models);
        String categoryLower = category == null ? "" : category.toLowerCase();
        if ("official".equals(categoryLower)) {
            entry.setSkip("官方种子条目，无需导入");
        } else if (baseUrl.isEmpty() && apiKey.isEmpty()) {
            entry.setSkip("无上游地址与密钥（空配置）");
        } else if (baseUrl.isEmpty()) {
            entry.setSkip("未配置上游地址，无法导入");
        }
        return entry;
    }

    /**
     * app_type → 展示名（条目自身没有名字时兜底）。
     */
    private String appLabel(String app) {
        if ("claude".equals(app)) {
            return "Claude";
        }
        if ("codex".equals(app)) {
            return "Codex";
        }
        if ("openclaw".equals(app)) {
            return "OpenClaw";
        }
        if ("gemini".equals(app)) {
            return "Gemini";
        }
        if ("grokbuild".equals(app)) {
            return "Grok";
        }
        return StringUtils.isEmpty(app) ? "未命名" : app;
    }
}

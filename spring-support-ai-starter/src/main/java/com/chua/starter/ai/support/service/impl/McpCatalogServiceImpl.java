package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.skill.SkillDefinition;
import com.chua.common.support.ai.skill.SkillResult;
import com.chua.common.support.datasearch.skill.spi.SkillOnlineProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.service.McpCatalogService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MCP 来源清单服务实现。
 *
 * <p>内置推荐为静态策展、自带可直连 transport（远程免鉴权或本机 npx 启动的官方
 * stdio 服务器）；在线检索实时调用已启用的 {@link SkillOnlineProvider} 检索工具，
 * 只返回真实第三方仓库引用，不缓存、不伪造。单源失败只告警，不影响其余来源。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpCatalogServiceImpl implements McpCatalogService {

    /**
     * 设置分组
     */
    private static final String GROUP = "mcp";

    /**
     * 在线检索每源最多返回条数
     */
    private static final int ONLINE_LIMIT = 20;

    private final SysAiGlobalSettingService globalSettingService;

    // ============================ 配置 ============================

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> builtin = new LinkedHashMap<>();
        builtin.put("key", "builtin");
        builtin.put("name", "内置推荐");
        builtin.put("enabled", true);
        builtin.put("count", listBuiltin().size());

        List<Map<String, Object>> online = new ArrayList<>();
        for (String src : onlineProviders().keySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", "online_" + src);
            item.put("source", src);
            item.put("name", src);
            item.put("enabled", isOnlineEnabled(src));
            online.add(item);
        }

        result.put("builtin", builtin);
        result.put("online", online);
        result.put("onlineCount", online.size());
        return result;
    }

    @Override
    public void updateConfig(Map<String, Boolean> config) {
        if (config == null) {
            return;
        }
        for (Map.Entry<String, Boolean> entry : config.entrySet()) {
            String key = entry.getKey();
            Boolean value = entry.getValue();
            if (key == null || value == null || !key.startsWith("online_")) {
                continue;
            }
            try {
                globalSettingService.upsertValue(GROUP, key, String.valueOf(value), "MCP source " + key);
            } catch (Exception e) {
                log.warn("[McpCatalog] 保存来源开关失败 key={}: {}", key, e.getMessage());
            }
        }
    }

    // ============================ 内置推荐 ============================

    @Override
    public List<Map<String, Object>> listBuiltin() {
        List<Map<String, Object>> result = new ArrayList<>();

        // 远程免鉴权 streamable HTTP，开箱即用
        result.add(builtin("deepwiki", "DeepWiki", "知识检索",
                "针对任意 GitHub 仓库提问、检索结构化知识的远程 MCP，免鉴权、开箱即用。",
                remote("https://mcp.deepwiki.com/mcp"), false, false,
                "BookSearch20Regular", "https://deepwiki.com"));

        // 本机 npx 启动的官方 stdio 服务器（需要 Node.js/npx）
        result.add(builtin("filesystem", "文件系统", "文件系统",
                "读写受授权目录内的文件；默认授权当前工作目录，可在安装后编辑目录参数。需要本机 Node.js/npx。",
                stdio("@modelcontextprotocol/server-filesystem", List.of("."), null),
                false, true, "Folder20Regular",
                "https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem"));

        result.add(builtin("fetch", "网页抓取", "网络",
                "抓取网页并转换为 Markdown 供模型使用。需要本机 Node.js/npx。",
                stdio("@modelcontextprotocol/server-fetch", List.of(), null),
                false, false, "Globe20Regular",
                "https://github.com/modelcontextprotocol/servers/tree/main/src/fetch"));

        result.add(builtin("memory", "持久记忆", "记忆",
                "基于知识图谱的持久化长期记忆。需要本机 Node.js/npx。",
                stdio("@modelcontextprotocol/server-memory", List.of(), null),
                false, false, "Lightbulb20Regular",
                "https://github.com/modelcontextprotocol/servers/tree/main/src/memory"));

        result.add(builtin("sequential-thinking", "顺序思考", "推理",
                "分步式顺序思考与复杂问题分解。需要本机 Node.js/npx。",
                stdio("@modelcontextprotocol/server-sequential-thinking", List.of(), null),
                false, false, "NumberSymbol20Regular",
                "https://github.com/modelcontextprotocol/servers/tree/main/src/sequentialthinking"));

        result.add(builtin("time", "时间时区", "工具",
                "时间查询与时区转换。需要本机 Node.js/npx。",
                stdio("@modelcontextprotocol/server-time", List.of(), null),
                false, false, "Clock20Regular",
                "https://github.com/modelcontextprotocol/servers/tree/main/src/time"));

        result.add(builtin("everything", "功能演示", "测试",
                "MCP 官方功能演示 / 连通性测试服务器。需要本机 Node.js/npx。",
                stdio("@modelcontextprotocol/server-everything", List.of(), null),
                false, false, "Star20Regular", "https://github.com/modelcontextprotocol/servers"));

        result.add(builtin("brave-search", "Brave 搜索", "知识检索",
                "通过 Brave Search 检索全网，需要 Brave API Key（安装后在传输环境变量 BRAVE_API_KEY 中配置）。",
                stdio("@modelcontextprotocol/server-brave-search", List.of(), Map.of()),
                true, false, "Search20Regular",
                "https://github.com/modelcontextprotocol/servers/tree/main/src/brave-search"));

        return result;
    }

    // ============================ 在线检索 ============================

    @Override
    public List<Map<String, Object>> searchOnline(String keyword) {
        List<Map<String, Object>> result = new ArrayList<>();
        String kw = keyword == null ? "" : keyword.trim();

        for (Map.Entry<String, SkillOnlineProvider> entry : onlineProviders().entrySet()) {
            String src = entry.getKey();
            if (!isOnlineEnabled(src)) {
                continue;
            }
            SkillOnlineProvider provider = entry.getValue();
            try {
                List<SkillDefinition> tools = provider.search(kw);
                if (tools == null) {
                    continue;
                }
                for (SkillDefinition tool : tools) {
                    if (tool.getName() == null
                            || !tool.getName().toLowerCase(Locale.ROOT).contains("search")) {
                        continue;
                    }
                    Map<String, Object> args = new LinkedHashMap<>();
                    args.put("search", kw);
                    args.put("limit", ONLINE_LIMIT);
                    args.put("offset", 0);
                    SkillResult res = tool.execute(args);
                    if (!res.isSuccess() || !(res.getContent() instanceof Map<?, ?> content)) {
                        continue;
                    }
                    if (!(content.get("items") instanceof List<?> items)) {
                        continue;
                    }
                    for (Object o : items) {
                        if (!(o instanceof Map<?, ?> it)) {
                            continue;
                        }
                        String owner = str(it.get("repoOwner"));
                        String repo = str(it.get("repoName"));
                        Map<String, Object> card = new LinkedHashMap<>();
                        card.put("id", "online_" + src + "_" + str(it.get("key")));
                        card.put("name", str(it.get("name")));
                        card.put("description", "来自 " + src + " 的第三方 MCP（GitHub: "
                                + owner + "/" + repo + "），源侧未提供可直连配置，请参考仓库说明手动配置传输。");
                        card.put("source", src);
                        card.put("installable", false);
                        card.put("builtin", false);
                        card.put("docsUrl", it.get("readmeUrl"));
                        card.put("labels", List.of("第三方仓库"));
                        result.add(card);
                    }
                }
            } catch (Exception e) {
                log.warn("[McpCatalog] 在线源 {} 检索失败: {}", src, e.getMessage());
            }
        }
        return result;
    }

    // ============================ transport 构造 ============================

    /**
     * 构造远程 streamable HTTP transport。
     * @param url 字符串
     * @return 返回值
    */
    private static Map<String, Object> remote(String url) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("type", "streamable_http");
        t.put("url", url);
        return t;
    }

    /**
     * 构造本机 stdio transport（npx -y pkg ...）。Windows 使用 npx.cmd 以保证可被进程解析。
     * @param Map<String 参数
     * @param env 参数
     * @param extra 参数
     * @param pkg 字符串
     * @return 返回值
    */
    private Map<String, Object> stdio(String pkg, List<String> extra, Map<String, String> env) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("type", "stdio");
        t.put("command", isWindows() ? "npx.cmd" : "npx");
        List<String> args = new ArrayList<>();
        args.add("-y");
        args.add(pkg);
        args.addAll(extra);
        t.put("args", args);
        if (env != null && !env.isEmpty()) {
            t.put("env", env);
        }
        return t;
    }

    /**
     * 构造一条内置推荐卡片。
     */
        private static Map<String, Object> builtin(String id, String name, String category,
                                               String description, Map<String, Object> transport,
                                               boolean requiresKey, boolean requiresConfig,
                                               String icon, String docs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "builtin_" + id);
        m.put("name", name);
        m.put("description", description);
        m.put("category", category);
        m.put("labels", List.of(category));
        m.put("transport", transport);
        m.put("builtin", true);
        m.put("installable", true);
        m.put("official", true);
        m.put("requiresKey", requiresKey);
        m.put("requiresConfig", requiresConfig);
        if (icon != null) {
            m.put("icon", icon);
        }
        if (docs != null) {
            m.put("docs", docs);
        }
        return m;
    }

    // ============================ 开关 / SPI ============================

    /**
     * is Online Enabled
     * @param src 字符串
     * @return 返回值
    */
    private boolean isOnlineEnabled(String src) {
        return globalSettingService.getBoolean(GROUP, "online_" + src, false);
    }

    /**
     * online Providers
     * @return 返回值
    */
    private Map<String, SkillOnlineProvider> onlineProviders() {
        try {
            Map<String, SkillOnlineProvider> map =
                    ServiceProvider.of(SkillOnlineProvider.class).list();
            return map != null ? map : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("[McpCatalog] 加载在线来源失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * is Windows
     * @return 返回值
    */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * str
     * @param o 对象
     * @return 返回值
    */
    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}

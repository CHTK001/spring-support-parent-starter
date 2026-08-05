package com.chua.starter.datasource.controller;

import com.chua.common.support.lang.document.DocumentData;
import com.chua.common.support.lang.document.DocumentTemplate;
import com.chua.common.support.lang.document.DocumentTemplateType;
import com.chua.common.support.lang.document.TableData;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.datasource.properties.DocumentProperties;
import com.chua.starter.datasource.support.DataSourceDocumentService;
import com.chua.starter.datasource.support.graph.DatabaseGraphService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源文档预览与图谱接口
 * <p>
 * 默认暴露路径：{@code /plugin/datasource/document/**}，可通过
 * {@code plugin.datasource.document.base-path} 修改。
 * </p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *     <li>{@code GET  /preview}     渲染整库文档 HTML；优先返回缓存</li>
 *     <li>{@code POST /apply}       强制刷新缓存并返回新文档 HTML</li>
 *     <li>{@code GET  /list}        返回文档元信息 JSON（数据库名、表数、最后刷新时间等）</li>
 *     <li>{@code GET  /table/{name}} 渲染单表详情 HTML</li>
 *     <li>{@code GET  /relationship.json}  返回表关系图 JSON（节点+边，扁平）</li>
 *     <li>{@code GET  /graph.json}  返回数据库关系图谱 JSON（relation-graph 兼容：节点含触发器/存储过程 data）</li>
 *     <li>{@code GET  /graph.html}  返回内嵌 relation-graph 的可视化 HTML 预览</li>
 *     <li>{@code GET  /graph/table/{name}.json} 返回单表汇总（列+触发器+关联存储过程）</li>
 *     <li>{@code POST /graph/apply} 强制重建图谱缓存并返回 graph.json</li>
 * </ul>
 *
 * @author CH
 * @since 2026/8/5
 */
@Slf4j
public class DataSourceDocumentController {

    private final DataSourceDocumentService service;
    private final DocumentProperties properties;

    /**
     * 可选的图谱服务（仅当图谱相关 Bean 装配时存在）
     */
    private final DatabaseGraphService graphService;

    public DataSourceDocumentController(DataSourceDocumentService service, DocumentProperties properties) {
        this(service, properties, null);
    }

    public DataSourceDocumentController(DataSourceDocumentService service,
                                        DocumentProperties properties,
                                        DatabaseGraphService graphService) {
        this.service = service;
        this.properties = properties;
        this.graphService = graphService;
    }

    /**
     * 渲染整库 HTML，优先使用缓存
     */
    public void preview(HttpServletResponse response) throws IOException {
        DocumentData data = service.preview();
        writeHtml(response, renderHtml(data, null));
    }

    /**
     * 强制刷新后渲染整库 HTML
     */
    public void apply(HttpServletResponse response) throws IOException {
        DocumentData data = service.apply();
        writeHtml(response, renderHtml(data, null));
    }

    /**
     * 文档元信息 JSON
     */
    public void list(HttpServletResponse response) throws IOException {
        DocumentData data = service.preview();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("database", data.getDatabaseName());
        body.put("product", data.getProductName() + " " + data.getProductVersion());
        body.put("url", data.getUrl());
        body.put("title", data.getTitle());
        body.put("version", data.getVersion());
        body.put("tableCount", data.getTables() == null ? 0 : data.getTables().size());
        body.put("cachedAt", service.getCachedAt());
        body.put("hasCache", service.hasCache());

        List<Map<String, Object>> tables = new java.util.ArrayList<>();
        if (data.getTables() != null) {
            for (TableData t : data.getTables()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("schema", t.getSchema());
                item.put("name", t.getTableName());
                item.put("remark", t.getRemark());
                item.put("type", t.getType());
                item.put("columns", t.getColumns() == null ? 0 : t.getColumns().size());
                tables.add(item);
            }
        }
        body.put("tables", tables);

        writeJson(response, body);
    }

    /**
     * 渲染单表详情 HTML
     * <p>从 request URI 末段解析表名（避免 path variable 绑定问题）</p>
     */
    public void table(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) throws IOException {
        String tableName = lastPathSegment(request);
        if (tableName.isEmpty()) {
            writeNotFound(response, "未指定表名");
            return;
        }
        DocumentData data = service.preview();
        if (data.getTables() == null) {
            writeNotFound(response, "未找到表: " + tableName);
            return;
        }
        TableData target = null;
        for (TableData t : data.getTables()) {
            if (tableName.equalsIgnoreCase(t.getTableName())) {
                target = t;
                break;
            }
        }
        if (target == null) {
            writeNotFound(response, "未找到表: " + tableName);
            return;
        }
        writeHtml(response, renderHtml(data, target));
    }

    /**
     * 表关系图 JSON（节点 + 边）
     */
    public void relationshipJson(HttpServletResponse response) throws IOException {
        DocumentData data = service.preview();
        writeJson(response, com.chua.common.support.lang.document.DocumentProvider.toRelationshipJson(data));
    }

    // ==================== 数据库关系图谱 ====================

    /**
     * 返回 relation-graph 兼容的 JSON（图 + 节点 data 携带触发器/存储过程）
     */
    public void graphJson(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ensureGraphService();
        DatabaseGraphService.GraphPayload payload = isForceRefresh(request) ? graphService.apply() : graphService.preview();
        writeJson(response, payload);
    }

    /**
     * 内嵌 relation-graph 的 HTML 可视化预览页
     */
    public void graphHtml(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ensureGraphService();
        DatabaseGraphService.GraphPayload payload = isForceRefresh(request) ? graphService.apply() : graphService.preview();
        String json = com.chua.common.support.lang.json.Json.toJson(payload);
        writeHtml(response, renderGraphHtml(json, payload));
    }

    /**
     * 单表汇总：列 + 触发器 + 关联存储过程
     * <p>从 request URI 末段解析表名（避免 path variable 绑定问题）</p>
     */
    public void graphTable(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) throws IOException {
        ensureGraphService();
        // 解析 URI：/plugin/datasource/document/graph/table/SYS_USER.json -> SYS_USER
        String pathInfo = request.getRequestURI();
        String suffix = ".json";
        int idx = pathInfo.lastIndexOf('/');
        if (idx < 0 || idx == pathInfo.length() - 1) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJson(response, Map.of("error", "Invalid path: " + pathInfo));
            return;
        }
        String tableName = pathInfo.substring(idx + 1);
        if (tableName.endsWith(suffix)) {
            tableName = tableName.substring(0, tableName.length() - suffix.length());
        }
        if (tableName.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJson(response, Map.of("error", "Table name is empty"));
            return;
        }
        DatabaseGraphService.GraphPayload payload = graphService.preview();
        for (Map<String, Object> node : payload.nodes) {
            if (tableName.equalsIgnoreCase(String.valueOf(node.get("id")))) {
                Map<String, Object> data = (Map<String, Object>) node.get("data");
                if (data == null) {
                    data = new LinkedHashMap<>();
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("name", node.get("id"));
                body.put("text", node.get("text"));
                body.putAll(data);
                writeJson(response, body);
                return;
            }
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        writeJson(response, Map.of("error", "Table not found: " + tableName));
    }

    /**
     * 强制重建图谱缓存
     */
    public void graphApply(HttpServletResponse response) throws IOException {
        ensureGraphService();
        DatabaseGraphService.GraphPayload payload = graphService.apply();
        writeJson(response, payload);
    }

    private void ensureGraphService() {
        if (graphService == null) {
            throw new IllegalStateException(
                    "图谱服务未启用：classpath 缺少 utils-support-datasource-starter 或触发器/存储过程读取能力不可用");
        }
    }

    private boolean isForceRefresh(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String refresh = request.getParameter("refresh");
        return "1".equals(refresh) || "true".equalsIgnoreCase(refresh);
    }

    /**
     * 渲染 relation-graph 可视化 HTML（CDN 内嵌，零构建）
     */
    private String renderGraphHtml(String json, DatabaseGraphService.GraphPayload payload) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<title>数据库关系图谱</title>")
                .append("<script src=\"https://unpkg.com/vue@3.4.21/dist/vue.global.prod.js\"></script>")
                .append("<script src=\"https://unpkg.com/@relation-graph/vue3@3.0.13/dist/index.umd.js\"></script>")
                .append("<style>")
                .append("body{margin:0;font-family:-apple-system,Segoe UI,sans-serif;background:#0f172a;color:#e2e8f0}")
                .append(".header{padding:12px 20px;border-bottom:1px solid #1e293b;background:#1e293b;display:flex;justify-content:space-between;align-items:center}")
                .append(".header h1{font-size:16px;margin:0;font-weight:600}")
                .append(".meta{font-size:12px;color:#94a3b8}")
                .append(".actions button{margin-left:8px;padding:4px 12px;background:#334155;color:#e2e8f0;border:1px solid #475569;border-radius:4px;cursor:pointer;font-size:12px}")
                .append(".actions button:hover{background:#475569}")
                .append("#graph{width:100vw;height:calc(100vh - 50px);background:#0f172a}")
                .append(".node-card{width:100%;height:100%;box-sizing:border-box;padding:8px 10px;border-radius:6px;")
                .append("background:#1e293b;border:1px solid #334155;color:#e2e8f0;font-size:12px;display:flex;flex-direction:column;gap:4px}")
                .append(".node-card.has-fk{border-color:#3b82f6}")
                .append(".node-card.is-root{border-color:#f59e0b;background:#422006}")
                .append(".node-title{font-weight:700;font-size:13px;color:#f8fafc;display:flex;align-items:center;gap:6px}")
                .append(".node-meta{font-size:10px;color:#94a3b8;display:flex;gap:8px;flex-wrap:wrap}")
                .append(".node-section{margin-top:4px;border-top:1px dashed #334155;padding-top:4px;font-size:11px}")
                .append(".trigger{color:#f87171}")
                .append(".procedure{color:#60a5fa}")
                .append(".badge{display:inline-block;padding:0 6px;border-radius:3px;font-size:10px;background:#334155;color:#cbd5e1}")
                .append(".pk{color:#fbbf24}")
                .append("</style></head><body>")

                .append("<div class=\"header\">")
                .append("<div><h1>数据库关系图谱</h1>")
                .append("<div class=\"meta\">")
                .append(payload.nodes.size()).append(" 张表 · ")
                .append(payload.lines.size()).append(" 条外键边 · ")
                .append(payload.triggerCount).append(" 个触发器 · ")
                .append(payload.procedureCount).append(" 个存储过程")
                .append("</div></div>")
                .append("<div class=\"actions\">")
                .append("<button onclick=\"window.location.href='graph.html?refresh=1'\">强制刷新</button>")
                .append("<button onclick=\"window.open('graph.json','_blank')\">查看 JSON</button>")
                .append("</div></div>")

                .append("<div id=\"graph\"></div>")

                .append("<script>")
                .append("const graphData = ").append(json).append(";")
                .append("const { createApp, ref } = Vue;")
                .append("const RG = RelationGraphVue3;")
                .append("const App = createApp({")
                .append("  components: { RelationGraph: RG.RelationGraph },")
                .append("  template: '<RelationGraph ref=\"rgRef\" :options=\"options\" :graph-json-data=\"graphData\" @node-click=\"onNodeClick\" />',")
                .append("  setup(){")
                .append("    const rgRef = ref(null);")
                .append("    const options = {")
                .append("      defaultNodeShape: 0,")
                .append("      defaultNodeWidth: 180, defaultNodeHeight: 80,")
                .append("      defaultLineShape: 1, defaultLineTextOffsetY: -8,")
                .append("      defaultNodeBorderWidth: 1, defaultNodeColor: '#1e293b',")
                .append("      layouts: [{ layoutName: 'force', layoutClassName: 'force' }],")
                .append("      layout: { layoutName: 'force' }")
                .append("    };")
                .append("    const onNodeClick = (node, e) => {")
                .append("      const data = node.data || {};")
                .append("      console.log('node click', node.id, data);")
                .append("    };")
                .append("    setTimeout(() => { try { rgRef.value && rgRef.value.zoomToFit(); } catch(_){} }, 800);")
                .append("    return { rgRef, options, graphData, onNodeClick };")
                .append("  }")
                .append("});")
                .append("app = App.mount('#graph');")
                .append("</script>")
                .append("</body></html>");
        return sb.toString();
    }

    // ==================== 渲染辅助 ====================

    private String renderHtml(DocumentData data, TableData single) {
        try {
            DocumentTemplate template = resolveTemplate();
            if (template == null) {
                return fallbackHtml(data, single);
            }
            if (single != null) {
                // 单表：构造一个只含单表的 DocumentData
                DocumentData singleData = DocumentData.builder()
                        .databaseName(data.getDatabaseName())
                        .productName(data.getProductName())
                        .productVersion(data.getProductVersion())
                        .url(data.getUrl())
                        .title(data.getTitle() + " - " + single.getTableName())
                        .description("表 " + single.getTableName() + " 详情")
                        .version(data.getVersion())
                        .tables(new java.util.ArrayList<>(List.of(single)))
                        .build();
                return template.renderHtml(singleData, null);
            }
            return template.renderHtml(data, null);
        } catch (Throwable t) {
            log.warn("渲染 HTML 失败，回退到内置简单模板", t);
            return fallbackHtml(data, single);
        }
    }

    /**
     * 解析模板：优先按用户配置名（如 SWAGGER）查找，找不到回退 DEFAULT
     */
    private DocumentTemplate resolveTemplate() {
        try {
            DocumentTemplateType type = DocumentTemplateType.valueOf(properties.getTemplate().toUpperCase());
            return DocumentTemplate.create(type);
        } catch (Throwable t) {
            log.debug("模板 [{}] 不可用，回退 DEFAULT", properties.getTemplate());
            try {
                return DocumentTemplate.create(DocumentTemplateType.DEFAULT);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /**
     * 内置最简 HTML 模板（兜底，不依赖任何三方模板）
     */
    private String fallbackHtml(DocumentData data, TableData single) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>")
                .append(escape(data.getTitle()))
                .append("</title><style>")
                .append("body{font-family:-apple-system,Segoe UI,sans-serif;max-width:1200px;margin:24px auto;padding:0 16px;color:#1f2937}")
                .append("h1{font-size:24px;border-bottom:2px solid #e5e7eb;padding-bottom:8px}")
                .append("h2{font-size:18px;margin-top:32px;color:#374151}")
                .append("table{border-collapse:collapse;width:100%;margin-top:8px;font-size:13px}")
                .append("th,td{border:1px solid #e5e7eb;padding:6px 10px;text-align:left}")
                .append("th{background:#f9fafb;font-weight:600}")
                .append("tr:nth-child(even){background:#fafafa}")
                .append(".meta{color:#6b7280;font-size:13px;margin:8px 0 24px}")
                .append(".pk{color:#2563eb;font-weight:600}")
                .append("</style></head><body>");

        sb.append("<h1>").append(escape(data.getTitle())).append("</h1>");
        sb.append("<div class=\"meta\">")
                .append("数据库: ").append(escape(nullToEmpty(data.getDatabaseName()))).append(" | ")
                .append("产品: ").append(escape(nullToEmpty(data.getProductName()))).append(" ")
                .append(escape(nullToEmpty(data.getProductVersion()))).append(" | ")
                .append("刷新: ").append(service.getCachedAt() == 0 ? "未缓存" : new java.util.Date(service.getCachedAt()))
                .append("</div>");

        if (data.getTables() == null || data.getTables().isEmpty()) {
            sb.append("<p>暂无表</p>");
        } else {
            // 索引
            if (single == null) {
                sb.append("<h2>表清单</h2><ul>");
                for (TableData t : data.getTables()) {
                    String name = t.getTableName();
                    sb.append("<li><a href=\"table/").append(escape(name)).append("\">")
                            .append(escape(name)).append("</a>");
                    if (t.getRemark() != null && !t.getRemark().isEmpty()) {
                        sb.append(" — ").append(escape(t.getRemark()));
                    }
                    sb.append("</li>");
                }
                sb.append("</ul>");
            }

            // 表详情
            List<TableData> showList = single == null ? data.getTables() : List.of(single);
            for (TableData t : showList) {
                sb.append("<h2>").append(escape(t.getTableName()));
                if (t.getRemark() != null && !t.getRemark().isEmpty()) {
                    sb.append(" <small style=\"color:#6b7280\">").append(escape(t.getRemark())).append("</small>");
                }
                sb.append("</h2>");

                if (t.getColumns() == null || t.getColumns().isEmpty()) {
                    sb.append("<p><em>无列</em></p>");
                    continue;
                }
                sb.append("<table><thead><tr><th>#</th><th>列名</th><th>类型</th><th>可空</th><th>默认</th><th>注释</th></tr></thead><tbody>");
                for (com.chua.common.support.lang.document.ColumnData c : t.getColumns()) {
                    sb.append("<tr>");
                    sb.append("<td>").append(c.getOrdinalPosition()).append("</td>");
                    sb.append("<td").append(c.isPrimaryKey() ? " class=\"pk\"" : "").append(">")
                            .append(escape(c.getColumnName()))
                            .append(c.isPrimaryKey() ? " 🔑" : "")
                            .append("</td>");
                    sb.append("<td>").append(escape(typeDesc(c))).append("</td>");
                    sb.append("<td>").append(c.isNullable() ? "YES" : "NO").append("</td>");
                    sb.append("<td>").append(escape(nullToEmpty(c.getDefaultValue()))).append("</td>");
                    sb.append("<td>").append(escape(nullToEmpty(c.getRemark()))).append("</td>");
                    sb.append("</tr>");
                }
                sb.append("</tbody></table>");
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private String typeDesc(com.chua.common.support.lang.document.ColumnData c) {
        String name = c.getTypeName();
        if (name == null) {
            return "";
        }
        if (c.getColumnSize() > 0) {
            String upper = name.toUpperCase();
            if (upper.contains("DECIMAL") || upper.contains("NUMERIC") || upper.contains("NUMBER")) {
                if (c.getDecimalDigits() != null && c.getDecimalDigits() > 0) {
                    return name + "(" + c.getColumnSize() + "," + c.getDecimalDigits() + ")";
                }
                return name + "(" + c.getColumnSize() + ")";
            }
            if (upper.contains("CHAR") || upper.contains("VARCHAR") || upper.contains("TEXT")) {
                return name + "(" + c.getColumnSize() + ")";
            }
        }
        return name;
    }

    private void writeHtml(HttpServletResponse response, String html) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter w = response.getWriter()) {
            w.write(html);
            w.flush();
        }
    }

    private void writeJson(HttpServletResponse response, Object body) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter w = response.getWriter()) {
            w.write(Json.toJson(body));
            w.flush();
        }
    }

    private void writeNotFound(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8");
        try (PrintWriter w = response.getWriter()) {
            w.write("<!DOCTYPE html><html><body><h1>404</h1><p>" + escape(msg) + "</p></body></html>");
            w.flush();
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 从 request URI 中提取最后一段路径
     */
    private static String lastPathSegment(jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        int slash = uri.lastIndexOf('/');
        if (slash < 0 || slash == uri.length() - 1) {
            return "";
        }
        return uri.substring(slash + 1);
    }
}

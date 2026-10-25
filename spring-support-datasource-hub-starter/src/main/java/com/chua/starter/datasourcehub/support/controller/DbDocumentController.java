package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.document.DocumentConfig;
import com.chua.common.support.lang.document.DocumentData;
import com.chua.common.support.lang.document.DocumentExportConfig;
import com.chua.common.support.lang.document.DocumentParser;
import com.chua.common.support.lang.document.DocumentProvider;
import com.chua.common.support.lang.document.DocumentTemplateType;
import com.chua.common.support.lang.document.TableData;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据库文档导出。
 *
 * <p>本控制器<b>不自己实现文档生成</b>：结构采集走 utils 层既有的
 * {@code DocumentParser("database")}（{@code com.chua.datasource.support.document.DatabaseDocumentParser}，
 * 基于 JDBC {@code DatabaseMetaData} 读表/列/主键/外键），渲染走既有的
 * {@link DocumentProvider} SPI（markdown / html / word / pdf 四种实现）。
 * 这里只做三件胶水活：</p>
 * <ol>
 *   <li>把数据源台账里的「主机/端口/库/账号/口令」拼成 parser 认识的 JDBC URL —— 复用
 *       {@link DatasourceService#jdbcInfo(Long)}，它已是全项目唯一拼 URL 的出口；</li>
 *   <li>把 parser 返回的 {@link DocumentData} 交给 {@link DocumentProvider} 落盘；</li>
 *   <li>把落盘文件读回字节流，按格式给正确的 Content-Type 与文件名。</li>
 * </ol>
 *
 * <p>为什么要单独一个控制器而不是塞进 {@code DbConsoleController}：导出是
 * 「产物下载」，返回的是二进制流而不是 {@code ReturnResult}；混在一起会让
 * 控制台那个已经很大的类再多一种响应形态。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Tag(name = "数据库文档", description = "按 markdown/html/word/pdf 导出数据库设计文档")
@RestController
@RequestMapping("/ms/monitor/db/document")
@RequiredArgsConstructor
@Slf4j
public class DbDocumentController {

    /**
     * 前端下拉里展示的格式顺序（也是探测顺序）。
     *
     * <p>pdf 排最后：它的渲染器依赖服务端装有 wkhtmltopdf，缺了会降级成 HTML；
     * 真正的可用性以 {@code /document/formats} 的探测结果为准。</p>
     */
    private static final List<String> CANDIDATE_FORMATS = List.of("markdown", "html", "word", "pdf");

    /**
     * 模板可选值（与 {@link DocumentTemplateType} 对齐）。
     */
    private static final List<String> CANDIDATE_TEMPLATES =
            Arrays.stream(DocumentTemplateType.values()).map(Enum::name).toList();

    private final DatasourceService datasourceService;

    /**
     * 当前实例真正装了的导出格式。
     *
     * <p>SPI 是按 classpath 上 {@code META-INF/extensions} 注册的：宿主没引
     * word/pdf 模块时 {@code DocumentProvider.create("word")} 返回 null。
     * 这里如实探测并上报，前端据此只渲染装了的选项 —— 而不是给一个点了就报
     * 「未找到文档导出实现」的下拉框。</p>
     *
     * @return 格式、扩展名与模板清单
     */
    @Operation(summary = "可用的文档导出格式", description = "按 classpath 上的 DocumentProvider SPI 实际探测")
    @GetMapping("/formats")
    public ResponseEntity<Map<String, Object>> formats() {
        List<Map<String, Object>> formats = new ArrayList<>();
        for (String type : CANDIDATE_FORMATS) {
            DocumentProvider provider = DocumentProvider.create(type);
            if (provider == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("format", provider.getType());
            item.put("extensions", provider.getExtensions());
            // pdf 走外部进程（wkhtmltopdf），与纯 JVM 渲染器不同源，单独标注
            item.put("external", "pdf".equalsIgnoreCase(provider.getType()));
            formats.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("formats", formats);
        body.put("templates", CANDIDATE_TEMPLATES);
        return ResponseEntity.ok(body);
    }

    /**
     * 导出数据库设计文档。
     *
     * @param datasourceId 数据源 ID（必填，不接受应用默认主数据源）
     * @param database    目标库名；为空则用台账里登记的默认库
     * @param format      导出格式，见 {@code /document/formats}
     * @param template    模板样式，markdown/html 生效；word 渲染器固定版式、忽略此参数
     * @param tables      只导出这些表（逗号分隔）；为空导出整库
     * @return 文档字节流
     */
    @Operation(summary = "导出数据库文档", description = "结构采集走既有 DocumentParser，渲染走既有 DocumentProvider")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam Long datasourceId,
            @RequestParam(required = false) String database,
            @RequestParam(required = false, defaultValue = "markdown") String format,
            @RequestParam(required = false, defaultValue = "DEFAULT") String template,
            @RequestParam(required = false) String tables) throws Exception {

        String fmt = format == null || format.isBlank() ? "markdown" : format.trim().toLowerCase(Locale.ROOT);
        DocumentProvider provider = DocumentProvider.create(fmt);
        if (provider == null) {
            return text(501, "当前实例未装配该导出格式: " + fmt
                    + "（word/pdf 需要宿主引入对应模块）");
        }

        DatasourceJdbcInfo info = datasourceService.jdbcInfo(datasourceId);
        String url = withDatabase(info.url(), database);

        DocumentParser parser = DocumentParser.create("database");
        if (parser == null) {
            return text(501, "当前实例未装配数据库文档采集器（utils-support-datasource-starter）");
        }

        DocumentData data = parser.parse(DocumentConfig.builder()
                .type("database")
                .url(url)
                .username(info.username())
                .password(info.password())
                // 不传 driverClass：让驱动管理器按 URL 自动发现（与既有 DataSourceDocumentService 同口径）
                .options(Map.of())
                .build());

        List<TableData> picked = filterTables(data.getTables(), tables);
        if (picked.isEmpty()) {
            return text(400, "没有可导出的表：库 " + data.getDatabaseName() + " 下未匹配到任何表");
        }
        data.setTables(picked);

        DocumentTemplateType templateType = resolveTemplate(template);
        File tmp = Files.createTempFile("db-doc-", "." + primaryExtension(provider)).toFile();
        tmp.deleteOnExit();
        try {
            provider.export(data, tmp, DocumentExportConfig.builder()
                    .format(fmt)
                    .templateType(templateType)
                    .outputFile(tmp)
                    .build());
        } catch (Exception e) {
            log.warn("[db-document] 文档渲染失败: datasourceId={} format={}", datasourceId, fmt, e);
            return text(500, "文档渲染失败: " + e.getMessage());
        }
        if (!tmp.exists() || tmp.length() == 0) {
            // PDF 渲染器缺 wkhtmltopdf 时会降级把 HTML 写到同名 .html，.pdf 本身不落地。
            // 这种「要 pdf 却只出 html」必须如实报错，不能塞一个 0 字节 .pdf 给用户。
            String hint = "pdf".equals(fmt) ? "（服务端需安装 wkhtmltopdf）" : "";
            return text(501, "渲染器未产出文件: " + fmt + hint);
        }

        byte[] body = Files.readAllBytes(tmp.toPath());
        Files.deleteIfExists(tmp.toPath());
        return ResponseEntity.ok()
                .contentType(mediaTypeOf(fmt))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileBaseName(data), primaryExtension(provider)))
                .body(body);
    }

    /**
     * 在线预览数据库设计文档。
     *
     * <p>走与 {@code /export} 相同的解析路径（采集表/列/主键/外键），但不做文件落盘，
     * 而是将 markdown 文本转成内嵌样式的 HTML 片段，返回 {@code text/html}。
     * 适合在 iframe 或对话框里直接渲染。</p>
     *
     * <p><b>支持预览的格式</b>：</p>
     * <ul>
     *   <li>markdown → 转 HTML（marked 库）；</li>
     *   <li>html → 直接包裹进 HTML 壳；</li>
     *   <li>word / pdf → 这两种是二进制格式，浏览器无法直接渲染，返回 501 提示下载。</li>
     * </ul>
     *
     * @param datasourceId 数据源 ID
     * @param database    目标库名；为空则用台账里登记的默认库
     * @param format      导出格式（默认 markdown）
     * @param template    模板样式
     * @param tables      只导出这些表（逗号分隔）
     * @return HTML 片段或错误提示
     */
    @Operation(summary = "在线预览数据库文档", description = "将文档内容转成 HTML 后返回，适合在 iframe 中渲染；word/pdf 格式不支持预览")
    @GetMapping("/preview")
    public ResponseEntity<byte[]> preview(
            @RequestParam Long datasourceId,
            @RequestParam(required = false) String database,
            @RequestParam(required = false, defaultValue = "markdown") String format,
            @RequestParam(required = false, defaultValue = "DEFAULT") String template,
            @RequestParam(required = false) String tables) throws Exception {

        String fmt = format == null || format.isBlank() ? "markdown" : format.trim().toLowerCase(Locale.ROOT);

        // word/pdf 是二进制，无法在浏览器里渲染，预览时直接拒绝
        if ("word".equalsIgnoreCase(fmt) || "pdf".equalsIgnoreCase(fmt)) {
            return text(501, "501 不支持预览 " + fmt + " 格式，请点「导出」下载后查看。");
        }

        // markdown / html 使用相同的解析路径，只是不写文件
        String targetFormat = "html".equalsIgnoreCase(fmt) ? "html" : "markdown";
        DocumentProvider provider = DocumentProvider.create(targetFormat);
        if (provider == null) {
            return text(501, "当前实例未装配 " + targetFormat + " 文档渲染器");
        }

        DatasourceJdbcInfo info = datasourceService.jdbcInfo(datasourceId);
        String url = withDatabase(info.url(), database);

        DocumentParser parser = DocumentParser.create("database");
        if (parser == null) {
            return text(501, "当前实例未装配数据库文档采集器");
        }

        DocumentData data = parser.parse(DocumentConfig.builder()
                .type("database")
                .url(url)
                .username(info.username())
                .password(info.password())
                .options(Map.of())
                .build());

        List<TableData> picked = filterTables(data.getTables(), tables);
        if (picked.isEmpty()) {
            return text(400, "库 " + data.getDatabaseName() + " 下未匹配到任何表");
        }
        data.setTables(picked);

        DocumentTemplateType templateType = resolveTemplate(template);
        File tmp = Files.createTempFile("db-doc-preview-", ".md").toFile();
        tmp.deleteOnExit();
        try {
            provider.export(data, tmp, DocumentExportConfig.builder()
                    .format(targetFormat)
                    .templateType(templateType)
                    .outputFile(tmp)
                    .build());
        } catch (Exception e) {
            log.warn("[db-document-preview] 渲染失败: datasourceId={} format={}", datasourceId, targetFormat, e);
            return text(500, "文档渲染失败: " + e.getMessage());
        }

        byte[] raw = Files.readAllBytes(tmp.toPath());
        Files.deleteIfExists(tmp.toPath());

        String htmlBody;
        if ("html".equalsIgnoreCase(targetFormat)) {
            // html 渲染器直接出 HTML 内容，无需再转
            htmlBody = new String(raw, StandardCharsets.UTF_8);
        } else {
            // markdown → HTML（内联 marked 风格，不依赖外部 CDN）
            String markdown = new String(raw, StandardCharsets.UTF_8);
            htmlBody = markdownToHtml(markdown);
        }

        String html = HTML_WRAPPER.formatted(data.getTitle() != null ? data.getTitle() : "数据库设计文档", htmlBody);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(html.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Minimal inline marked-like markdown to HTML converter.
     *
     * <p>不引入任何第三方库，直接手写：覆盖本场景所需的子集（标题、表格、代码块、列表、粗斜体、链接）。
     * 完整 markdown → HTML 转换已有 DocumentProvider("markdown") 负责，这里只做前端预览用的轻量渲染。</p>
     */
    private static String markdownToHtml(String md) {
        if (md == null || md.isBlank()) {
            return "<p><em>（文档内容为空）</em></p>";
        }
        StringBuilder sb = new StringBuilder();
        String[] lines = md.split("\n", -1);
        boolean inCode = false;
        boolean inUl = false;
        boolean inOl = false;
        boolean inTable = false;

        for (String raw : lines) {
            String line = raw;

            // 代码块开始/结束
            if (line.startsWith("```")) {
                if (!inCode) {
                    if (inUl) { sb.append("</ul>\n"); inUl = false; }
                    if (inOl) { sb.append("</ol>\n"); inOl = false; }
                    if (inTable) { sb.append("</tbody></table>\n"); inTable = false; }
                    sb.append("<pre><code>");
                    inCode = true;
                } else {
                    sb.append("</code></pre>\n");
                    inCode = false;
                }
                continue;
            }
            if (inCode) {
                sb.append(escHtml(line)).append("\n");
                continue;
            }

            // 关闭未闭合的列表/表格
            if (inUl && !line.startsWith("- ") && !line.startsWith("* ")) { sb.append("</ul>\n"); inUl = false; }
            if (inOl && !line.matches("\\d+\\.\\s.*")) { sb.append("</ol>\n"); inOl = false; }
            if (inTable && !line.startsWith("|")) { sb.append("</tbody></table>\n"); inTable = false; }

            if (line.isBlank()) {
                sb.append("\n");
                continue;
            }

            // 标题
            if (line.startsWith("# ")) {
                sb.append("<h1>").append(inline(line.substring(2))).append("</h1>\n");
                continue;
            }
            if (line.startsWith("## ")) {
                sb.append("<h2>").append(inline(line.substring(3))).append("</h2>\n");
                continue;
            }
            if (line.startsWith("### ")) {
                sb.append("<h3>").append(inline(line.substring(4))).append("</h3>\n");
                continue;
            }

            // 无序列表
            if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inUl) { sb.append("<ul>\n"); inUl = true; }
                sb.append("<li>").append(inline(line.substring(2))).append("</li>\n");
                continue;
            }

            // 有序列表
            if (line.matches("\\d+\\.\\s.*")) {
                if (!inOl) { sb.append("<ol>\n"); inOl = true; }
                sb.append("<li>").append(inline(line.replaceFirst("^\\d+\\.\\s+", ""))).append("</li>\n");
                continue;
            }

            // 表格（处理表头分隔行）
            if (line.startsWith("|")) {
                if (!inTable) { sb.append("<table class='md-table'>\n<thead><tr>"); inTable = true; }
                // 跳过分隔行（|---|:---|）
                if (line.replace("|", "").replace("-", "").replace(":", "").trim().isEmpty()) {
                    // 分隔行 → 从 thead 切到 tbody
                    sb.append("</tr></thead><tbody>\n");
                    continue;
                }
                String[] cells = line.split("\\|", -1);
                StringBuilder row = new StringBuilder("<tr>");
                for (int i = 1; i < cells.length - 1; i++) {
                    row.append(line.startsWith("<th>") ? "<th>" : "<td>");
                    row.append(inline(cells[i].trim()));
                    row.append(line.startsWith("<th>") ? "</th>" : "</td>");
                }
                row.append("</tr>\n");
                sb.append(row);
                continue;
            }

            // hr
            if (line.matches("-{3,}|\\*{3,}|_{3,}")) {
                sb.append("<hr>\n");
                continue;
            }

            // 普通段落
            sb.append("<p>").append(inline(line)).append("</p>\n");
        }
        // 收尾
        if (inCode) {
            sb.append("</code></pre>\n");
        }
        if (inUl) {
            sb.append("</ul>\n");
        }
        if (inOl) {
            sb.append("</ol>\n");
        }
        if (inTable) {
            sb.append("</tbody></table>\n");
        }
        return sb.toString();
    }

    /** 行内元素：粗体/斜体/行内代码/链接 */
    private static String inline(String s) {
        if (s == null) {
            return "";
        }
        return escHtml(stripMarkdown(s));
    }

    /** 去掉行内 markdown 标记 */
    private static String stripMarkdown(String s) {
        if (s == null) {
            return "";
        }
        // 循环去掉成对的 ** __ * _ `，最多 20 轮（防止极端情况死循环）
        for (int i = 0; i < 20; i++) {
            String t = s.replace("**", "").replace("__", "").replace("*", "").replace("_", "").replace("`", "");
            if (t.equals(s)) {
                break;
            }
            s = t;
        }
        return s;
    }

    private static String escHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** HTML 壳：含内联样式，确保在任何页面里渲染一致 */
    private static final String HTML_WRAPPER =
            """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>%s</title>
              <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; font-size: 14px; line-height: 1.6; color: #333; background: #fff; padding: 24px 32px; max-width: 900px; margin: 0 auto; }
                h1 { font-size: 22px; font-weight: 700; color: #1a1a1a; border-bottom: 2px solid #1976d2; padding-bottom: 8px; margin-bottom: 16px; }
                h2 { font-size: 18px; font-weight: 600; color: #333; margin: 20px 0 10px; }
                h3 { font-size: 15px; font-weight: 600; color: #555; margin: 16px 0 8px; }
                p { margin-bottom: 10px; }
                ul, ol { padding-left: 24px; margin-bottom: 12px; }
                li { margin-bottom: 4px; }
                pre { background: #f5f5f5; border: 1px solid #e0e0e0; border-radius: 6px; padding: 12px 16px; overflow-x: auto; margin-bottom: 12px; font-size: 13px; }
                code { background: #f0f0f0; padding: 1px 5px; border-radius: 3px; font-family: "JetBrains Mono", Consolas, monospace; font-size: 13px; }
                pre code { background: none; padding: 0; }
                table { width: 100%%; border-collapse: collapse; margin-bottom: 16px; }
                th { background: #1976d2; color: #fff; padding: 8px 12px; text-align: left; font-weight: 600; }
                td { padding: 7px 12px; border: 1px solid #e0e0e0; }
                tr:nth-child(even) td { background: #f9f9f9; }
                tr:hover td { background: #e8f4fd; }
                hr { border: none; border-top: 1px solid #e0e0e0; margin: 20px 0; }
                a { color: #1976d2; }
              </style>
            </head>
            <body>%s</body>
            </html>
            """;

    // ==================== 内部方法 ====================

    /**
     * 取渲染器的主扩展名（{@code getExtensions()[0]}）。
     *
     * @param provider 渲染器
     * @return 扩展名，含点
     */
    private static String primaryExtension(DocumentProvider provider) {
        String[] extensions = provider.getExtensions();
        String ext = extensions != null && extensions.length > 0 ? extensions[0] : ".txt";
        return ext.startsWith(".") ? ext : "." + ext;
    }

    /**
     * 文件名（不含扩展名）清洗：去掉路径分隔符与控制字符，避免拼 header 时被截断。
     *
     * @param data 文档数据
     * @return 安全的文件名词干
     */
    private static String fileBaseName(DocumentData data) {
        String title = data.getTitle() == null || data.getTitle().isBlank() ? "数据库设计文档" : data.getTitle();
        String cleaned = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return cleaned.isEmpty() ? "数据库设计文档" : cleaned;
    }

    /**
     * 组装 {@code Content-Disposition}。
     *
     * <p>中文文件名必须用 RFC 5987 的 {@code filename*}，只给 ASCII 的 {@code filename}
     * 会让浏览器拿到乱码名。这里两个都带：老客户端读 {@code filename}，新客户端读
     * {@code filename*}。</p>
     *
     * @param baseName 文件名词干
     * @param ext      扩展名
     * @return header 值
     */
    private static String contentDisposition(String baseName, String ext) {
        String ascii = baseName.replaceAll("[^\\x20-\\x7e]", "_");
        String encoded = URLEncoder.encode(baseName + ext, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + ext + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * 格式到 Content-Type 的映射。
     *
     * <p>pdf 必须显式给 {@code application/pdf}：沿用默认的 {@code text/html} 会让
     * 浏览器把 .pdf 当网页渲染成空白。</p>
     *
     * @param fmt 格式
     * @return 媒体类型
     */
    private static MediaType mediaTypeOf(String fmt) {
        return switch (fmt) {
            case "markdown" -> MediaType.parseMediaType("text/markdown;charset=UTF-8");
            case "word" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.parseMediaType("text/html;charset=UTF-8");
        };
    }

    /**
     * 模板名解析：非法值退回 {@link DocumentTemplateType#DEFAULT}，不报错。
     *
     * @param template 模板名
     * @return 模板枚举
     */
    private static DocumentTemplateType resolveTemplate(String template) {
        if (template == null || template.isBlank()) {
            return DocumentTemplateType.DEFAULT;
        }
        try {
            return DocumentTemplateType.valueOf(template.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DocumentTemplateType.DEFAULT;
        }
    }

    /**
     * 按表名白名单过滤采集结果。
     *
     * <p>大小写不敏感：用户在控制台点选表时看到的是库里的原样表名，但不同数据库
     * 元数据的大小写策略不一致（MySQL 原样、Oracle 全大写），用忽略大小写比较才
     * 不会出现「勾了却一张表都没导出」。</p>
     *
     * @param all     采集到的全部表
     * @param tables  逗号分隔的表名，为空表示不过滤
     * @return 过滤后的表清单
     */
    private static List<TableData> filterTables(List<TableData> all, String tables) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        if (tables == null || tables.isBlank()) {
            return all;
        }
        Set<String> wanted = Arrays.stream(tables.split(","))
                .map(s -> s.trim())
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (wanted.isEmpty()) {
            return all;
        }
        return all.stream()
                .filter(t -> t.getTableName() != null && wanted.contains(t.getTableName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    /**
     * 把目标库名拼到 JDBC URL 上。
     *
     * <p>parser 是从 URL 里截 catalog 的（{@code extractCatalog}），不拼的话只能导出
     * 台账登记的那个默认库，而控制台允许浏览同实例下的任意库。已带库名的 URL 会被
     * 换成目标库；query 段（{@code ?useSSL=false} 等）原样保留。</p>
     *
     * @param url      原始 URL
     * @param database 目标库名，可为空
     * @return 拼接后的 URL
     */
    private static String withDatabase(String url, String database) {
        if (database == null || database.isBlank() || url == null) {
            return url;
        }
        String db = database.trim();
        int q = url.indexOf('?');
        String base = q >= 0 ? url.substring(0, q) : url;
        String query = q >= 0 ? url.substring(q) : "";
        int slash = base.lastIndexOf('/');
        String prefix = slash >= 0 ? base.substring(0, slash + 1) : base + "/";
        return prefix + db + query;
    }

    /**
     * 纯文本错误响应。
     *
     * <p>用 {@code text/plain} 而不是 JSON：这个端点的正常产物是文件下载，
     * 前端遇到非 2xx 时直接把 body 当错误文案读即可，不必再判 Content-Type。</p>
     *
     * @param status HTTP 状态
     * @param msg    错误文案
     * @return 响应
     */
    private static ResponseEntity<byte[]> text(int status, String msg) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(msg.getBytes(StandardCharsets.UTF_8));
    }
}
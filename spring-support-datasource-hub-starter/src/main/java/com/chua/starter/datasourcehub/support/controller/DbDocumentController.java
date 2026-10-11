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
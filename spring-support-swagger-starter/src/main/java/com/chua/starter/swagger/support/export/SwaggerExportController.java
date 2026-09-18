package com.chua.starter.swagger.support.export;

import com.chua.common.support.lang.document.OpenApiDocumentData;
import com.chua.common.support.lang.document.OpenApiDocumentProvider;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
* Swagger 文档在线导出端点。
*
* <p>通过 REST 实时获取 OpenAPI 文档 HTML / Markdown,
* 便于集成到 CI 或下载到本地。</p>
*
* <h2>端点</h2>
* <ul>
*     <li>{@code GET /chua/swagger/export} — 下载 HTML (默认)</li>
*     <li>{@code GET /chua/swagger/export?format=markdown} — 下载 Markdown</li>
*     <li>{@code GET /chua/swagger/export/preview} — 浏览器内嵌预览 HTML</li>
* </ul>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@RestController
@RequestMapping("${chua.swagger.export.path:/chua/swagger/export}")
@ConditionalOnProperty(prefix = "chua.swagger.export", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerExportController {

    /** Openapiprovider */
    private final ObjectProvider<OpenAPI> openAPIProvider;
    /** 属性 */
    private final SwaggerExportProperties properties;

    /**
    * 创建 Swagger导出控制器 实例
    * @param openAPIProvider 打开api提供者
    * @param properties 属性
    */
    public SwaggerExportController(ObjectProvider<OpenAPI> openAPIProvider,
                                   SwaggerExportProperties properties) {
        this.openAPIProvider = openAPIProvider;
        this.properties = properties;
    }

    /**
    * 下载 打开api 文档。默认 HTML，可通过 {@code format} 指定。
    * @param false false
    * @return download的结果
    */
    @GetMapping
    public ResponseEntity<byte[]> download(@RequestParam(value = "format", required = false) String format) {
        OpenAPI openAPI = openAPIProvider.getIfAvailable();
        if (openAPI == null) {
            return ResponseEntity.status(404)
                    .body(("OpenAPI Bean 未加载 (SpringDoc 未启用?)").getBytes(StandardCharsets.UTF_8));
        }
        String type = format != null ? format : resolveType(properties);
        OpenApiDocumentProvider provider = OpenApiDocumentProvider.create(type);
        if (provider == null) {
            return ResponseEntity.status(500)
                    .body(("未找到 OpenApiDocumentProvider SPI (type=" + type + ")").getBytes(StandardCharsets.UTF_8));
        }
        OpenApiDocumentData data = OpenApiConverter.fromOpenApi(openAPI);
        try {
            byte[] body = render(provider, data);
            String ext = provider.getExtensions()[0];
            String filename = (data.getTitle() == null ? "api-doc" : data.getTitle()) + ext;
            return ResponseEntity.ok()
                    .contentType(mediaTypeFor(type))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(body);
        } catch (Exception e) {
            log.error("[SwaggerExport] 渲染失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(("渲染失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
    * 浏览器内嵌预览 (文本/HTML, inline 而非 attachment)。
    * @return preview的结果
    */
    @GetMapping("/preview")
    public ResponseEntity<byte[]> preview() {
        OpenAPI openAPI = openAPIProvider.getIfAvailable();
        if (openAPI == null) {
            return ResponseEntity.status(404).body(("OpenAPI Bean 未加载").getBytes(StandardCharsets.UTF_8));
        }
        OpenApiDocumentProvider provider = OpenApiDocumentProvider.create("html");
        if (provider == null) {
            return ResponseEntity.status(500).body("未找到 html provider".getBytes(StandardCharsets.UTF_8));
        }
        OpenApiDocumentData data = OpenApiConverter.fromOpenApi(openAPI);
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(render(provider, data));
        } catch (Exception e) {
            log.error("[SwaggerExport] preview 失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(("preview 失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
    * Render
    *
    * @param provider 提供者
    * @param data 数据
    * @return render的结果
    */
    private byte[] render(OpenApiDocumentProvider provider, OpenApiDocumentData data) throws Exception {
        if (provider instanceof com.chua.common.support.lang.document.OpenApiHtmlProvider html) {
            return html.render(data).getBytes(StandardCharsets.UTF_8);
        }
        // 通用路径: 写临时文件再读回
        java.io.File tmp = java.io.File.createTempFile("api-doc-", ".tmp");
        tmp.deleteOnExit();
        provider.export(data, tmp);
        try (java.io.FileInputStream fis = new java.io.FileInputStream(tmp);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            fis.transferTo(bos);
            return bos.toByteArray();
        }
    }

    /**
    * 解析类型
    *
    * @param props props
    * @return resolve类型的结果
    */
    private String resolveType(SwaggerExportProperties props) {
        String output = props.getOutput();
        if (output == null) {
            return "html";
        }
        String lower = output.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "markdown";
        }
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
            return "word";
        }
        return "html";
    }

    /**
    * media类型for
    *
    * @param type 类型
    * @return media类型for的结果
    */
    private MediaType mediaTypeFor(String type) {
        return switch (type) {
            case "markdown" -> MediaType.parseMediaType("text/markdown;charset=UTF-8");
            case "word" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> MediaType.TEXT_HTML;
        };
    }
}

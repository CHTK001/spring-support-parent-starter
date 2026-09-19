package com.chua.starter.swagger.support.export;

import com.chua.common.support.lang.document.OpenApiDocumentData;
import com.chua.common.support.lang.document.OpenApiDocumentProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;

/**
 * Swagger 文档导出门面（基于通用 {@link OpenApiDocumentProvider}）。
 *
 * <p>此入口接受 SpringDoc 的 {@link io.swagger.v3.oas.models.OpenAPI},
 * 通过 {@link OpenApiConverter} 适配到通用 {@link OpenApiDocumentData},
 * 再委托给 {@link OpenApiDocumentProvider} SPI 实现 (HTML / markdown / word)。</p>
 *
 * <p>整个流程不耦合具体 SPI 实现, 升级或替换实现 (例如自定义主题) 只需替换 SPI。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class SwaggerOpenApiExporter {

    /**
     * 导出 springdoc 打开api 到指定文件。
     *
     * @param openAPI    springdoc 打开api
     * @param props      导出配置
     * @throws IOException 写文件失败
     */
    public void export(io.swagger.v3.oas.models.OpenAPI openAPI, SwaggerExportProperties props) throws IOException {
        if (openAPI == null) {
            log.warn("[SwaggerExport] OpenAPI 为空, 跳过导出");
            return;
        }
        OpenApiDocumentData data = OpenApiConverter.fromOpenApi(openAPI);
        // 顶层信息回填
        if (props.getTitle() != null && !props.getTitle().isBlank() && (data.getTitle() == null || data.getTitle().isBlank())) {
            data.setTitle(props.getTitle());
        }
        if (props.getVersion() != null && !props.getVersion().isBlank() && (data.getVersion() == null || data.getVersion().isBlank())) {
            data.setVersion(props.getVersion());
        }

        String type = resolveType(props);
        OpenApiDocumentProvider provider = OpenApiDocumentProvider.create(type);
        if (provider == null) {
            log.error("[SwaggerExport] 未找到 OpenApiDocumentProvider SPI (type={})", type);
            return;
        }
        File output = new File(props.getOutput());
        provider.export(data, output);
        log.info("[SwaggerExport] 已通过 SPI={} 导出 OpenAPI 文档至: {}", type, output.getAbsolutePath());
    }

    /**
     * 解析要使用的 提供者 类型: 默认从 输出 后缀推断 (HTML / md / docx)。
     * @param props props
     * @return resolve类型的结果
     */
    private String resolveType(SwaggerExportProperties props) {
        String output = props.getOutput();
        if (output == null) {
            return "html";
        }
        String lower = output.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "html";
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "markdown";
        }
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
            return "word";
        }
        return "html";
    }
}

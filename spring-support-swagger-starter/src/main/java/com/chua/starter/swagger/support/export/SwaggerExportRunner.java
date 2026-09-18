package com.chua.starter.swagger.support.export;

import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
* 应用启动后将 springdoc 收集的 打开api 按泛微 E10 风格导出到本地 HTML。
*
* <p>默认在 {@code chua.swagger.export.enabled=true} 时启用。
* 输出路径由 {@link SwaggerExportProperties#getOutput()} 指定, 默认 {@code ./swagger-export.html}。</p>
*
* <p>延迟到 {@code SmartLifecycle} 启动之后, 避免 controller 中 Swagger 注解尚未扫描完成。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "chua.swagger.export", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerExportRunner implements CommandLineRunner {

    /** 打开api */
    private final OpenAPI openAPI;
    /** 属性 */
    private final SwaggerExportProperties properties;

    /**
    * 创建 Swagger导出runner 实例
    * @param openAPIProvider 打开api提供者
    * @param properties 属性
    */
    public SwaggerExportRunner(org.springframework.beans.factory.ObjectProvider<OpenAPI> openAPIProvider,
                               SwaggerExportProperties properties) {
        this.openAPI = openAPIProvider.getIfAvailable();
        this.properties = properties;
    }

    @Override
    /** 运行 */
    public void run(String... args) {
        if (openAPI == null) {
            log.warn("[SwaggerExport] 未发现 OpenAPI Bean (SpringDoc 未加载?), 跳过导出");
            return;
        }
        if (!properties.isDownloadAfterStart()) {
            log.info("[SwaggerExport] downloadAfterStart=false, 已跳过启动期导出");
            return;
        }
        try {
            SwaggerOpenApiExporter exporter = new SwaggerOpenApiExporter();
            exporter.export(openAPI, properties);
        } catch (Exception e) {
            log.error("[SwaggerExport] 导出失败: {}", e.getMessage(), e);
        }
    }
}

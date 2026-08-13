package com.chua.starter.swagger.support.export;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Swagger 导出配置。
 *
 * <p>启动后将 SpringDoc 收集的 {@code OpenAPI} 按"泛微 E10 OpenAPI"风格渲染为单页 HTML,
 * 默认输出至 {@code ./swagger-export.html}。</p>
 *
 * <h2>配置项</h2>
 * <pre>
 * chua.swagger.export:
 *   enabled: true                    # 是否启用导出
 *   output: ./swagger-export.html    # 输出文件路径
 *   title: 应用 OpenAPI 接口文档      # 顶部标题
 *   version: 1.0.0                   # 版本号徽标
 *   download-after-start: true       # 启动后自动导出
 *   include-request-sample: true     # 是否生成请求/响应示例代码块
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "chua.swagger.export")
public class SwaggerExportProperties {

    /**
     * 是否启用导出。
     */
    private boolean enabled = true;

    /**
     * 输出文件路径（相对当前工作目录）。
     */
    private String output = "./swagger-export.html";

    /**
     * 顶部标题。
     */
    private String title = "Open API 接口文档";

    /**
     * 版本号徽标。
     */
    private String version = "1.0.0";

    /**
     * 启动后是否立即导出。
     */
    private boolean downloadAfterStart = true;

    /**
     * 是否生成请求/响应示例代码块。
     */
    private boolean includeRequestSample = true;
}
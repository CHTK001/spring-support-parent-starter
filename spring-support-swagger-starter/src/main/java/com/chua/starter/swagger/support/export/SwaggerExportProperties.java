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
 * plugin.swagger.export:
 *   enabled: true                    # 是否启用导出
 *   output: ./swagger-export.html    # 输出文件路径
 *   title: 应用 OpenAPI 接口文档      # 顶部标题
 *   version: 1.0.0                   # 版本号徽标
 *   download-after-start: true       # 启动后自动导出
 *   include-request-sample: true     # 是否生成请求/响应示例代码块
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.swagger.export")
public class SwaggerExportProperties {

    /**
     * 导出总开关。
     *
     * <p>含义：同时控制启动期导出 {@link SwaggerExportRunner} 与在线导出端点 {@link SwaggerExportController}
     * 的装配（两者均为 {@code @ConditionalOnProperty}，{@code matchIfMissing=true}）。</p>
     * <p>默认值：{@code true}；显式配置 {@code plugin.swagger.export.enabled=false} 才关闭。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     export:
     *       enabled: true
     * </pre>
     */
    private boolean enabled = true;

    /**
     * 输出文件路径（相对当前工作目录）。
     *
     * <p>含义：启动期离线导出的落盘位置；文件后缀同时决定在线导出 {@code format} 的缺省类型：
     * {@code .html/.htm} → html、{@code .md/.markdown} → markdown、{@code .docx/.doc} → word。</p>
     * <p>默认值：{@code ./swagger-export.html}；配置为空时在线导出回落为 {@code html} 类型。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     export:
     *       output: ./docs/api.html
     * </pre>
     */
    private String output = "./swagger-export.html";

    /**
     * 导出文档顶部标题。
     *
     * <p>含义：渲染导出文档的顶部标题。</p>
     * <p>默认值：{@code "Open API 接口文档"}；仅在原 OpenAPI 标题为空时回填，不覆盖业务标题。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     export:
     *       title: 订单中心 OpenAPI
     * </pre>
     */
    private String title = "Open API 接口文档";

    /**
     * 导出文档版本徽标。
     *
     * <p>含义：渲染导出文档页头的版本号。</p>
     * <p>默认值：{@code "1.0.0"}；仅在原 OpenAPI 版本为空时回填。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     export:
     *       version: 1.0.0
     * </pre>
     */
    private String version = "1.0.0";

    /**
     * 启动后是否立即离线导出。
     *
     * <p>含义：{@code true} 时应用启动完成即写 {@code output} 文件；{@code false} 时仅保留在线导出端点。</p>
     * <p>默认值：{@code true}。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     export:
     *       download-after-start: true
     * </pre>
     */
    private boolean downloadAfterStart = true;

    /**
     * 是否生成请求/响应示例代码块。
     *
     * <p>含义：声明导出文档中是否包含请求/响应示例。</p>
     * <p>默认值：{@code true}。</p>
     * <p>⚠ 当前运行期无代码读取该字段（预留项），配置与否均不影响导出结果。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     export:
     *       include-request-sample: true
     * </pre>
     */
    private boolean includeRequestSample = true;
}

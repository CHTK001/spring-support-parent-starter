package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源文档预览配置
 * <p>
 * 暴露 HTTP 接口实时读取当前运行数据库的结构，渲染为 HTML 文档并在线预览。
 * 复用 {@code utils-support-datasource-starter} 中的 {@code DatabaseDocumentParser}，
 * 因此零额外三方模板引擎依赖。
 * </p>
 *
 * <h3>配置示例：</h3>
 * <pre>
 * plugin:
 *   datasource:
 *     document:
 *       enable: true                                 # 是否启用（默认 true；非 Web 应用自动跳过）
 *       base-path: /plugin/datasource/document       # 暴露路径前缀
 *       title: 数据库设计文档                         # 文档标题（默认 "数据库设计文档"）
 *       version: 1.0.0                               # 文档版本号
 *       template: DEFAULT                             # 模板：DEFAULT / AURORA / SWAGGER
 *       include-tables:                               # 仅生成指定表（白名单，逗号分隔，* 通配）
 *       exclude-tables: flyway_schema_history,sys_database_version  # 排除表
 *       include-schemas:                              # 仅指定 schema（默认全部）
 *       options:                                      # 透传给 DocumentParser 的扩展参数
 *         all: false                                  # 是否跨库导出所有 catalog
 * </pre>
 *
 * @since 2026/8/5
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = DocumentProperties.PRE, ignoreInvalidFields = true)
public class DocumentProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.datasource.document";

    /**
     * 是否启用
     * <p>默认 true；非 Web 环境（无 DispatcherServlet）会自动跳过装配</p>
     */
    private boolean enable = true;

    /**
     * 暴露路径前缀
     * <p>默认 {@code /plugin/datasource/document}</p>
     */
    private String basePath = "/plugin/datasource/document";

    /**
     * 文档标题
     */
    private String title = "数据库设计文档";

    /**
     * 文档版本号
     */
    private String version = "1.0.0";

    /**
     * HTML 模板类型：
     * <ul>
     *     <li>{@code DEFAULT}  —— 内置默认</li>
     *     <li>{@code AURORA}   —— 深色主题</li>
     *     <li>{@code SWAGGER}  —— Swagger 风格</li>
     * </ul>
     * 注意：实际可用模板由类路径下 {@code DocumentTemplate} SPI 决定；如未引入对应实现将回退到 {@code DEFAULT}
     */
    private String template = "DEFAULT";

    /**
     * 表名白名单（逗号分隔，{@code *} 通配）
     */
    private String includeTables;

    /**
     * 表名黑名单（逗号分隔，{@code *} 通配）
     */
    private String excludeTables = "flyway_schema_history,sys_database_version";

    /**
     * 模式 白名单（逗号分隔，{@code *} 表示全部）
     */
    private String includeSchemas;

    /**
     * 是否在请求时强制刷新（apply 接口始终刷新；preview 接口若缓存存在则不刷新）
     */
    private boolean cacheable = true;

    /**
     * 透传给 文档parser 的扩展参数
     * <p>例如：{@code all=true} 表示跨 catalog 导出</p>
     */
    private Map<String, Object> options = new LinkedHashMap<>();

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）

    /**
     * 是否启用
     *
     * @return 是否enable的结果
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * 获取基础路径
     *
     * @return 获取base路径的结果
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * 获取Title
     *
     * @return 获取title的结果
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取版本
     *
     * @return 获取版本的结果
     */
    public String getVersion() {
        return version;
    }

    /**
     * 获取Template
     *
     * @return 获取template的结果
     */
    public String getTemplate() {
        return template;
    }

    /**
     * 获取includetables
     *
     * @return 获取includetables的结果
     */
    public String getIncludeTables() {
        return includeTables;
    }

    /**
     * 获取excludetables
     *
     * @return 获取excludetables的结果
     */
    public String getExcludeTables() {
        return excludeTables;
    }

    /**
     * 获取include模式
     *
     * @return 获取include模式的结果
     */
    public String getIncludeSchemas() {
        return includeSchemas;
    }

    /**
     * 是否Cacheable
     *
     * @return 是否缓存的结果
     */
    public boolean isCacheable() {
        return cacheable;
    }

    /**
     * 获取期权
     *
     * @return 获取期权的结果
     */
    public Map<String, Object> getOptions() {
        return options;
    }
}

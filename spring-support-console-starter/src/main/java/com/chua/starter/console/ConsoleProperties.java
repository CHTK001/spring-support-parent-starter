package com.chua.starter.console;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理控制台配置属性。
 *
 * <p>控制台提供 FTL 管理页面（鉴权）临时调整 {@code @ApiFeature}/{@code @ApiGray} 与通用键值
 * 运行设置；调整写入 sqlite 持久化；启动时注解指纹比对，不一致则重置覆盖库（见测试报告/模块说明）。</p>
 *
 * <p>配置前缀为 {@code plugin.console}（{@link #PRE}）。完整 YAML 配置示例（application.yml）：</p>
 *
 * <pre>{@code
 * plugin:
 *   console:
 *     # 是否启用控制台（默认 true；false 时自动装配与键值环境注入一并关闭）
 *     enable: true
 *     # SQLite 持久化文件路径（默认 console.sqlite，相对运行目录解析；建议显式指向可写目录）
 *     sqlite-path: ./data/console.sqlite
 *     # 页面权限码（默认 console:manage；当前为预留配置，实际鉴权绑定编译期常量）
 *     permission-code: console:manage
 *     # 键值管理前缀过滤（默认空串=管理全部键值；当前为预留配置）
 *     kv-prefix: ""
 * }</pre>
 *
 * <p>注意：{@code ConsoleKvEnvironmentPostProcessor} 以 {@code environment.getProperty(...)}
 * 按连字符键名精确读取 {@code plugin.console.enable} / {@code plugin.console.sqlite-path}
 * （不走松散绑定），配置键请按上表形式书写。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Data
@ConfigurationProperties(prefix = ConsoleProperties.PRE)
public class ConsoleProperties {

    public static final String PRE = "plugin.console"; // PRE

    /**
     * 是否启用控制台。
     *
     * <p>含义：控制台总开关，同时控制两处——自动装配条件
     * （{@code @ConditionalOnProperty(prefix="plugin.console", name="enable", havingValue="true", matchIfMissing=true)}）
     * 与键值环境注入器 {@code ConsoleKvEnvironmentPostProcessor}。设为 {@code false} 时
     * 控制台页面不装配、键值也不注入 {@code Environment}。</p>
     *
     * <p>默认值：{@code true}（字段初始化值）；配置缺省时因 {@code matchIfMissing=true}
     * 同样按启用处理，即不配置该键模块也生效。</p>
     *
     * <p>YAML 配置示例：</p>
     * <pre>{@code
     * plugin:
     *   console:
     *     enable: true
     * }</pre>
     */
    private boolean enable = true;

    /**
     * SQLite 持久化文件路径。
     *
     * <p>含义：覆盖库（{@code console_feature_override} / {@code console_gray_override} /
     * {@code console_kv} / {@code console_meta} 四表）所在的 SQLite 文件路径，按相对运行目录解析，
     * 也可写绝对路径；持久层 {@code ConsoleSqliteStore} 与键值环境注入器读取同一文件。</p>
     *
     * <p>默认值：{@code console.sqlite}（字段初始化值）；配置缺省（未写该键）时，
     * 环境注入器按 {@code environment.getProperty("plugin.console.sqlite-path", "console.sqlite")}
     * 回落到同名默认文件。建议显式指向可写、随部署持久的目录（如 {@code ./data/console.sqlite}），
     * 避免容器/临时目录场景丢数据。</p>
     *
     * <p>YAML 配置示例：</p>
     * <pre>{@code
     * plugin:
     *   console:
     *     sqlite-path: ./data/console.sqlite
     * }</pre>
     */
    private String sqlitePath = "console.sqlite";

    /**
     * 页面访问权限码。
     *
     * <p>含义：{@code /console/**} 页面与调整接口所需的 {@code @Permission} 权限码
     * （由 oauth-client 的 PermissionInterceptor 执行鉴权）。</p>
     *
     * <p>默认值：{@code console:manage}（字段初始化值）；配置缺省时回落到该默认值。</p>
     *
     * <p>注意：当前为<b>预留配置</b>——控制器绑定的是编译期常量
     * {@code ConsolePageController.CONSOLE_PERMISSION = "console:manage"}，本字段的
     * {@code getPermissionCode()} 在模块内无调用点；修改该配置项不会改变实际鉴权行为，
     * 需要换权限码需改源码常量。</p>
     *
     * <p>YAML 配置示例：</p>
     * <pre>{@code
     * plugin:
     *   console:
     *     permission-code: console:manage
     * }</pre>
     */
    private String permissionCode = "console:manage";

    /**
     * 键值管理前缀过滤。
     *
     * <p>含义：限制键值设置页可管理的 {@code console_kv} 键前缀；空串表示管理全部键值。</p>
     *
     * <p>默认值：{@code ""}（空串，字段初始化值，即不过滤）；配置缺省或为空时
     * 均按「管理全部键值」处理。</p>
     *
     * <p>注意：当前为<b>预留配置</b>——键值页面固定调用 {@code store.listKv("")}
     * （管理全部），本字段的 {@code getKvPrefix()} 在模块内无调用点；修改该配置项
     * 当前不会改变页面行为。</p>
     *
     * <p>YAML 配置示例：</p>
     * <pre>{@code
     * plugin:
     *   console:
     *     kv-prefix: ""
     * }</pre>
     */
    private String kvPrefix = "";
}

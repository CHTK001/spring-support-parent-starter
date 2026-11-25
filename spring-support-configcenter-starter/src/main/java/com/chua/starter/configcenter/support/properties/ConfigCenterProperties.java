package com.chua.starter.configcenter.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置中心配置属性
 * <p>
 * 支持从远程配置中心（如 Nacos、Apollo、ZooKeeper、Redis 等）加载配置，
 * 并支持配置热更新功能。配置前缀为 {@link #PRE}（{@code plugin.config-center}），
 * 由环境后置处理器在启动早期绑定，随后供自动装配复用。
 * </p>
 *
 * <p>完整配置示例（application.yml）：</p>
 * <pre>{@code
 * plugin:
 *   config-center:
 *     enable: true                            # 总开关，默认 false
 *     protocol: nacos                         # 协议：nacos / apollo / zookeeper / redis
 *     address: http://127.0.0.1:8848          # 配置中心地址
 *     username: your-username                 # 认证账号
 *     password: your-password                 # 认证密码
 *     namespace-id: dev                       # 命名空间，缺省回落 spring.profiles.active
 *     connect-timeout: 5000                   # 连接超时（毫秒），默认 5000
 *     read-timeout: 5000                      # 读取超时（毫秒），默认 5000
 *     hot-reload:
 *       enabled: true                         # 热更新总开关，默认 true
 *       value-annotation-enabled: true        # @Value 热更新开关，默认 true
 *       config-value-annotation-enabled: true # @ConfigValue 热更新开关，默认 true
 *       refresh-delay-ms: 100                 # 防抖窗口（毫秒），默认 100
 *       log-on-change: true                   # 变更打印日志，默认 true
 * }</pre>
 *
 * @since 2024/9/9
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = ConfigCenterProperties.PRE, ignoreInvalidFields = true)
public class ConfigCenterProperties {

    /**
     * PRE
     */
    public static final String PRE = "plugin.config-center";

    /**
     * 是否启用配置中心。
     * <p>
     * 总开关：{@code false} 时环境后置处理器直接返回、自动装配整体不生效，
     * 不会创建远程配置连接，也没有热更新。
     * </p>
     * <p>默认值：{@code false}。</p>
     * <pre>{@code
     * plugin:
     *   config-center:
     *     enable: true
     * }</pre>
     */
    private boolean enable = false;

    /**
     * 连接超时（毫秒）。
     * <p>
     * 与配置中心建立连接的最大等待时间，原样传入底层 {@code ConfigCenterSetting#connectionTimeout}；
     * 本模块不校验非正数，异常取值由具体协议实现解释。
     * </p>
     * <p>默认值：{@code 5000}。</p>
     * <pre>{@code
     * plugin:
     *   config-center:
     *     connect-timeout: 5000
     * }</pre>
     */
    // [P3C A20 豁免]
    private int connectTimeout = 5000;

    /**
     * 读取超时（毫秒）。
     * <p>
     * 等待配置中心响应的最大时间，原样传入底层 {@code ConfigCenterSetting#readTimeout}；
     * 本模块不校验非正数，异常取值由具体协议实现解释。
     * </p>
     * <p>默认值：{@code 5000}。</p>
     * <pre>{@code
     * plugin:
     *   config-center:
     *     read-timeout: 5000
     * }</pre>
     */
    // [P3C A20 豁免]
    private int readTimeout = 5000;

    /**
     * 协议类型，对应底层 {@code ConfigCenter} SPI 的注册名。
     * <p>
     * 当前仓库内置实现：{@code nacos}（随本模块依赖默认引入）、{@code apollo}、{@code zookeeper}、{@code redis}
     * （后三者需宿主自行引入对应 utils-starter）。未引入实现或名称拼写错误时，
     * 启动日志提示「暂不支持 {protocol}」，不抛异常，但不会加载任何远程配置。
     * </p>
     * <p>默认值：无（{@code null}）。</p>
     * <pre>{@code
     * plugin:
     *   config-center:
     *     protocol: nacos
     * }</pre>
     */
    private String protocol;

    /**
     * 配置中心地址。
     * <p>如 {@code http://127.0.0.1:8848}，具体格式随协议实现而定。</p>
     * <p>默认值：无（{@code null}）。</p>
     * <pre>{@code
     * plugin:
     *   config-center:
     *     address: http://127.0.0.1:8848
     * }</pre>
     */
    private String address;

    /**
     * 配置中心认证账号。
     * <p>默认值：无（{@code null}）。生产环境建议经环境变量或启动参数注入，避免明文提交到仓库。</p>
     * <pre>{@code
     * plugin:
     *   config-center:
     *     username: your-username
     * }</pre>
     */
    private String username;

    /**
     * 配置中心认证密码。
     * <p>默认值：无（{@code null}）。生产环境建议经环境变量或启动参数注入（示例为占位符，勿写真实口令）。</p>
     * <pre>{@code
     * plugin:
     *   config-center:
     *     password: your-password
     * }</pre>
     */
    private String password;

    /**
     * 命名空间标识，最终装入 {@code ConfigCenterSetting#profile}。
     * <p>
     * 未配置（{@code null}）时回落为 {@code spring.profiles.active}。
     * </p>
     * <p>默认值：无（{@code null}，回落 {@code spring.profiles.active}）。</p>
     * <pre>{@code
     * plugin:
     *   config-center:
     *     namespace-id: dev
     * }</pre>
     */
    private String namespaceId;

    /**
     * 热更新配置。
     * <p>
     * 嵌套配置组，控制配置变更监听、注解热更新开关、防抖窗口与变更日志；
     * 各子项含义与默认值见 {@link HotReload}。
     * </p>
     * <p>默认值：{@code new HotReload()}（子项按 {@link HotReload} 字段默认值生效）。</p>
     * <pre>{@code
     * plugin:
     *   config-center:
     *     hot-reload:
     *       enabled: true
     *       value-annotation-enabled: true
     *       config-value-annotation-enabled: true
     *       refresh-delay-ms: 100
     *       log-on-change: true
     * }</pre>
     */
    private HotReload hotReload = new HotReload();

    /**
     * 热更新配置。
     * <p>
     * 配置变更监听与注解热更新的行为开关组，YAML 前缀为
     * {@code plugin.config-center.hot-reload}。
     * </p>
     *
     * <p>完整配置示例：</p>
     * <pre>{@code
     * plugin:
     *   config-center:
     *     hot-reload:
     *       enabled: true                        # 热更新总开关，默认 true
     *       value-annotation-enabled: true       # @Value 热更新开关，默认 true
     *       config-value-annotation-enabled: true # @ConfigValue 热更新开关，默认 true
     *       refresh-delay-ms: 100                # 防抖窗口（毫秒），默认 100
     *       log-on-change: true                  # 变更打印日志，默认 true
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class HotReload {

        /**
         * 是否启用热更新。
         * <p>
         * 热更新总开关：{@code true} 时环境后置处理器按 dataId 注册变更监听，
         * 同时也是两个注解处理器注册 key 级监听的前提。
         * </p>
         * <p>默认值：{@code true}。</p>
         * <pre>{@code
         * plugin:
         *   config-center:
         *     hot-reload:
         *       enabled: true
         * }</pre>
         */
        private boolean enabled = true;

        /**
         * 是否支持 @Value 注解热更新。
         * <p>
         * 启用后，使用 @Value 注解的字段与单参方法在配置变更时自动回写新值，
         * 无需 @RefreshScope；需同时满足 {@code hot-reload.enabled=true}
         * 且协议实现支持监听。
         * </p>
         * <p>默认值：{@code true}。</p>
         * <pre>{@code
         * plugin:
         *   config-center:
         *     hot-reload:
         *       value-annotation-enabled: true
         * }</pre>
         */
        private boolean valueAnnotationEnabled = true;

        /**
         * 是否支持 @ConfigValue 注解热更新。
         * <p>
         * 启用后，使用 @ConfigValue 注解的字段支持热更新，无需 @RefreshScope；
         * 注意字段上还需显式声明 {@code hotReload = true}（该注解属性默认 {@code false}）。
         * </p>
         * <p>默认值：{@code true}。</p>
         * <pre>{@code
         * plugin:
         *   config-center:
         *     hot-reload:
         *       config-value-annotation-enabled: true
         * }</pre>
         */
        private boolean configValueAnnotationEnabled = true;

        /**
         * 配置变更后的防抖窗口（毫秒）。
         * <p>
         * 同一 dataId / 同一 key 在窗口内的后续变更会被直接丢弃（不合并、不补发），
         * 防止配置频繁变更导致应用抖动；配置为 {@code 0} 或负数时窗口不生效，
         * 每次变更都会立即应用。
         * </p>
         * <p>默认值：{@code 100}；≤0 时等效于关闭防抖。</p>
         * <pre>{@code
         * plugin:
         *   config-center:
         *     hot-reload:
         *       refresh-delay-ms: 100
         * }</pre>
         */
        // [P3C A20 豁免]
        private long refreshDelayMs = 100;

        /**
         * 是否在配置变更时打印日志。
         * <p>
         * 作用于环境级监听（dataId 变更/更新/删除）与 @ConfigValue 热更新日志；
         * @Value 热更新日志不受本项控制，固定打印。
         * </p>
         * <p>默认值：{@code true}。</p>
         * <pre>{@code
         * plugin:
         *   config-center:
         *     hot-reload:
         *       log-on-change: true
         * }</pre>
         */
        private boolean logOnChange = true;
    }
}

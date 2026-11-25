package com.chua.starter.apm.skywalking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SkyWalking 连接的「APM 视角」配置（前缀 {@code plugin.apm.skywalking}）。
 * <p>
 * utils 层已经有一套 {@code skywalking.*}，这里再给一份 {@code plugin.apm.skywalking.*}，
 * 原因是平台侧的 APM 开关都收在 {@code plugin.apm} 命名空间下：运维只想改 APM 相关配置时，
 * 不必在 {@code skywalking} 与 {@code plugin.apm} 两个前缀之间来回翻。
 * </p>
 * <p>
 * 两份都可以用，{@code plugin.apm.skywalking.*} 优先级更高（自动配置声明了 order）。
 * 全部留空时本模块不改动任何设置，沿用 {@code skywalking.*} 或环境变量。
 * </p>
 * <p>
 * 装配条件：只有显式配置了 {@code plugin.apm.skywalking.url}，桥接 Bean
 * （{@link SkywalkingApmAutoConfiguration}）才会装配并把本类的值写入进程级
 * {@code SkywalkingSettings}；键不存在时本类不注册，{@code skywalking.*} 原样生效。
 * </p>
 *
 * <p>完整 YAML 配置示例（含配置前缀与关键项注释）：</p>
 *
 * <pre>
 * plugin:
 *   apm:
 *     skywalking:
 *       url: http://127.0.0.1:12800   # OAP 地址；只有配置了本键，桥接 Bean 才会装配
 *       username: your-username       # OAP Basic 鉴权用户名；桥接生效时空串也会覆盖进程级值
 *       password: change-me           # OAP Basic 鉴权密码；建议经环境变量/配置中心注入
 *       timeout: 5000                 # 单次查询超时（毫秒）；{@code <=0} 时沿用 skywalking.timeout（默认 5000）
 * </pre>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "plugin.apm.skywalking")
public class SkywalkingApmProperties {

    /**
     * OAP 地址，例如 {@code http://127.0.0.1:12800}。
     *
     * <p>默认值：{@code ""}（空串）。留空表示不改写进程级地址，沿用 {@code skywalking.url}
     * （utils 层默认 {@code http://127.0.0.1:12800}）。写入进程级设置时会规范化：
     * 去掉尾部 {@code /}、剥离 {@code /graphql} 后缀。</p>
     *
     * <p>注意：只有显式配置本键，整个桥接 Bean 才会装配；写成空串仍会装配，但不会改写地址。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     skywalking:
     *       url: http://127.0.0.1:12800
     * </pre>
     */
    private String url = "";

    /**
     * OAP Basic 鉴权用户名。
     *
     * <p>默认值：{@code ""}（空串）。未配置 {@code url}（桥接不装配）时，留空即沿用
     * {@code skywalking.username}；桥接一旦生效，本值非 null 即覆盖进程级用户名——
     * 空串也会覆盖，不会回落到 {@code skywalking.username}。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     skywalking:
     *       username: your-username
     * </pre>
     */
    private String username = "";

    /**
     * OAP Basic 鉴权密码。
     *
     * <p>默认值：{@code ""}（空串）。覆盖行为与 {@code username} 相同：桥接生效时空串也会
     * 覆盖进程级密码，不会回落到 {@code skywalking.password}。生产环境建议经环境变量
     * （{@code SW_OAP_PASSWORD}）或配置中心注入，不要把真实口令写进仓库。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     skywalking:
     *       password: change-me
     * </pre>
     */
    private String password = "";

    /**
     * 单次查询超时（毫秒）。
     *
     * <p>默认值：{@code 0}。{@code <=0} 时 {@code SkywalkingSettings#apply} 不覆盖，
     * 沿用 {@code skywalking.timeout}（utils 层默认 {@code 5000} 毫秒）；只有 {@code >0}
     * 才会覆盖进程级超时。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     skywalking:
     *       timeout: 5000
     * </pre>
     */
    private long timeout = 0L;
}

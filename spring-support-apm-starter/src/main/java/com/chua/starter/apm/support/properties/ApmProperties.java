package com.chua.starter.apm.support.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * APM 模块配置（前缀 {@code plugin.apm}）。
 *
 * <p>完整 YAML 配置示例（含全部配置项与默认值）：</p>
 *
 * <pre>
 * plugin:
 *   apm:
 *     enabled: true                                 # 模块总开关；false 时整个自动配置不装配
 *     type: skywalking                              # 引擎名（skywalking / opentelemetry / hotspot）；留空自动探测
 *     mode: embedded                                # 启动模式：embedded=随宿主 Servlet；standalone=独立 HTTP 服务
 *     path: /api/apm/v1                             # 统一 REST 前缀（嵌入=Servlet 映射，独立=Server 路由）
 *     host: 0.0.0.0                                 # 独立模式监听地址
 *     port: 12801                                   # 独立模式监听端口
 *     server-type: jdk                              # 独立模式底层 Server SPI 类型（jdk / netty 等）
 *     detect-interval: 10000                        # 探测结果缓存毫秒数（下限 1000）
 *     settings-file: config/apm-settings.properties # 热改设置落盘文件
 *     register-service-info: true                   # 是否登记到服务信息面板
 * </pre>
 *
 * <p>热改边界：只有 {@code enabled}、{@code type}、{@code detect-interval} 与引擎自声明项
 * （如 {@code oapUrl}）支持运行期经 {@code POST /config} 热改；{@code mode}、{@code path}、
 * {@code host}、{@code port}、{@code server-type}、{@code settings-file} 属于重启项，
 * 改后必须重启进程。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "plugin.apm")
public class ApmProperties {

    /**
     * 是否启用 APM 模块。
     *
     * <p>默认值：{@code true}。设为 {@code false} 时，类级 {@code @ConditionalOnProperty}
     * 使整个自动配置（含全部 Bean）不装配。支持运行期热改：{@code POST /config}
     * 接受 {@code true/false/1/0/on/off}。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     enabled: true
     * </pre>
     */
    private boolean enabled = true;

    /**
     * 引擎类型（{@code skywalking} / {@code opentelemetry} / {@code hotspot}）。
     * <p>留空表示自动探测：按 {@code ApmProvider#order()} 依次尝试，取第一个可用者。</p>
     *
     * <p>默认值：{@code ""}（空 = 自动探测）。支持运行期热改；部署侧可用
     * {@code -Dplugin.apm.type=xxx} 固定引擎，此时设置文件里的值被忽略。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     type: skywalking
     * </pre>
     */
    private String type = "";

    /**
     * 启动模式：
     * <ul>
     *   <li>{@code embedded} — 向当前 Spring 容器注册 Servlet，随宿主服务一起启动；</li>
     *   <li>{@code standalone} — 用 {@code ServerBuilder} 单独起一个 HTTP 服务。</li>
     * </ul>
     *
     * <p>默认值：{@code embedded}（未配置时按 embedded 处理）。属于重启项：
     * 进程起来后再改不生效，{@code POST /config} 携带会被拒绝。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     mode: embedded
     * </pre>
     */
    private String mode = "embedded";

    /**
     * 统一 REST 前缀（嵌入模式对应 Servlet 映射，独立模式对应 Server 路由）。
     *
     * <p>默认值：{@code /api/apm/v1}。装配时规范化：以 {@code /} 开头、去尾部 {@code /}。
     * 属于重启项。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     path: /api/apm/v1
     * </pre>
     */
    private String path = "/api/apm/v1";

    /**
     * 独立模式监听地址。
     *
     * <p>默认值：{@code 0.0.0.0}（监听所有网卡；独立进程入口 {@code ApmServerMain}
     * 的命令行缺省是 {@code 127.0.0.1}）。仅 {@code mode=standalone} 时生效，属于重启项。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     host: 0.0.0.0
     * </pre>
     */
    private String host = "0.0.0.0";

    /**
     * 独立模式监听端口。
     *
     * <p>默认值：{@code 12801}。仅 {@code mode=standalone} 时生效，属于重启项；
     * 嵌入模式接口随宿主端口暴露，不使用本端口。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     port: 12801
     * </pre>
     */
    // [P3C A20 豁免]
    private int port = 12801;

    /**
     * 独立模式底层 Server SPI 类型（{@code jdk} / {@code netty} …）。
     *
     * <p>默认值：{@code jdk}（utils 层 {@code ServerBuilder#type}）。
     * 仅 {@code mode=standalone} 时生效，属于重启项。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     server-type: jdk
     * </pre>
     */
    private String serverType = "jdk";

    /**
     * 探测结果缓存时间（毫秒）：避免前端轮询状态时反复探活。
     *
     * <p>默认值：{@code 10000} 毫秒；实际生效 TTL = {@code Math.max(1000, detectInterval)}，
     * 即下限 1000 毫秒（再小等于每次请求都探活）。热改时低于 1000 的值会被直接拒绝。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     detect-interval: 10000
     * </pre>
     */
    private long detectInterval = 10_000L;

    /**
     * 运行期可改配置（UI 上的「APM 设置」保存的值）落盘文件。
     * <p>
     * 只存「允许热改」的键（{@code enabled} / {@code type} / {@code detectInterval} 与各引擎的
     * 运行期设置如 OAP 地址）；部署侧的 {@code mode}/{@code path}/{@code port} 不在此列 ——
     * 它们决定 Servlet 与端口的注册方式，改了必须重启，写进文件反而会造成
     * 「文件里是新值、进程里是旧值」的假象。</p>
     *
     * <p>默认值：{@code config/apm-settings.properties}；配置为空时同样回落到该默认值。
     * 相对路径按进程工作目录解析。属于重启项。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     settings-file: config/apm-settings.properties
     * </pre>
     */
    private String settingsFile = "config/apm-settings.properties";

    /**
     * 是否登记到服务信息面板（{@code ServiceInfoRegistry}）。
     *
     * <p>默认值：{@code true}；设为 {@code false} 时 {@code apmServiceInfoContributor}
     * Bean 不装配，服务信息面板不再展示 APM 状态与入口。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>
     * plugin:
     *   apm:
     *     register-service-info: true
     * </pre>
     */
    private boolean registerServiceInfo = true;
}

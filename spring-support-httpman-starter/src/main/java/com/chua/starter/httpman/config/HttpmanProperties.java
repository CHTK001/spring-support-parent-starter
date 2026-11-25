package com.chua.starter.httpman.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 接口调试控制台（HttpMan）配置
 *
 * <p>所有 HttpMan 能力受 {@code httpman.enabled} 总开关控制。启用后：</p>
 * <ul>
 *   <li>REST {@code /ms/httpman/**} 端点暴露：集合/请求/历史的 CRUD 以及请求执行代理；</li>
 *   <li>记录每次运行（历史），支持按请求/集合/时间范围检索；</li>
 *   <li>代理执行（{@code HttpmanProxyExecutor}）使用 Spring WebClient 发出真实请求。</li>
 * </ul>
 *
 * <p>完整 YAML 配置示例（前缀 {@code httpman}）：</p>
 * <pre>{@code
 * httpman:
 *   enabled: true                  # 总开关
 *   page: /httpman                 # 前端页面路径（CMS 留档）
 *   path-prefix: /ms/httpman       # 后端 REST 路径前缀
 *   default-collection: 默认集合    # 根集合名称
 *   history-retention-days: 30     # 历史保留天数，<=0 表示不清理
 *   connect-timeout: 15s           # 连接超时
 *   read-timeout: 60s              # 读取超时
 *   ssl-insecure: false            # 跳过证书校验（仅自签/测试环境）
 *   proxy-from-env: false          # 预留：从环境变量读代理
 *   proxy: ""                      # 显式代理地址，空 = 不走代理
 *   proxy-bypass:                  # 不走代理的主机前缀
 *     - 127.0.0.1
 *     - localhost
 *   sensitive-header-mask: true    # 历史记录脱敏 Authorization/Cookie
 *   allowed-methods: [GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD]
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "httpman")
public class HttpmanProperties {

    /**
     * 总开关，{@code false} 时不暴露任何 HttpMan 能力。
     *
     * <p>默认值：{@code true}。注意：当前版本服务/控制器未读取该字段，
     * 置为 {@code false} 不会禁用已装配的 REST 接口；要彻底关闭请不引依赖或排除本 starter 的自动配置。</p>
     *
     * <pre>{@code
     * httpman:
     *   enabled: true
     * }</pre>
     */
    private boolean enabled = true;

    /**
     * 前端页面路径（不是非得用，CMS 留档）。
     *
     * <p>默认值：{@code /httpman}。预留字段，后端运行时不消费。</p>
     *
     * <pre>{@code
     * httpman:
     *   page: /httpman
     * }</pre>
     */
    private String page = "/httpman";

    /**
     * 后端 REST 路径前缀（前端约定 {@code /ms/httpman/**}）。
     *
     * <p>默认值：{@code /ms/httpman}。控制器当前通过 {@code @RequestMapping("/ms/httpman")} 固定映射，
     * 修改本字段不会改变实际端点路径，属前后端约定留档字段。</p>
     *
     * <pre>{@code
     * httpman:
     *   path-prefix: /ms/httpman
     * }</pre>
     */
    private String pathPrefix = "/ms/httpman";

    /**
     * 集合根文件夹名称（首次打开时自动创建）。
     *
     * <p>默认值：{@code 默认集合}。预留字段，服务端当前未消费；根集合由前端/调用方按需创建。</p>
     *
     * <pre>{@code
     * httpman:
     *   default-collection: 默认集合
     * }</pre>
     */
    private String defaultCollection = "默认集合";

    /**
     * 历史记录保留天数（0 表示不限制）。
     *
     * <p>默认值：{@code 30}；小于等于 0 表示不限制/不清理（清理入口对 {@code <=0} 直接返回）。
     * 当前实际清理入口是 {@code POST /ms/httpman/history/clean?retentionDays=}（接口默认 30），
     * 本字段不会被自动消费，如需按本字段清理需调用方自行取值。</p>
     *
     * <pre>{@code
     * httpman:
     *   history-retention-days: 30
     * }</pre>
     */
    // [P3C A20 豁免]
    private int historyRetentionDays = 30;

    /**
     * 代理执行：连接超时。
     *
     * <p>默认值：{@code 15s}（{@code Duration.ofSeconds(15)}）。当前执行器未单独设置连接超时
     * （由 reactor-netty 内部兜底），字段保留；YAML 支持 {@code 15s} / {@code PT15S} 等写法。</p>
     *
     * <pre>{@code
     * httpman:
     *   connect-timeout: 15s
     * }</pre>
     */
    // [P3C A20 豁免]
    private Duration connectTimeout = Duration.ofSeconds(15);

    /**
     * 代理执行：读取超时。
     *
     * <p>默认值：{@code 60s}（{@code Duration.ofSeconds(60)}）。实际生效于两处：
     * reactor-netty {@code responseTimeout} 与 {@code Mono.timeout}；
     * 服务端同步等待上限为 {@code read-timeout + 15s}。</p>
     *
     * <pre>{@code
     * httpman:
     *   read-timeout: 60s
     * }</pre>
     */
    // [P3C A20 豁免]
    private Duration readTimeout = Duration.ofSeconds(60);

    /**
     * 代理执行：是否需要跳过证书校验（地对错误证书/自签的场景开放）。
     *
     * <p>默认值：{@code false}。{@code true} 时 reactor-netty 走 {@code noSSL()} 信任所有证书，
     * 仅建议测试/自签环境开启。</p>
     *
     * <pre>{@code
     * httpman:
     *   ssl-insecure: false
     * }</pre>
     */
    private boolean sslInsecure = false;

    /**
     * 代理执行：是否为全局代理（从 {@code http_proxy} / {@code https_proxy} 环境变量读）。
     *
     * <p>默认值：{@code false}。当前执行器仅消费显式的 {@code httpman.proxy}，
     * 本字段为预留，暂未接入环境变量读取。</p>
     *
     * <pre>{@code
     * httpman:
     *   proxy-from-env: false
     * }</pre>
     */
    private boolean proxyFromEnv = false;

    /**
     * 代理执行：显式代理地址（覆盖环境变量），如 {@code http://127.0.0.1:7890}。
     *
     * <p>默认值：{@code null}（空/空白 = 不走代理，{@code noProxy()}）。
     * 地址无法解析时执行会抛 {@code IllegalArgumentException: 代理地址格式错误}。</p>
     *
     * <pre>{@code
     * httpman:
     *   proxy: http://127.0.0.1:7890
     * }</pre>
     */
    private String proxy;

    /**
     * 代理执行：排除列表，命中这些前缀的请求不走代理（如 {@code 127.0.0.1} / {@code localhost}）。
     *
     * <p>默认值：空列表（{@code new ArrayList<>()}），即不排除任何主机；
     * 元素会以逗号拼接进 reactor-netty 的 {@code nonProxyHosts}。
     * 仅在 {@code httpman.proxy} 非空时生效。</p>
     *
     * <pre>{@code
     * httpman:
     *   proxy-bypass:
     *     - 127.0.0.1
     *     - localhost
     * }</pre>
     */
    private List<String> proxyBypass = new ArrayList<>();

    /**
     * 是否在运行记录中剥离敏感头（Authorization / Cookie），防止凭据落库。
     *
     * <p>默认值：{@code true}。命中 {@code authorization} / {@code cookie} / {@code x-oauth-token}
     * 的值在请求转发前与历史落库前都会替换为 {@code ******}；生产环境请勿关闭。</p>
     *
     * <pre>{@code
     * httpman:
     *   sensitive-header-mask: true
     * }</pre>
     */
    private boolean sensitiveHeaderMask = true;

    /**
     * 允许的请求方法（不在此列的会被拒绝，避免把它当暴露代理用）。
     *
     * <p>默认值：{@code GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD}；
     * 执行时方法不在白名单内直接返回「不允许的请求方法」。
     * 置为空列表会拒绝所有执行请求。</p>
     *
     * <pre>{@code
     * httpman:
     *   allowed-methods: [GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD]
     * }</pre>
     */
    private List<String> allowedMethods = new ArrayList<>(List.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
}

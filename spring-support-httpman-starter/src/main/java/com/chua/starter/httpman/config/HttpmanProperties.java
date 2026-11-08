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
 *   <li>REST {@code /ms/httpman/**} args 暴露：集合/请求/历史的 CRUD 以及请求执行代理；</li>
 *   <li>记录每次运行（历史），支持按请求/集合/时间范围检索；</li>
 *   <li>代理执行（{@code HttpmanProxyExecutor}）使用 Spring WebClient 发出真实请求。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "httpman")
public class HttpmanProperties {

    /**
     * 总开关，false 时不暴露任何 HttpMan 能力
     */
    private boolean enabled = true;

    /**
     * 前端页面路径（不是非得用，CMS 留档）
     */
    private String page = "/httpman";

    /**
     * 后端 REST 路径前缀（前端约定 /ms/httpman/**）
     */
    private String pathPrefix = "/ms/httpman";

    /**
     * 集合根文件夹名称（首次打开时自动创建）
     */
    private String defaultCollection = "默认集合";

    /**
     * 历史记录保留天数（0 表示不限制；过期行会在查询时静默过滤，不走补偿任务）
     */
    private int historyRetentionDays = 30;

    /**
     * 代理执行：连接超时
     */
    private Duration connectTimeout = Duration.ofSeconds(15);

    /**
     * 代理执行：读取超时
     */
    private Duration readTimeout = Duration.ofSeconds(60);

    /**
     * 代理执行：是否需要跳过证书校验（地对错误证书/自签的场景开放）
     */
    private boolean sslInsecure = false;

    /**
     * 代理执行：是否为全局代理（从 {@code http_proxy} / {@code https_proxy} 环境变量读）
     */
    private boolean proxyFromEnv = false;

    /**
     * 代理执行：显式代理地址（覆盖环境变量），如 {@code http://127.0.0.1:7890}
     */
    private String proxy;

    /**
     * 代理执行：排除列表，命中这些前缀的请求不走代理（如 {@code 127.0.0.1} / {@code localhost}）
     */
    private List<String> proxyBypass = new ArrayList<>();

    /**
     * 是否在运行记录中剥离敏感头（Authorization / Cookie），防止凭据落库
     */
    private boolean sensitiveHeaderMask = true;

    /**
     * 允许的请求方法（不在此列的会被拒绝，避免把它当暴露代理用）
     */
    private List<String> allowedMethods = new ArrayList<>(List.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
}

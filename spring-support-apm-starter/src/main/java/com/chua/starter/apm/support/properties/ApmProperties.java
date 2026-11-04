package com.chua.starter.apm.support.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * APM 模块配置（前缀 {@code plugin.apm}）。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "plugin.apm")
public class ApmProperties {

    /**
     * 是否启用 APM 模块
     */
    private boolean enabled = true;

    /**
     * 引擎类型（{@code skywalking} / {@code opentelemetry} / {@code hotspot}）。
     * <p>留空表示自动探测：按 {@code ApmProvider#order()} 依次尝试，取第一个可用者。</p>
     */
    private String type = "";

    /**
     * 启动模式：
     * <ul>
     *   <li>{@code embedded} — 向当前 Spring 容器注册 Servlet，随宿主服务一起启动；</li>
     *   <li>{@code standalone} — 用 {@code ServerBuilder} 单独起一个 HTTP 服务。</li>
     * </ul>
     */
    private String mode = "embedded";

    /**
     * 统一 REST 前缀（嵌入模式对应 Servlet 映射，独立模式对应 Server 路由）
     */
    private String path = "/api/apm/v1";

    /**
     * 独立模式监听地址
     */
    private String host = "0.0.0.0";

    /**
     * 独立模式监听端口
     */
    private int port = 12801;

    /**
     * 独立模式底层 Server SPI 类型（{@code jdk} / {@code netty} …）
     */
    private String serverType = "jdk";

    /**
     * 探测结果缓存时间（毫秒）：避免前端轮询状态时反复探活
     */
    private long detectInterval = 10_000L;

    /**
     * 是否登记到服务信息面板（{@code ServiceInfoRegistry}）
     */
    private boolean registerServiceInfo = true;
}

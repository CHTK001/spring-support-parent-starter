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
 *
 * @author CH
 * @since 4.0.0.51
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "plugin.apm.skywalking")
public class SkywalkingApmProperties {

    /**
     * OAP 地址，例如 {@code http://127.0.0.1:12800}。留空表示沿用 {@code skywalking.url}
     */
    private String url = "";

    /**
     * OAP Basic 鉴权用户名，留空表示沿用 {@code skywalking.username}
     */
    private String username = "";

    /**
     * OAP Basic 鉴权密码，留空表示沿用 {@code skywalking.password}
     */
    private String password = "";

    /**
     * 单次查询超时（毫秒），{@code <=0} 表示沿用 {@code skywalking.timeout}
     */
    private long timeout = 0L;
}

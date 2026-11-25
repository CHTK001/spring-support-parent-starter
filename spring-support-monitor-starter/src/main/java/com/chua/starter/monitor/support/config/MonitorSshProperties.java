package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenSSH 采集通道超时配置。
 *
 * <p>对应 {@code com.chua.ssh.support.client.SshClient} 的 connect/session/exec 超时：
 * 慢链路（高 RTT、Tailscale 抖动）可调大，避免命令还没跑完就被等成超时。
 * 经 {@code @Component} + {@code @ConfigurationProperties} 注册。</p>
 *
 * <p>完整配置示例（前缀 {@code plugin.monitor.ssh}，值均为默认值）：</p>
 * <pre>
 * plugin:
 *   monitor:
 *     ssh:
 *       connect-timeout-seconds: 15   # 连接超时（秒）
 *       session-timeout-seconds: 30   # 会话（认证）超时（秒）
 *       exec-timeout-seconds: 60      # 命令执行/读取超时（秒）
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Component
@ConfigurationProperties(prefix = "plugin.monitor.ssh")
public class MonitorSshProperties {

    /**
     * 连接超时（秒）：建立 TCP + SSH 握手、等待 connect verify 的上限。
     *
     * <p>默认值：{@code 15}；配置 &le;0 时按 {@code 1} 秒兜底
     * （{@code SshConnectionCache} 以 {@code Math.max(1, x)} 归一）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     ssh:
     *       connect-timeout-seconds: 15
     * </pre>
     */
    // [P3C A20 豁免]
    private int connectTimeoutSeconds = 15;

    /**
     * 会话（认证）超时（秒）：等待 session.auth() 完成的上限。
     *
     * <p>默认值：{@code 30}；配置 &le;0 时按 {@code 1} 秒兜底
     * （{@code SshConnectionCache} 以 {@code Math.max(1, x)} 归一）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     ssh:
     *       session-timeout-seconds: 30
     * </pre>
     */
    // [P3C A20 豁免]
    private int sessionTimeoutSeconds = 30;

    /**
     * 命令执行/读取超时（秒）：等待 exit-status/CLOSED 的上限，慢链路可调大。
     *
     * <p>默认值：{@code 60}；配置 &le;0 时按 {@code 1} 秒兜底
     * （{@code SshConnectionCache} 以 {@code Math.max(1, x)} 归一）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     ssh:
     *       exec-timeout-seconds: 60
     * </pre>
     */
    // [P3C A20 豁免]
    private int execTimeoutSeconds = 60;
}

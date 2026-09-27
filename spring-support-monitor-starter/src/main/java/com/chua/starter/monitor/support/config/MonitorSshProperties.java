package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenSSH 采集通道超时配置。
 *
 * <p>对应 {@code com.chua.ssh.support.client.SshClient} 的 connect/session/exec 超时：
 * 慢链路（高 RTT、Tailscale 抖动）可调大，避免命令还没跑完就被等成超时。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Component
@ConfigurationProperties(prefix = "monitor.ssh")
public class MonitorSshProperties {

    /**
     * 连接超时（秒）：建立 TCP + SSH 握手、等待 connect verify 的上限。
     */
    private int connectTimeoutSeconds = 15;

    /**
     * 会话（认证）超时（秒）：等待 session.auth() 完成的上限。
     */
    private int sessionTimeoutSeconds = 15;

    /**
     * 命令执行/读取超时（秒）：等待 exit-status/CLOSED 的上限，慢链路可调大。
     */
    private int execTimeoutSeconds = 60;
}

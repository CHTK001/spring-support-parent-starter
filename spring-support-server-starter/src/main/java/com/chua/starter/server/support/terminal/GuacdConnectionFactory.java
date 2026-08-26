package com.chua.starter.server.support.terminal;

import com.chua.starter.server.support.config.ServerProperties;
import com.chua.starter.server.support.enums.ProtocolType;
import com.chua.starter.server.support.model.SshCredential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.InetGuacamoleSocket;
import org.apache.guacamole.protocol.ConfiguredGuacamoleSocket;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.springframework.stereotype.Component;

/**
 * guacd 连接工厂
 * <p>
 * 按协议（SSH/VNC/RDP）组装 {@link GuacamoleConfiguration} 并与
 * guacd 完成握手，返回已配置的 Socket。
 * 协议参数遵循 Guacamole 官方协议定义。
 * </p>
 *
 * @see <a href="https://guacamole.apache.org/doc/gug/configuring-guacamole.html">Guacamole 官方文档</a>
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuacdConnectionFactory {

    /** SSH 默认端口 */
    private static final int DEFAULT_SSH_PORT = 22;

    /** RDP 默认端口 */
    private static final int DEFAULT_RDP_PORT = 3389;

    /** VNC 默认端口 */
    private static final int DEFAULT_VNC_PORT = 5900;

    /** RDP 默认分辨率宽 */
    private static final int RDP_WIDTH = 1280;

    /** RDP 默认分辨率高 */
    private static final int RDP_HEIGHT = 768;

    /** RDP 默认 DPI */
    private static final int RDP_DPI = 96;

    /** 服务属性（guacd 地址） */
    private final ServerProperties properties;

    /**
     * 创建与 guacd 完成握手的 Socket
     *
     * @param protocolType 协议类型
     * @param address      目标主机地址
     * @param targetPort   目标服务端口，null 时按协议默认值
     * @param credential   解密后的凭据
     * @return 已配置 Socket（使用方负责 close）
     * @throws GuacamoleException 连接或握手失败时抛出
     */
    public ConfiguredGuacamoleSocket create(ProtocolType protocolType, String address,
                                            Integer targetPort, SshCredential credential) throws GuacamoleException {
        var socket = new InetGuacamoleSocket(properties.getGuacd().getHost(), properties.getGuacd().getPort());
        try {
            var config = buildConfig(protocolType, address, targetPort, credential);
            return new ConfiguredGuacamoleSocket(socket, config);
        } catch (GuacamoleException e) {
            // 握手失败时回收底层连接，避免泄漏
            try {
                socket.close();
            } catch (GuacamoleException closeError) {
                log.warn("[terminal] 回收底层 guacd Socket 失败", closeError);
            }
            throw e;
        }
    }

    /**
     * 组装协议参数
     *
     * @param protocolType 协议类型
     * @param address      目标主机地址
     * @param targetPort   目标端口
     * @param credential   凭据
     * @return 配置对象
     */
    private GuacamoleConfiguration buildConfig(ProtocolType protocolType, String address,
                                               Integer targetPort, SshCredential credential) {
        var config = new GuacamoleConfiguration();
        switch (protocolType) {
            // SSH 终端：支持密码或私钥两种认证
            case SSH -> {
                config.setProtocol("ssh");
                config.setParameter("hostname", address);
                config.setParameter("port", String.valueOf(targetPort == null ? DEFAULT_SSH_PORT : targetPort));
                if (notBlank(credential.username())) {
                    config.setParameter("username", credential.username());
                }
                if (credential.hasKey()) {
                    // 私钥内容直接经协议下发，由 guacd 完成认证
                    config.setParameter("private-key", credential.privateKey());
                    if (notBlank(credential.password())) {
                        config.setParameter("passphrase", credential.password());
                    }
                } else if (notBlank(credential.password())) {
                    config.setParameter("password", credential.password());
                }
            }
            // VNC 桌面：仅口令认证
            case VNC -> {
                config.setProtocol("vnc");
                config.setParameter("hostname", address);
                config.setParameter("port", String.valueOf(targetPort == null ? DEFAULT_VNC_PORT : targetPort));
                if (notBlank(credential.password())) {
                    config.setParameter("password", credential.password());
                }
            }
            // RDP 远程桌面：账号密码认证，指定初始分辨率
            case RDP -> {
                config.setProtocol("rdp");
                config.setParameter("hostname", address);
                config.setParameter("port", String.valueOf(targetPort == null ? DEFAULT_RDP_PORT : targetPort));
                if (notBlank(credential.username())) {
                    config.setParameter("username", credential.username());
                }
                if (notBlank(credential.password())) {
                    config.setParameter("password", credential.password());
                }
                config.setParameter("width", String.valueOf(RDP_WIDTH));
                config.setParameter("height", String.valueOf(RDP_HEIGHT));
                config.setParameter("dpi", String.valueOf(RDP_DPI));
                // 自适应远端桌面尺寸变化
                config.setParameter("resize-method", "display-update");
                config.setParameter("security", "any");
            }
            // SFTP 走 REST 实现，不应进入 guacd 通道
            default -> throw new IllegalArgumentException("协议不支持经 guacd 连接: " + protocolType);
        }
        return config;
    }

    /**
     * 非空判断
     *
     * @param value 待判断字符串
     * @return 是否非空
     */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

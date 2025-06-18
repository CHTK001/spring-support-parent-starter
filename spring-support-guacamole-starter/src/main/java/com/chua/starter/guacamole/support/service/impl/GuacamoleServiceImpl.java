package com.chua.starter.guacamole.support.service.impl;

import com.chua.starter.guacamole.support.properties.GuacamoleProperties;
import com.chua.starter.guacamole.support.service.GuacamoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.GuacamoleSocket;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.net.InetGuacamoleSocket;
import org.apache.guacamole.net.SimpleGuacamoleTunnel;
import org.apache.guacamole.protocol.ConfiguredGuacamoleSocket;
import org.apache.guacamole.protocol.GuacamoleConfiguration;

/**
 * Guacamole服务实现类
 *
 * @author CH
 * @since 2024/7/24
 */
@Slf4j
@RequiredArgsConstructor
public class GuacamoleServiceImpl implements GuacamoleService {

    private final GuacamoleProperties properties;

    @Override
    public GuacamoleTunnel createTunnel(String protocol, String host, int port) throws GuacamoleException {
        GuacamoleConfiguration config = new GuacamoleConfiguration();
        config.setProtocol(protocol);
        config.setParameter("hostname", host);
        config.setParameter("port", String.valueOf(port));

        return createTunnel(config);
    }

    @Override
    public GuacamoleTunnel createTunnel(String protocol, String host, int port, String username, String password) throws GuacamoleException {
        GuacamoleConfiguration config = new GuacamoleConfiguration();
        config.setProtocol(protocol);
        config.setParameter("hostname", host);
        config.setParameter("port", String.valueOf(port));
        config.setParameter("username", username);
        config.setParameter("password", password);

        return createTunnel(config);
    }

    @Override
    public GuacamoleTunnel createRdpTunnel(String host, int port, String username, String password,
                                           String domain, String security, int width, int height) throws GuacamoleException {
        GuacamoleConfiguration config = new GuacamoleConfiguration();
        config.setProtocol("rdp");
        config.setParameter("hostname", host);
        config.setParameter("port", String.valueOf(port));
        config.setParameter("username", username);
        config.setParameter("password", password);

        if (domain != null && !domain.isEmpty()) {
            config.setParameter("domain", domain);
        }

        if (security != null && !security.isEmpty()) {
            config.setParameter("security", security);
        }

        config.setParameter("width", String.valueOf(width));
        config.setParameter("height", String.valueOf(height));
        config.setParameter("color-depth", "24");
        config.setParameter("enable-drive", "true");
        config.setParameter("drive-path", "user-drives");
        config.setParameter("create-drive-path", "true");
        config.setParameter("enable-wallpaper", "false");
        config.setParameter("enable-theming", "false");
        config.setParameter("enable-font-smoothing", "true");
        config.setParameter("enable-full-window-drag", "false");
        config.setParameter("enable-desktop-composition", "false");
        config.setParameter("enable-menu-animations", "false");

        return createTunnel(config);
    }

    @Override
    public GuacamoleTunnel createVncTunnel(String host, int port, String password,
                                           int width, int height) throws GuacamoleException {
        GuacamoleConfiguration config = new GuacamoleConfiguration();
        config.setProtocol("vnc");
        config.setParameter("hostname", host);
        config.setParameter("port", String.valueOf(port));

        if (password != null && !password.isEmpty()) {
            config.setParameter("password", password);
        }

        config.setParameter("width", String.valueOf(width));
        config.setParameter("height", String.valueOf(height));
        config.setParameter("color-depth", "24");

        return createTunnel(config);
    }

    @Override
    public GuacamoleTunnel createSshTunnel(String host, int port, String username, String password) throws GuacamoleException {
        GuacamoleConfiguration config = new GuacamoleConfiguration();
        config.setProtocol("ssh");
        config.setParameter("hostname", host);
        config.setParameter("port", String.valueOf(port));
        config.setParameter("username", username);

        if (password != null && !password.isEmpty()) {
            config.setParameter("password", password);
        }

        config.setParameter("font-name", "Courier New");
        config.setParameter("font-size", "12");
        config.setParameter("color-scheme", "gray-black");

        return createTunnel(config);
    }

    /**
     * 创建Guacamole隧道
     *
     * @param config Guacamole配置
     * @return Guacamole隧道
     * @throws GuacamoleException 如果创建隧道时发生错误
     */
    private GuacamoleTunnel createTunnel(GuacamoleConfiguration config) throws GuacamoleException {
        // 创建与Guacamole服务器的连接
        GuacamoleSocket socket = new InetGuacamoleSocket(
                properties.getHost(),
                properties.getPort()
        );

        // 使用配置创建Guacamole socket
        ConfiguredGuacamoleSocket configuredSocket = new ConfiguredGuacamoleSocket(socket, config);

        // 创建并返回隧道
        return new SimpleGuacamoleTunnel(configuredSocket);
    }
} 
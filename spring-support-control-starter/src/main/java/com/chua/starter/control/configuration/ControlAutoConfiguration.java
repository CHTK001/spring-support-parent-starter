package com.chua.starter.control.configuration;

import com.chua.common.support.network.ProtocolType;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.starter.control.properties.ControlProperties;
import com.chua.webrtc.server.WebRtcServer;
import com.chua.webrtc.spi.media.ScreenCaptureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebRTC 远程控制自动配置。
 *
 * <p>注册 {@link WebRtcServer} Bean：同时承担</p>
 * <ul>
 *   <li>{@link com.chua.common.support.network.server.AbstractServer}：在 {@code control.webrtc.port}
 *       （默认 10000）上提供 {@code /webrtc} WebSocket 信令</li>
 *   <li>{@link com.chua.webrtc.spi.WebRtcService}：通过
 *       {@link com.chua.common.support.spi.ServiceProvider} SPI 提供会话/采集/控制能力</li>
 * </ul>
 *
 * <p>桌面采集使用 {@link ScreenCaptureService}（Windows DXGI Desktop Duplication），
 * 键鼠注入使用 {@link com.chua.webrtc.control.ControlService}（java.awt.Robot）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ControlProperties.class)
@ConditionalOnProperty(prefix = ControlProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class ControlAutoConfiguration {

    /**
     * 创建 WebRTC 远控服务器。
     *
     * @param properties 远控配置
     * @return WebRtcServer
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean(WebRtcServer.class)
    public WebRtcServer webRtcServer(ControlProperties properties) {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost(properties.getHost());
        setting.setPort(properties.getPort());
        setting.setProtocol(ProtocolType.WS.name());
        WebRtcServer server = new WebRtcServer(setting);
        server.setAutoClose(true);
        if (properties.isAutoStart()) {
            server.start();
            String displayHost = "0.0.0.0".equals(properties.getHost()) ? "127.0.0.1" : properties.getHost();
            log.info("[webrtc] 远程控制信令已启动: ws://{}:{}{}",
                    displayHost, properties.getPort(), WebRtcServer.SIGNALING_PATH);
        }
        return server;
    }
}

package com.chua.webrtc.support.configuration;

import com.chua.webrtc.support.properties.WebRtcProperties;
import com.chua.webrtc.support.service.WebRtcRoomService;
import com.chua.webrtc.support.service.WebRtcSignalingService;
import com.chua.webrtc.support.handler.WebRtcEventHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * WebRTC自动配置类
 *
 * @author CH
 * @since 4.1.0
 */
@Configuration
@EnableConfigurationProperties(WebRtcProperties.class)
@ConditionalOnProperty(prefix = "plugin.webrtc", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.chua.webrtc.support")
public class WebRtcAutoConfiguration {

    /**
     * WebRTC房间管理服务
     */
    @Bean
    public WebRtcRoomService webRtcRoomService(WebRtcProperties properties) {
        return new WebRtcRoomService(properties);
    }

    /**
     * WebRTC信令服务
     */
    @Bean
    public WebRtcSignalingService webRtcSignalingService(WebRtcRoomService roomService) {
        return new WebRtcSignalingService(roomService);
    }

    /**
     * WebRTC事件处理器
     */
    @Bean
    public WebRtcEventHandler webRtcEventHandler(WebRtcSignalingService signalingService) {
        return new WebRtcEventHandler(signalingService);
    }
}
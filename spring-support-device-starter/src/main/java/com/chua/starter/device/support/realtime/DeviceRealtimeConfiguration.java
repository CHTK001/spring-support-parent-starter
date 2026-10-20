package com.chua.starter.device.support.realtime;

import com.chua.socket.support.session.SocketSessionTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 设备实时通道配置
 *
 * <p>默认装配空发布器，保证设备模块在没有 SSE/WebSocket 时也能独立启动；
 * 宿主引入 socket starter 后，自动改用 {@link SocketDeviceRealtimePublisher}，
 * 把设备状态事件广播到全局 SSE 连接。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class DeviceRealtimeConfiguration {

    /**
     * 空发布器：只记录 debug 日志，不做任何网络发送
     *
     * @return 空发布器
     */
    @Bean
    @ConditionalOnMissingBean(DeviceRealtimePublisher.class)
    public DeviceRealtimePublisher noopDeviceRealtimePublisher() {
        return (event, payload) -> log.debug("[Device][Realtime] event={}, payload={}", event, payload);
    }

    /**
     * 宿主启用 socket 时的广播发布器
     *
     * <p>用 {@code @ConditionalOnClass} 的字符串形式，避免宿主未引入 socket starter 时
     * 加载本配置导致 NoClassDefFoundError。</p>
     *
     * @param templateProvider 会话模板提供者
     * @return 广播发布器
     */
    @Bean
    @Primary
    @ConditionalOnClass(name = "com.chua.socket.support.session.SocketSessionTemplate")
    public DeviceRealtimePublisher socketDeviceRealtimePublisher(
            ObjectProvider<SocketSessionTemplate> templateProvider) {
        return new SocketDeviceRealtimePublisher(templateProvider);
    }
}

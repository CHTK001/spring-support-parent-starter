package com.chua.socket.support.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.spi.SocketProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Socket 自动配置类
 * 通过 SPI 机制根据配置的协议类型创建对应的 Socket 服务
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
@ConditionalOnProperty(prefix = "plugin.socket", name = "enable", havingValue = "true", matchIfMissing = false)
 */
@Slf4j
@EnableConfigurationProperties(SocketProperties.class)
public class SocketConfiguration {

    /**
     * 创建 Socket 会话模板
     * 根据配置的协议类型通过 SPI 获取对应的提供者
     *
     * @param properties 配置属性
     * @param listeners  监听器列表
     * @return 会话模板实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = SocketProperties.PRE, name = "enable", havingValue = "true")
    public SocketSessionTemplate socketSessionTemplate(
            SocketProperties properties,
            List<SocketListener> listeners) {

        String protocolName = properties.getProtocol().getValue();
        log.info("[Socket] 使用协议: {}", protocolName);

        // 通过 SPI 获取对应的 Socket 提供者
        SocketProvider provider = ServiceProvider.of(SocketProvider.class)
                .getNewExtension(protocolName);

        if (provider == null) {
            throw new IllegalStateException(
                    "[Socket] 未找到协议 [" + protocolName + "] 的 SPI 实现，" +
                    "请确保已添加对应的依赖（spring-support-socket-io-starter / spring-support-socket-rsocket-starter / spring-support-socket-sse-starter）"
            );
        }

        log.info("[Socket] 加载 SPI 实现: {}", provider.getClass().getName());

        return provider.createSessionTemplate(properties, listeners);
    }

    /**
     * Socket 生命周期管理
     *
     * @param template   会话模板
     * @param properties 配置属性
     * @return 生命周期 Bean
     */
    @Bean
    @ConditionalOnProperty(prefix = SocketProperties.PRE, name = "enable", havingValue = "true")
    public SocketLifecycle socketLifecycle(
            SocketSessionTemplate template,
            SocketProperties properties) {
        return new SocketLifecycle(template, properties);
    }

    /**
     * Socket 生命周期管理类
     */
    public static class SocketLifecycle implements InitializingBean, DisposableBean {

        private final SocketSessionTemplate template;
        private final SocketProperties properties;

        public SocketLifecycle(SocketSessionTemplate template, SocketProperties properties) {
            this.template = template;
            this.properties = properties;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            log.info("[Socket] 启动 {} 服务，预设端口: {}",
                    properties.getProtocol().getValue(),
                    properties.getRoom().stream()
                            .map(SocketProperties.Room::getPort)
                            .map(String::valueOf)
                            .collect(Collectors.joining(", ")));
            template.start();
        }

        @Override
        public void destroy() throws Exception {
            log.info("[Socket] 停止 {} 服务", properties.getProtocol().getValue());
            template.stop();
        }
    }
}

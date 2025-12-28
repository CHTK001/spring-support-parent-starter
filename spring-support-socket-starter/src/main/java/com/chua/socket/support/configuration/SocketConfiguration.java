package com.chua.socket.support.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.spi.SocketProvider;
import lombok.extern.slf4j.Slf4j;
import static com.chua.starter.common.support.logger.ModuleLog.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * 创建多协议 Socket 会话模板集合
     * 根据配置的协议类型通过 SPI 获取对应的提供者
     *
     * @param properties 配置属性
     * @param listeners  监听器列表
     * @return 协议名称到会话模板实例的映射
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = SocketProperties.PRE, name = "enable", havingValue = "true")
    public Map<String, SocketSessionTemplate> socketSessionTemplates(
            SocketProperties properties,
            List<SocketListener> listeners) {

        Map<String, SocketSessionTemplate> templates = new LinkedHashMap<>();
        List<SocketProperties.ProtocolConfig> protocolConfigs = properties.getEffectiveProtocols();

        log.info("[Socket] 加载 {} 个协议配置", highlight(protocolConfigs.size()));

        for (SocketProperties.ProtocolConfig protocolConfig : protocolConfigs) {
            String protocolName = protocolConfig.getProtocol().getValue();
            log.info("[Socket] 使用协议: {}", highlight(protocolName));

            // 通过 SPI 获取对应的 Socket 提供者
            SocketProvider provider = ServiceProvider.of(SocketProvider.class)
                    .getNewExtension(protocolName);

            if (provider == null) {
                throw new IllegalStateException(
                        "[Socket] 未找到协议 [" + protocolName + "] 的 SPI 实现，" +
                        "请确保已添加对应的依赖（spring-support-socket-io-starter / spring-support-socket-rsocket-starter / spring-support-socket-sse-starter）"
                );
            }

            log.debug("[Socket] 加载 SPI 实现: {}", provider.getClass().getSimpleName());

            // 创建协议特定的配置
            SocketProperties protocolProperties = createProtocolProperties(properties, protocolConfig);
            SocketSessionTemplate template = provider.createSessionTemplate(protocolProperties, listeners);
            templates.put(protocolName, template);
        }

        return templates;
    }

    /**
     * 创建单一协议的会话模板（兼容旧代码）
     * 返回第一个协议的模板作为默认模板
     *
     * @param templates 所有协议模板映射
     * @return 默认会话模板实例
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(SocketSessionTemplate.class)
    @ConditionalOnProperty(prefix = SocketProperties.PRE, name = "enable", havingValue = "true")
    public SocketSessionTemplate socketSessionTemplate(
            Map<String, SocketSessionTemplate> templates) {
        return templates.values().iterator().next();
    }

    /**
     * 根据协议配置创建独立的属性对象
     *
     * @param baseProperties 基础属性
     * @param protocolConfig 协议配置
     * @return 协议特定的属性对象
     */
    private SocketProperties createProtocolProperties(
            SocketProperties baseProperties,
            SocketProperties.ProtocolConfig protocolConfig) {
        SocketProperties props = new SocketProperties();
        props.setEnable(baseProperties.isEnable());
        props.setProtocol(protocolConfig.getProtocol());
        props.setRoom(protocolConfig.getRoom());
        props.setHost(baseProperties.getHost());
        props.setCodecType(baseProperties.getCodecType());
        props.setEncryptEnabled(baseProperties.isEncryptEnabled());
        props.setEncryptKey(baseProperties.getEncryptKey());
        props.setMaxFrameSize(baseProperties.getMaxFrameSize());
        props.setBossCount(baseProperties.getBossCount());
        props.setWorkCount(baseProperties.getWorkCount());
        props.setAllowCustomRequests(baseProperties.isAllowCustomRequests());
        props.setPingTimeout(baseProperties.getPingTimeout());
        props.setPingInterval(baseProperties.getPingInterval());
        props.setAuthFactory(baseProperties.getAuthFactory());
        props.setUseLinuxNativeEpoll(baseProperties.isUseLinuxNativeEpoll());
        return props;
    }

    /**
     * Socket 生命周期管理
     *
     * @param templates  所有协议模板映射
     * @param properties 配置属性
     * @return 生命周期 Bean
     */
    @Bean
    @ConditionalOnProperty(prefix = SocketProperties.PRE, name = "enable", havingValue = "true")
    public SocketLifecycle socketLifecycle(
            Map<String, SocketSessionTemplate> templates,
            SocketProperties properties) {
        return new SocketLifecycle(templates, properties);
    }

    /**
     * Socket 生命周期管理类
     * 支持管理多个协议模板的启动和停止
     */
    public static class SocketLifecycle implements InitializingBean, DisposableBean {

        private final Map<String, SocketSessionTemplate> templates;
        private final SocketProperties properties;

        public SocketLifecycle(Map<String, SocketSessionTemplate> templates, SocketProperties properties) {
            this.templates = templates;
            this.properties = properties;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            List<SocketProperties.ProtocolConfig> protocolConfigs = properties.getEffectiveProtocols();
            for (SocketProperties.ProtocolConfig config : protocolConfigs) {
                String protocolName = config.getProtocol().getValue();
                SocketSessionTemplate template = templates.get(protocolName);
                if (template != null) {
                    String ports = config.getRoom().stream()
                            .map(SocketProperties.Room::getPort)
                            .map(String::valueOf)
                            .collect(Collectors.joining(", "));
                    log.info("[Socket] 启动 {} 服务, 端口: {}", highlight(protocolName), highlight(ports));
                    template.start();
                }
            }
        }

        @Override
        public void destroy() throws Exception {
            for (Map.Entry<String, SocketSessionTemplate> entry : templates.entrySet()) {
                log.info("[Socket] 停止 {} 服务", entry.getKey());
                entry.getValue().stop();
            }
        }
    }
}

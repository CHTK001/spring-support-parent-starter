package com.chua.socketio.support.configuration;

import com.chua.common.support.utils.ClassUtils;
import com.chua.socketio.support.SocketIOListener;
import com.chua.socketio.support.auth.SocketAuthFactory;
import com.chua.socketio.support.properties.SocketIoProperties;
import com.chua.socketio.support.register.SocketIoRegistration;
import com.chua.socketio.support.session.DelegateSocketSessionFactory;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.socketio.support.wrapper.WrapperConfiguration;
import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Set;

/**
 * socket.io配置
 *
 * @author CH
 */
@Slf4j
@SuppressWarnings("ALL")
@EnableConfigurationProperties(SocketIoProperties.class)
public class SocketConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {


    private SocketIoProperties socketIoProperties;
    private ServerProperties serverProperties;

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = SocketIoProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
    public SocketSessionTemplate socketSessionFactory( SocketIoProperties properties) {
        return new DelegateSocketSessionFactory(properties);
    }
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = SocketIoProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
    public SocketIoRegistration socketIoRegistration(List<WrapperConfiguration> configurations,  SocketIoProperties properties,
                                                     SocketSessionTemplate socketSessionTemplate,
                                                     List<SocketIOListener> listenerList) {
        return new SocketIoRegistration(configurations, properties, socketSessionTemplate, listenerList);

    }

    /**
     * 配置
     * @return
     */
    private WrapperConfiguration newConfiguration(SocketIoProperties.Room room, SocketAuthFactory socketAuthFactory) {
        com.corundumstudio.socketio.Configuration configuration = new Configuration();
        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setTcpNoDelay(true);
        socketConfig.setSoLinger(0);

        int port = room.getPort();
        String clientId = room.getClientId();
        configuration.setSocketConfig(socketConfig);
        configuration.setAddVersionHeader(true);
        configuration.setWebsocketCompression(true);
        configuration.setHttpCompression(true);
        configuration.setJsonSupport(new JacksonJsonSupport());
        // host在本地测试可以设置为localhost或者本机IP，在Linux服务器跑可换成服务器IP
        configuration.setHostname(socketIoProperties.getHost());
        configuration.setPort(port < 0 ? (-1 * port) + serverProperties.getPort() + 10010 : port);
        configuration.setTransports(Transport.POLLING, Transport.WEBSOCKET);
        // socket连接数大小（如只监听一个端口boss线程组为1即可）
        configuration.setBossThreads(socketIoProperties.getBossCount());
        configuration.setWorkerThreads(socketIoProperties.getBossCount());
        configuration.setAllowCustomRequests(socketIoProperties.isAllowCustomRequests());
        // 协议升级超时时间（毫秒），默认10秒。HTTP握手升级为ws协议超时时间
        configuration.setUpgradeTimeout(socketIoProperties.getUpgradeTimeout());
        // Ping消息超时时间（毫秒），默认60秒，这个时间间隔内没有接收到心跳消息就会发送超时事件
        configuration.setPingTimeout(socketIoProperties.getPingTimeout());
        // Ping消息间隔（毫秒），默认25秒。客户端向服务器发送一条心跳消息间隔
        configuration.setPingInterval(socketIoProperties.getPingInterval());
        // 允许最大消息内容大小（单位：字节），默认50M
        configuration.setMaxFramePayloadLength(socketIoProperties.getMaxFramePayloadLength());
        // HTTP消息最大内容大小（单位：字节），默认50M
        configuration.setMaxHttpContentLength(socketIoProperties.getMaxHttpContentLength());
        // 是否启用websocket的Compression（RFC 7692）
        configuration.setWebsocketCompression(true);
        // 是否启用Linux的NativeEpoll
        configuration.setUseLinuxNativeEpoll(socketIoProperties.isUseLinuxNativeEpoll());

        if(null != socketAuthFactory) {
            configuration.setAuthorizationListener(new AuthorizationListener() {
                @Override
                public AuthorizationResult getAuthorizationResult(HandshakeData data) {
                    return new AuthorizationResult(socketAuthFactory.isAuthorized(data));
                }

            });
        }

        return new WrapperConfiguration(configuration, room);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if(!socketIoProperties.isEnable()) {
            return;
        }

        Set<SocketIoProperties.Room> port = socketIoProperties.getRoom();
        Object object = ClassUtils.forObject(socketIoProperties.getAuthFactory());
        for (SocketIoProperties.Room s : port) {
            WrapperConfiguration configuration = newConfiguration(s, (SocketAuthFactory) object);
            registry.registerBeanDefinition("configuration_" + s, BeanDefinitionBuilder.genericBeanDefinition(WrapperConfiguration.class, () -> configuration).getBeanDefinition());
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        socketIoProperties = Binder.get(applicationContext.getEnvironment())
                .bindOrCreate(SocketIoProperties.PRE, SocketIoProperties.class);
        serverProperties = Binder.get(applicationContext.getEnvironment())
                .bindOrCreate("server", ServerProperties.class);
    }
}

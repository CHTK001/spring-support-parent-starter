package com.chua.socketio.support.configuration;

import com.chua.common.support.utils.ClassUtils;
import com.chua.socketio.support.SocketIOListener;
import com.chua.socketio.support.auth.SocketAuthFactory;
import com.chua.socketio.support.properties.SocketIoProperties;
import com.chua.socketio.support.register.SocketIoRegistration;
import com.chua.socketio.support.resolver.DefaultSocketSessionResolver;
import com.chua.socketio.support.resolver.SocketSessionResolver;
import com.chua.socketio.support.server.DelegateSocketIOServer;
import com.chua.socketio.support.session.DelegateSocketSessionFactory;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.util.StringUtils;

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
    public SocketIoRegistration socketIoRegistration(List<Configuration> configurations,  SocketIoProperties properties,
                                                     SocketSessionTemplate socketSessionTemplate,
                                                     List<SocketIOListener> listenerList) {
        return new SocketIoRegistration(configurations, properties, socketSessionTemplate, listenerList);

    }

    /**
     * 配置
     * @return
     */
    private Configuration newConfiguration(Integer port, SocketAuthFactory socketAuthFactory) {
        com.corundumstudio.socketio.Configuration configuration = new Configuration();
        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setTcpNoDelay(true);
        socketConfig.setSoLinger(0);

        configuration.setSocketConfig(socketConfig);
        configuration.setAddVersionHeader(true);
        configuration.setWebsocketCompression(true);
        configuration.setHttpCompression(true);
        configuration.setJsonSupport(new JacksonJsonSupport());
        // host在本地测试可以设置为localhost或者本机IP，在Linux服务器跑可换成服务器IP
        configuration.setHostname(socketIoProperties.getHost());
        configuration.setPort(port == -1 ? serverProperties.getPort() + 10011 : port);
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

        if(null != socketAuthFactory) {
            configuration.setAuthorizationListener(new AuthorizationListener() {
                @Override
                public AuthorizationResult getAuthorizationResult(HandshakeData data) {
                    return new AuthorizationResult(socketAuthFactory.isAuthorized(data));
                }

            });
        }

        return configuration;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if(!socketIoProperties.isEnable()) {
            return;
        }

        Set<String> port = socketIoProperties.getPort();
        Object object = ClassUtils.forObject(socketIoProperties.getAuthFactory());
        for (String s : port) {
            Configuration configuration = newConfiguration(Integer.valueOf(s), (SocketAuthFactory) object);
            registry.registerBeanDefinition("configuration_" + s, BeanDefinitionBuilder.genericBeanDefinition(Configuration.class, () -> configuration).getBeanDefinition());
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

//package com.chua.starter.pay.support.configuration;
//
//import com.chua.common.support.net.NetUtils;
//import com.chua.common.support.protocol.ClientSetting;
//import com.chua.common.support.protocol.ServerSetting;
//import com.chua.common.support.utils.IdUtils;
//import com.chua.common.support.utils.IoUtils;
//import com.chua.common.support.utils.ObjectUtils;
//import com.chua.common.support.utils.StringUtils;
//import com.chua.starter.pay.support.properties.PayMqttProperties;
//import lombok.extern.slf4j.Slf4j;
//import org.mybatis.spring.annotation.MapperScan;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.DisposableBean;
//import org.springframework.beans.factory.support.BeanDefinitionBuilder;
//import org.springframework.beans.factory.support.BeanDefinitionRegistry;
//import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
//import org.springframework.boot.autoconfigure.web.ServerProperties;
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.boot.context.properties.bind.Binder;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.ApplicationContextAware;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.scheduling.annotation.EnableScheduling;
//
///**
// * 支付配置
// * @author CH
// * @since 2024/12/27
// */
//@Slf4j
//@MapperScan("com.chua.starter.pay.support.mapper")
//@ComponentScan({
//        "com.chua.starter.pay.support.service",
//        "com.chua.starter.pay.support.controller",
//        "com.chua.starter.pay.support.scheduler",
//})
//@EnableConfigurationProperties(PayMqttProperties.class)
//@EnableScheduling
//public class PayConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware, DisposableBean {
//
//
//    private PayMqttProperties payMqttProperties;
//    private ServerProperties serverProperties;
//    private MicaServer micaServer;
//    private MicaClient micaClient;
//
//    @Override
//    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
//        int port = payMqttProperties.getPort();
//        if(port <= 0) {
//            port = ObjectUtils.defaultIfNull(serverProperties.getPort(), 8080) + 10000;
//        }
//
//        if(NetUtils.isPortInUsed(port)) {
//            log.info("MQ: {}已被占用", port);
//            return;
//        }
//        log.info("当前服务器 MQ: 127.0.0.1:{}", port);
//        micaServer = new MicaServer(ServerSetting.builder()
//                .host(StringUtils.defaultString(payMqttProperties.getHost(), "0.0.0.0"))
//                .port(port)
//                .build());
//
//        try {
//            micaServer.start();
//            micaClient = new MicaClient(ClientSetting.builder()
//                    .host("127.0.0.1")
//                    .port(port)
//                    .clientId(IdUtils.createUlid())
//                    .username(payMqttProperties.getUsername())
//                    .password(payMqttProperties.getPassword())
//                    .build());
//
//            micaClient.connect();
//            MicaSession session = (MicaSession) micaClient.getSession();
//
//            registry.registerBeanDefinition("micaClient#ServerClient", BeanDefinitionBuilder
//                    .genericBeanDefinition(MicaSession.class, () -> session).getBeanDefinition()
//            );
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @Override
//    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
//        this.payMqttProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(PayMqttProperties.PRE, PayMqttProperties.class);
//        this.serverProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate("server", ServerProperties.class);
//    }
//
//    @Override
//    public void destroy() throws Exception {
//        IoUtils.closeQuietly(micaClient);
//        IoUtils.closeQuietly(micaServer);
//    }
//}

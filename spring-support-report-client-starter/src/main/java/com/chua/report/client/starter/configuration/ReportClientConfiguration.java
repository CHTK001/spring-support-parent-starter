package com.chua.report.client.starter.configuration;

import com.chua.common.support.protocol.protocol.Protocol;
import com.chua.common.support.protocol.server.ProtocolServer;
import com.chua.report.client.starter.function.ReportConfigValueConfiguration;
import com.chua.report.client.starter.function.ReportXxlJobConfiguration;
import com.chua.report.client.starter.properties.ReportClientProperties;
import com.chua.report.client.starter.properties.ReportEndpointProperties;
import com.chua.report.client.starter.report.OfflineReport;
import com.chua.report.client.starter.service.ReportService;
import com.chua.report.client.starter.setting.SettingFactory;
import lombok.Data;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * 上报设置
 * @author CH
 * @since 2024/9/11
 */
@Data
@EnableConfigurationProperties({ReportClientProperties.class, ReportEndpointProperties.class})
public class ReportClientConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware, EnvironmentAware, DisposableBean {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        //注册协议
        registerProtocol(registry);
        registerService(registry);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

    }

    @Override
    public void setEnvironment(Environment environment) {
        SettingFactory.getInstance().register(environment);
    }

    private void registerService(BeanDefinitionRegistry registry) {
        registry.registerBeanDefinition("reportService", BeanDefinitionBuilder
                .rootBeanDefinition(ReportService.class)
                .getBeanDefinition());

        registry.registerBeanDefinition( "reportXxlJobConfiguration", BeanDefinitionBuilder
                .rootBeanDefinition(ReportXxlJobConfiguration.class)
                .getBeanDefinition()
        );
    }

    /**
     * 注册协议
     * @param registry 注册
     */
    private void registerProtocol(BeanDefinitionRegistry registry) {
        SettingFactory settingFactory = SettingFactory.getInstance();
        if(!settingFactory.isEnable()) {
            return;
        }

        if(settingFactory.isServer() && !settingFactory.canSelf()) {
            return;
        }

        Protocol protocol = settingFactory.getProtocol();
        registry.registerBeanDefinition("reportClientEndpointConfiguration",
                BeanDefinitionBuilder.rootBeanDefinition(ProtocolServerFactoryBean.class)
                        .setDestroyMethodName("close")
                        .setInitMethodName("start")
                        .addConstructorArgValue(protocol)
                        .getBeanDefinition()
                );
        try {
            settingFactory.afterPropertiesSet();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void destroy() throws Exception {
        SettingFactory.getInstance().close();
        SettingFactory.getInstance().publish(new OfflineReport());
    }

    static class ProtocolServerFactoryBean implements FactoryBean<ProtocolServer>, AutoCloseable {
        final Protocol protocol;
        private final ProtocolServer endpointServer;

        public ProtocolServerFactoryBean(Protocol protocol) {
            this.protocol = protocol;
            endpointServer = protocol.createServer();
            endpointServer.addDefinition(new ReportConfigValueConfiguration());
            endpointServer.addDefinition(new ReportXxlJobConfiguration());
        }


        @Override
        public void close() throws Exception {
            endpointServer.close();
        }

        public void start() throws Exception {
            endpointServer.start();
        }

        @Override
        public ProtocolServer getObject() throws Exception {
            return endpointServer;
        }

        @Override
        public Class<?> getObjectType() {
            return ProtocolServer.class;
        }
    }
}

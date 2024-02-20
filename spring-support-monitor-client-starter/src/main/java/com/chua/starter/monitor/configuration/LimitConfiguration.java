package com.chua.starter.monitor.configuration;

import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.task.limit.RateLimitMappingFactory;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.factory.MonitorFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * 配置文件插件
 *
 * @author CH
 */
@Slf4j
public class LimitConfiguration implements BeanFactoryAware, EnvironmentAware, ApplicationContextAware {


    private ProtocolServer protocolServer;
    private ProtocolClient protocolClient;

    private ConfigurableListableBeanFactory beanFactory;

    private Environment environment;
    private ApplicationContext applicationContext;


    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "ConfigValueAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory");
        }
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        String[] beanNamesForType = this.beanFactory.getBeanNamesForType(ProtocolServer.class);
        if(beanNamesForType.length == 0) {
            return;
        }
        this.protocolServer = this.beanFactory.getBean(ProtocolServer.class);
        this.protocolClient = this.beanFactory.getBean(ProtocolClient.class);
        this.protocolServer.addListen(RateLimitMappingFactory.getInstance());
        doInjectSubscribe();
    }

    private void doInjectSubscribe() {
        String configAppName = MonitorFactory.getInstance().getSubscribeConfig();
        if(StringUtils.isEmpty(configAppName)) {
            return;
        }
        BootResponse response = protocolClient.get(BootRequest.builder()
                        .moduleType(ModuleType.LIMIT)
                        .commandType(CommandType.SUBSCRIBE)
                        .appName(MonitorFactory.getInstance().getAppName())
                        .profile(MonitorFactory.getInstance().getActive())
                        .content(configAppName)
                .build()
        );
        if(response.getCommandType() != CommandType.RESPONSE) {
            return;
        }

        log.info("LIMIT 订阅成功");
        register(response.getContent());
    }

    private void register(String content) {
        BootRequest request = new BootRequest();
        request.setContent(content);
        RateLimitMappingFactory.getInstance().limitConfig(request);
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}

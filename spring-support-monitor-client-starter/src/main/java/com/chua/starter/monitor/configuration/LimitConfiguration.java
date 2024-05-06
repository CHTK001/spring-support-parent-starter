package com.chua.starter.monitor.configuration;

import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.task.limit.RateLimitMappingFactory;
import com.chua.common.support.utils.MapUtils;
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


    private BootProtocolServer protocolServer;
    private BootProtocolClient protocolClient;

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
        String[] beanNamesForType = this.beanFactory.getBeanNamesForType(BootProtocolServer.class);
        if(beanNamesForType.length == 0) {
            return;
        }
        if(!MonitorFactory.getInstance().isEnable()) {
            return;
        }
        this.protocolServer = this.beanFactory.getBean(BootProtocolServer.class);
        this.protocolClient = this.beanFactory.getBean(BootProtocolClient.class);
        this.protocolServer.addMapping(RateLimitMappingFactory.getInstance());
        doInjectSubscribe();
    }

    private void doInjectSubscribe() {
        if(!MonitorFactory.getInstance().containsKey("LIMIT")) {
            return;
        }
        BootResponse response = protocolClient.get(BootRequest.builder()
                        .moduleType("LIMIT")
                        .commandType(CommandType.SUBSCRIBE)
                        .appName(MonitorFactory.getInstance().getAppName())
                        .profile(MonitorFactory.getInstance().getActive())
                        .content(MonitorFactory.getInstance().getSubscribeApps())
                .build()
        );
        if(response.getCommandType() != CommandType.RESPONSE) {
            return;
        }

        log.info("LIMIT 订阅成功");
        register(MapUtils.getString(response.getData(), "data"));
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

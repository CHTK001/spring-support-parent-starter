package com.chua.starter.monitor.configuration;

import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.client.ProtocolClient;
import com.chua.common.support.protocol.request.DefaultRequest;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.RequestBuilder;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.protocol.server.ProtocolServer;
import com.chua.common.support.protocol.server.Server;
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

import java.nio.charset.StandardCharsets;

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
        String[] beanNamesForType = this.beanFactory.getBeanNamesForType(Server.class);
        if(beanNamesForType.length == 0) {
            return;
        }
        if(!MonitorFactory.getInstance().isEnable()) {
            return;
        }
        this.protocolServer = this.beanFactory.getBean(ProtocolServer.class);
        this.protocolClient = this.beanFactory.getBean(ProtocolClient.class);
        this.protocolServer.addDefinition(RateLimitMappingFactory.getInstance());
        doInjectSubscribe();
    }

    private void doInjectSubscribe() {
        if(!MonitorFactory.getInstance().containsKey("LIMIT")) {
            return;
        }
        Response response = protocolClient.sendRequestAndReply(RequestBuilder.newBuilder()
                        .url("LIMIT")
                        .attribute("commandType", "SUBSCRIBE")
                        .attribute("appName", MonitorFactory.getInstance().getAppName())
                        .attribute("profile", MonitorFactory.getInstance().getActive())
                        .attribute("content", MonitorFactory.getInstance().getSubscribeApps())
                .build()
        );
        JsonObject responseJson =  response.getBody(JsonObject.class);
        if(responseJson.isEquals("commandType", "RESPONSE")) {
            return;
        }

        log.info("LIMIT 订阅成功");
        register(MapUtils.getString(responseJson, "data"));
    }

    private void register(String content) {
        Request request = new DefaultRequest();
        request.setBody(content.getBytes(StandardCharsets.UTF_8));
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

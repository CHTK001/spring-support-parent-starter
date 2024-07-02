package com.chua.starter.monitor.service;

import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.client.ProtocolClient;
import com.chua.common.support.protocol.protocol.CommandType;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.protocol.request.SenderRequest;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.monitor.factory.MonitorFactory;
import jakarta.annotation.Resource;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;

/**
 * 注册中心服务
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/05
 */
public class ProtocolRegisterCenterService implements RegisterCenterService, ApplicationContextAware {

    private ProtocolClient protocolClient;

    @Resource
    private Environment environment;

    @Override
    public ServiceInstance getService(String appName) {
        if(null == protocolClient) {
            throw new NullPointerException("protocolClient is null");
        }

        if(!MonitorFactory.getInstance().containsKey("REGISTER_CENTER")) {
            throw new NullPointerException("protocolClient is null");
        }

        Response response = protocolClient.sendRequestAndReply(SenderRequest.builder()
                .moduleType("REGISTER_CENTER")
                .commandType(CommandType.REQUEST)
                .appName(environment.getProperty("spring.application.name"))
                .profile(environment.getProperty("spring.profiles.active", "default"))
                .content(MonitorFactory.getInstance().getSubscribeApps())
                .build()
        );
        JsonObject responseBody = response.getBody(JsonObject.class);
        if(!responseBody.isEquals("commandType", "RESPONSE")) {
            return null;
        }

        return Json.fromJson(MapUtils.getString(responseBody, "data"), ServiceInstance.class);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            this.protocolClient = applicationContext.getAutowireCapableBeanFactory().getBean(ProtocolClient.class);
        } catch (Exception ignored) {
        }

    }
}
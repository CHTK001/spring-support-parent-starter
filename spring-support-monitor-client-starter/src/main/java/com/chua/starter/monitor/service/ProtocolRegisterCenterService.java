package com.chua.starter.monitor.service;

import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.protocol.boot.ProtocolClient;
import com.chua.common.support.utils.StringUtils;
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

        String config = MonitorFactory.getInstance().getSubscribeConfig();
        if(StringUtils.isEmpty(config)) {
            throw new NullPointerException("protocolClient is null");
        }

        BootResponse response = protocolClient.get(BootRequest.builder()
                .moduleType("REGISTER_CENTER")
                .commandType(CommandType.REQUEST)
                .appName(environment.getProperty("spring.application.name"))
                .profile(environment.getProperty("spring.profiles.active", "default"))
                .content(config)
                .build()
        );
        if(response.getCommandType() != CommandType.RESPONSE) {
            return null;
        }

        return Json.fromJson(response.getData() + "", ServiceInstance.class);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            this.protocolClient = applicationContext.getAutowireCapableBeanFactory().getBean(ProtocolClient.class);
        } catch (Exception ignored) {
        }

    }
}
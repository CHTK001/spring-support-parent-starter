package com.chua.starter.monitor.configuration;

import com.chua.common.support.invoke.annotation.RequestLine;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.client.ProtocolClient;
import com.chua.common.support.protocol.request.BadResponse;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.protocol.server.ProtocolServer;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.factory.MonitorFactory;
import com.chua.starter.monitor.patch.PatchResolver;
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
public class PatchConfiguration implements BeanFactoryAware, EnvironmentAware, ApplicationContextAware {


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
        if(MonitorFactory.getInstance().isServer()) {
            return;
        }
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        String[] beanNamesForType = this.beanFactory.getBeanNamesForType(ProtocolServer.class);
        if(beanNamesForType.length == 0) {
            return;
        }

        if(!MonitorFactory.getInstance().isEnable()) {
            return;
        }
        this.protocolServer = this.beanFactory.getBean(ProtocolServer.class);
        this.protocolClient = this.beanFactory.getBean(ProtocolClient.class);
        this.protocolServer.addDefinition(this);
    }


    /**
     * 补丁
     *
     * @param request 请求
     * @return {@link Response}
     */
    @RequestLine("patch")
    public Response patch(Request request ) {
        String content = new String(request.getBody());
        if(StringUtils.isBlank(content)) {
            return new BadResponse(request, "patchFile is null");
        }
        JsonObject jsonObject = Json.getJsonObject(content);
        String patchFile = jsonObject.getString("patchFile");
        String patchFileName = jsonObject.getString("monitorPatchPack");
        if(StringUtils.isBlank(patchFile) || StringUtils.isBlank(patchFileName)) {
            return new BadResponse(request, "patchFile is null");
        }

        PatchResolver patchResolver = new PatchResolver(patchFileName);
        patchResolver.resolve(patchFile);
        return Response.ok(request);
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

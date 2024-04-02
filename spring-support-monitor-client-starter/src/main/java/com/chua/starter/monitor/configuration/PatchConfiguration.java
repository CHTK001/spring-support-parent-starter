package com.chua.starter.monitor.configuration;

import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.annotations.ServiceMapping;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.ProtocolClient;
import com.chua.common.support.protocol.boot.ProtocolServer;
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
        this.protocolServer.addMapping(this);
    }


    /**
     * 补丁
     *
     * @param request 请求
     * @return {@link BootResponse}
     */
    @ServiceMapping("patch")
    public BootResponse patch(BootRequest request ) {
        String content = request.getContent();
        if(StringUtils.isBlank(content)) {
            return BootResponse.empty();
        }
        JsonObject jsonObject = Json.getJsonObject(content);
        String patchFile = jsonObject.getString("patchFile");
        String patchFileName = jsonObject.getString("monitorPatchPack");
        if(StringUtils.isBlank(patchFile) || StringUtils.isBlank(patchFileName)) {
            return BootResponse.empty();
        }

        PatchResolver patchResolver = new PatchResolver(patchFileName);
        patchResolver.resolve(patchFile);
        return BootResponse.ok();
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

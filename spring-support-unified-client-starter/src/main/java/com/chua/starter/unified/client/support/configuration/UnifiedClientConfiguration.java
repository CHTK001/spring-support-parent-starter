package com.chua.starter.unified.client.support.configuration;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.protocol.server.ServerOption;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;

import static com.chua.common.support.discovery.Constants.SUBSCRIBE;

/**
 * 统一客户端配置
 *
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties(UnifiedClientProperties.class)
public class UnifiedClientConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware, EnvironmentAware {

    @Resource
    private UnifiedClientProperties unifiedClientProperties;

    private String appName;
    private Environment environment;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        //注册协议
        registerProtocol(registry);
    }

    private void registerProtocol(BeanDefinitionRegistry registry) {
        if(!unifiedClientProperties.isOpen()) {
            return;
        }

        this.appName = environment.getProperty("spring.application.name");

        UnifiedClientProperties.UnifiedExecuter executer = unifiedClientProperties.getExecuter();
        String protocol = unifiedClientProperties.getProtocol();
        JSONObject jsonObject = new JSONObject();
        jsonObject.putAll(BeanMap.create(executer));
        jsonObject.put(SUBSCRIBE, unifiedClientProperties.getSubscribe());
        BootOption bootOption = BootOption.builder()
                .encryptionSchema(unifiedClientProperties.getEncryptionSchema())
                .encryptionKey(unifiedClientProperties.getEncryptionKey())
                .address(unifiedClientProperties.getAddress())
                .appName(appName)
                .ext(jsonObject)
                .profile(environment.getProperty("spring.profiles.active", "default"))
                .serverOption(ServerOption.builder().port(executer.getPort()).host(executer.getHost()).build())
                .build();
        Protocol protocol1 = ServiceProvider.of(Protocol.class).getNewExtension(protocol, bootOption);

        registry.registerBeanDefinition(protocol + "server", BeanDefinitionBuilder
                .rootBeanDefinition(protocol1.serverType())
                .addConstructorArgValue(bootOption)
                .setDestroyMethodName("close")
                .setInitMethodName("start")
                .getBeanDefinition()
        );
        registry.registerBeanDefinition(protocol + "client", BeanDefinitionBuilder
                .rootBeanDefinition(protocol1.clientType())
                .addConstructorArgValue(bootOption)
                .getBeanDefinition()
        );

        registryEnv(protocol1);
    }

    /**
     * 注册表环境
     *
     * @param protocol1 protocol1
     */
    private void registryEnv(Protocol protocol1) {
        ProtocolClient protocolClient = protocol1.createClient();
        BootRequest request = new BootRequest();
        request.setModuleType(ModuleType.EXECUTOR);
        request.setCommandType(CommandType.REGISTER);
        UnifiedClientProperties.UnifiedExecuter executer = unifiedClientProperties.getExecuter();
        JSONObject jsonObject = new JSONObject();
        jsonObject.putAll(BeanMap.create(executer));
        jsonObject.put(SUBSCRIBE, unifiedClientProperties.getSubscribe());
        request.setAppName(appName);
        request.setProfile(environment.getProperty("spring.profiles.active", "default"));
        request.setContent(jsonObject.toJSONString(JSONWriter.Feature.WriteEnumsUsingName));
        BootResponse bootResponse = protocolClient.send(request);
        log.info("注册结果: {}", bootResponse);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        unifiedClientProperties = Binder.get(applicationContext.getEnvironment())
                .bindOrCreate(UnifiedClientProperties.PRE, UnifiedClientProperties.class);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}

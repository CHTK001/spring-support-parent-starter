package com.chua.starter.oauth.server.support.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.converter.FastJsonHttpMessageConverter;
import com.chua.starter.common.support.utils.BeanDefinitionRegistryUtils;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.processor.ResponseAdviceMethodProcessor;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import com.chua.starter.oauth.server.support.properties.ThirdPartyProperties;
import com.chua.starter.oauth.server.support.protocol.Protocol;
import com.chua.starter.oauth.server.support.provider.GiteeThirdPartyProvider;
import com.chua.starter.oauth.server.support.provider.LoginProvider;
import com.chua.starter.oauth.server.support.resolver.SimpleLoggerResolver;
import com.chua.starter.oauth.server.support.resolver.SimpleUserInfoResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.*;

/**
 * 鉴权
 * <img src="oss.png" />
 *
 * @author CH
 * @since 2022/7/23 8:51
 */
@Slf4j
@EnableConfigurationProperties({AuthServerProperties.class, ThirdPartyProperties.class})
public class EnableAuthServerConfiguration implements BeanDefinitionRegistryPostProcessor,
        EnvironmentPostProcessor,
        CommandLineRunner,
        ApplicationContextAware,
        DisposableBean, WebMvcConfigurer {

    @Resource
    private AuthServerProperties authServerProperties;
    private List<HttpMessageConverter<?>> messageConverters;


    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        BeanDefinitionRegistryUtils.registerBeanDefinition(registry, LoginCheck.class);
        BeanDefinitionRegistryUtils.registerBeanDefinition(registry, LoginProvider.class);
        BeanDefinitionRegistryUtils.registerBeanDefinition(registry, GiteeThirdPartyProvider.class);
        BeanDefinitionRegistryUtils.registerBeanDefinition(registry, SimpleLoggerResolver.class);
        BeanDefinitionRegistryUtils.registerBeanDefinition(registry, SimpleUserInfoResolver.class);

        List<String> protocols = authServerProperties.getProtocols();
        if (protocols.isEmpty()) {
            log.info("未设置认证协议");
            return;
        }
        List<Class<?>> tpl = new ArrayList<>();
        Map<String, Class<Protocol>> listType = ServiceProvider.of(Protocol.class).listType();
        for (Map.Entry<String, Class<Protocol>> entry : listType.entrySet()) {
            Class<Protocol> protocol = entry.getValue();
            if (tpl.contains(protocol) && !protocols.contains(entry.getKey())) {
                continue;
            }
            tpl.add(protocol);
            registry.registerBeanDefinition(protocol.getTypeName(), BeanDefinitionBuilder.genericBeanDefinition(protocol).getBeanDefinition());
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("spring.freemarker.templateLoaderPath", "classpath:/templates/");
        properties.setProperty("spring.freemarker.suffix", ".ftl");
        properties.setProperty("spring.freemarker.charset", "UTF-8");
        properties.setProperty("spring.freemarker.request-context-attribute", "request");
        properties.setProperty("spring.freemarker.number_format", "0.##########");
        properties.setProperty("spring.freemarker.cache", "false");
        properties.setProperty("spring.resources.static-locations", "classpath:/static/");
        properties.setProperty("spring.mvc.static-path-pattern", "/static/**");
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("oauth", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }

    @Override
    public void destroy() throws Exception {
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (null != authServerProperties) {
            return;
        }
        this.messageConverters = new LinkedList<>();
        messageConverters.add(new MappingJackson2HttpMessageConverter());
        messageConverters.add(new FastJsonHttpMessageConverter());
        messageConverters.add(new MappingJackson2XmlHttpMessageConverter());
        messageConverters.add(new StringHttpMessageConverter());

        authServerProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(AuthServerProperties.PRE, AuthServerProperties.class);
    }

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        handlers.add(new ResponseAdviceMethodProcessor(messageConverters));
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("开启鉴权服务器");
        log.info("加密方式: {}", authServerProperties.getEncryption());
        log.info("是否开启密钥校验: {}", authServerProperties.isOpenCheckAkSk());
        log.info("在线模式: {}", authServerProperties.getOnline());
        log.info("密钥管理器: {}", authServerProperties.getTokenManagement());
        log.info("密钥生成器: {}", authServerProperties.getTokenGeneration());
        log.info("日志实现器: {}", authServerProperties.getLog());

    }
}

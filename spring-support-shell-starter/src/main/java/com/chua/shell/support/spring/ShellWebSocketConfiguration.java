package com.chua.shell.support.spring;

import com.chua.starter.common.support.configuration.SpringBeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.web.util.WebAppRootListener;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/**
 * ws
 *
 * @author CH
 */
@EnableConfigurationProperties(ShellProperties.class)
public class ShellWebSocketConfiguration
        implements BeanPostProcessor, ServletContextInitializer, BeanDefinitionRegistryPostProcessor {

    private BeanManager beanManager = BeanManager.getInstance();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        beanManager.register(bean, beanName);
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }


    @Bean
    public BeanManager beanManager() {
        return beanManager;
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        servletContext.addListener(WebAppRootListener.class);
        servletContext.setInitParameter("org.apache.tomcat.websocket.textBufferSize", "52428800");
        servletContext.setInitParameter("org.apache.tomcat.websocket.binaryBufferSize", "52428800");
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registry.registerBeanDefinition("ShellWebSocketHandler", BeanDefinitionBuilder.rootBeanDefinition(ShellWebSocketHandler.class).getBeanDefinition());
    }


    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Bean
    @ConditionalOnMissingBean
    public RequestMappingHandlerMappingBean requestMappingHandlerMappingBean(ShellProperties shellProperties, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return new RequestMappingHandlerMappingBean(shellProperties, requestMappingHandlerMapping);
    }

    public class RequestMappingHandlerMappingBean {
        public RequestMappingHandlerMappingBean(ShellProperties shellProperties, RequestMappingHandlerMapping requestMappingHandlerMapping) {
            if (shellProperties.getIpPass().isEmpty()) {
                return;
            }
            SpringBeanUtils.registerBean("ShellController", BeanDefinitionBuilder.rootBeanDefinition(ShellController.class).getBeanDefinition());
            SpringBeanUtils.registerController("ShellController", requestMappingHandlerMapping);
        }
    }
}

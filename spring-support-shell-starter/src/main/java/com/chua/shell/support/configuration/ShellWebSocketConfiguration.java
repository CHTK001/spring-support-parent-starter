package com.chua.shell.support.configuration;

import com.chua.common.support.shell.BaseShell;
import com.chua.common.support.shell.WebShell;
import com.chua.common.support.shell.mapping.DelegateCommand;
import com.chua.common.support.shell.mapping.HelpCommand;
import com.chua.common.support.shell.mapping.SystemCommand;
import com.chua.shell.support.command.CfrCommand;
import com.chua.shell.support.command.SpringCommand;
import com.chua.shell.support.properties.ShellProperties;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.web.util.WebAppRootListener;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import static org.springframework.beans.factory.config.AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE;

/**
 * ws
 *
 * @author CH
 */
@EnableConfigurationProperties(ShellProperties.class)
public class ShellWebSocketConfiguration extends ServerEndpointConfig.Configurator
        implements BeanPostProcessor, ServletContextInitializer, BeanDefinitionRegistryPostProcessor {

    public static BaseShell shell = new WebShell();
    static {
        shell.register(new HelpCommand());
        shell.register(new DelegateCommand());
        shell.register(new SystemCommand());
        shell.register(new SpringCommand());
        shell.register(new CfrCommand());
    }
    public static final String ADDRESS = "IP.ADDRESS";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        shell.register(bean);
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }

    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {

        Map<String, Object> attributes = sec.getUserProperties();
        HttpSession session = (HttpSession) request.getHttpSession();
        if (session != null) {
            attributes.put(ADDRESS, session.getAttribute("ip"));
            Enumeration<String> names = session.getAttributeNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                attributes.put(name, session.getAttribute(name));
            }
        }
    }


    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Bean
    public FilterRegistrationBean<ShellFilter> shellFilter() {
        FilterRegistrationBean<ShellFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ShellFilter());
        registrationBean.setAsyncSupported(true);
        registrationBean.setOrder(1);
        registrationBean.setUrlPatterns(Collections.singleton("/*"));
        return registrationBean;
    }


    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registry.registerBeanDefinition(ShellWebSocketHandler.class.getTypeName(),
                BeanDefinitionBuilder.rootBeanDefinition(ShellWebSocketHandler.class)
                        .setAutowireMode(AUTOWIRE_BY_TYPE)
                        .getBeanDefinition());
    }


    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void onStartup(jakarta.servlet.ServletContext servletContext) throws jakarta.servlet.ServletException {
        servletContext.addListener(WebAppRootListener.class);
        servletContext.setInitParameter("org.apache.tomcat.websocket.textBufferSize", "52428800");
        servletContext.setInitParameter("org.apache.tomcat.websocket.binaryBufferSize", "52428800");
    }

    static class ShellFilter implements Filter {
        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {
            HttpServletRequest req = (HttpServletRequest) servletRequest;
            req.getSession().setAttribute("ip", RequestUtils.getIpAddress(req));
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

}

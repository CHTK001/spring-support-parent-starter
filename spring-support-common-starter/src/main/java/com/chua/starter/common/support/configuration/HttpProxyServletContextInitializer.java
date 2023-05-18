package com.chua.starter.common.support.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.properties.ProxyProperties;
import com.chua.starter.common.support.transpond.httpproxy.ProxyServlet;
import com.chua.starter.common.support.transpond.resolve.HttpProxyResolver;
import com.chua.starter.common.support.transpond.resolve.ProxyResolver;
import com.google.common.base.Strings;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.ServletContextInitializer;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import java.util.List;

/**
 * http
 *
 * @author CH
 */
public class HttpProxyServletContextInitializer implements ServletContextInitializer {
    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        ProxyProperties proxyProperties = Binder.get(SpringBeanUtils.getApplicationContext().getEnvironment()).bindOrCreate(ProxyProperties.PRE, ProxyProperties.class);
        if (!proxyProperties.isOpenProxy()) {
            return;
        }
        List<ProxyProperties.ProxyConfiguration> config = proxyProperties.getConfig();
        for (ProxyProperties.ProxyConfiguration proxyConfiguration : config) {
            if (!"http".equals(proxyConfiguration.getType())) {
                continue;
            }
            ProxyResolver proxyResolver = ServiceProvider.of(ProxyResolver.class)
                    .getNewExtension(proxyConfiguration.getType(), proxyConfiguration);
            if (null == proxyResolver) {
                continue;
            }

            HttpProxyResolver resolver = (HttpProxyResolver) proxyResolver;

            ServletRegistration initServlet = servletContext.addServlet(proxyResolver.getName(), resolver.getServlet());
            initServlet.addMapping(proxyConfiguration.getSource());

            String filter = proxyConfiguration.getFilter();
            if (!Strings.isNullOrEmpty(filter)) {
                initServlet.setInitParameter("proxyFilter", filter);
            }

            initServlet.setInitParameter("targetUri", proxyConfiguration.getTarget());
            initServlet.setInitParameter(ProxyServlet.P_LOG, "false");

        }
    }
}

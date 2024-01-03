package com.chua.starter.common.support.transpond.resolve;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.utils.Md5Utils;
import com.chua.starter.common.support.transpond.httpproxy.ProxyServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;

import javax.servlet.Servlet;

/**
 * http
 *
 * @author CH
 */
@Extension("http")
public class HttpProxyResolver implements ProxyResolver {

    private final ProxyProperties.ProxyConfiguration config;

    public HttpProxyResolver(ProxyProperties.ProxyConfiguration config) {
        this.config = config;
    }

    @Override
    public Object getObject() throws Exception {
        return null;
    }

    @Override
    public Class<?> getObjectType() {
        return ServletRegistrationBean.class;
    }

    @Override
    public String getName() {
        return Md5Utils.getInstance().getMd5String(config.getSource() + "->" + config.getTarget());
    }

    public Class<? extends Servlet> getServlet() {
        return ProxyServlet.class;
    }

    @Override
    public void destroy() throws Exception {

    }
}

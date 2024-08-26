package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.configuration.resolver.VersionArgumentResolver;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.Collections;

/**
 * 版本
 * @author CH
 * @since 2024/8/26
 */
@Data
public class VersionConfiguration {
    private static final String X_HEADER_VERSION = "x-response-version";


    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<VersionFilter> versionFilter(@Autowired(required = false)VersionArgumentResolver versionArgumentResolver) {
        FilterRegistrationBean<VersionFilter> filterFilterRegistrationBean = new FilterRegistrationBean<>();
        filterFilterRegistrationBean.setAsyncSupported(true);
        filterFilterRegistrationBean.setName("version filter");
        filterFilterRegistrationBean.setFilter(new VersionFilter(versionArgumentResolver));
        filterFilterRegistrationBean.setUrlPatterns(Collections.singletonList("/*"));
        filterFilterRegistrationBean.setOrder(Integer.MAX_VALUE);
        return filterFilterRegistrationBean;
    }

    public static class VersionFilter implements Filter{

        private final VersionArgumentResolver versionArgumentResolver;

        public VersionFilter(VersionArgumentResolver versionArgumentResolver) {
            this.versionArgumentResolver = versionArgumentResolver;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            if(null != versionArgumentResolver && response instanceof HttpServletResponse httpServletResponse) {
                httpServletResponse.addHeader(X_HEADER_VERSION, versionArgumentResolver.version());
            }
        }
    }
}

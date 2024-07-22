package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.interceptor.address.AddressHandlerInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 实现WebMvcConfigurer接口的类，用于自定义Spring MVC的拦截器配置。
 * 通过该类，可以针对Web应用程序的HTTP请求进行预处理和后处理，增强应用程序的功能和安全性。
 *
 * @author CH
 * @since 2024/6/21
 */
@RequiredArgsConstructor
public class InterceptorWebMvcConfigurer implements WebMvcConfigurer {

    final ApplicationContext applicationContext;

    /**
     * 配置拦截器
     *
     * @param registry 相当于拦截器的注册中心
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//       下面这句代码相当于添加一个拦截器   添加的拦截器就是我们刚刚创建的
        registry.addInterceptor(new AddressHandlerInterceptor(applicationContext))
                .addPathPatterns("/**")
                .excludePathPatterns("/", "/login", "/logout");
    }
}


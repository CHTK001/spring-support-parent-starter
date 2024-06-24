package com.chua.starter.common.support.interceptor.address;

import com.chua.starter.common.support.configuration.SpringBeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求过滤器类，用于对HTTP请求进行预处理和后处理。
 * 实现了Filter接口，以拦截并处理web请求。
 *
 * @author CH
 * @since 2024/6/21
 */
public class AddressHandlerInterceptor implements HandlerInterceptor {

    private final ApplicationContext applicationContext;
    AddressLimiter addressLimiter;
    AddressRecorder addressRecorder;

    public AddressHandlerInterceptor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.addressLimiter = SpringBeanUtils.getBean(applicationContext, AddressLimiter.class);
        this.addressRecorder = SpringBeanUtils.getBean(applicationContext, AddressRecorder.class, new AddressRecorder.DefaultAddressRecorder());
    }
}

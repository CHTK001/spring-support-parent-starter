package com.chua.starter.datasource.support;

import com.chua.starter.datasource.DS;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author CH
 */
public class MethodPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, DS.class) || AnnotatedElementUtils.hasAnnotation(targetClass, DS.class);
    }
}

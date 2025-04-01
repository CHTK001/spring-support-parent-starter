package com.chua.starter.datasource.support;

import com.chua.starter.common.support.annotations.DS;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author CH
 */
public class MethodPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor {
    public MethodPointcutAdvisor(ApplicationContext applicationContext) {
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                Method method = invocation.getMethod();
                if (AnnotatedElementUtils.hasAnnotation(method, DS.class) || AnnotatedElementUtils.hasAnnotation(invocation.getThis().getClass(), DS.class)) {
                    DynamicDataSourceAspect dynamicDataSourceAspect = new DynamicDataSourceAspect();
                    dynamicDataSourceAspect.setApplicationContext(applicationContext);
                    dynamicDataSourceAspect.beforeSwitchDS(invocation);
                    try {
                        return invocation.proceed();
                    } finally {
                        dynamicDataSourceAspect.afterSwitchDS(null);
                    }
                }
                return invocation.proceed();
            }
        });
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, DS.class) || AnnotatedElementUtils.hasAnnotation(targetClass, DS.class);
    }
}

package com.chua.starter.oauth.client.support.interceptor;

import com.chua.starter.common.support.annotations.Permission;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.AnnotatedElementUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author CH
 * @version 1.0.0
 * @since 2024/01/18
 */
public class PermissionPointcut extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, Permission.class) ||
                AnnotatedElementUtils.hasAnnotation(targetClass, Permission.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                Method method = invocation.getMethod();
                return invocation.proceed();
            }
        });
    }
}

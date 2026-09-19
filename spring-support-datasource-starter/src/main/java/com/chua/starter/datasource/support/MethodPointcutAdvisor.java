package com.chua.starter.datasource.support;

import com.chua.starter.datasource.annotation.DS;
import com.chua.starter.datasource.properties.MultiDataSourceSettingProperties;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author CH
 * @since 4.0.0
 */
public class MethodPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor {
    /**
     * 创建 方法pointcutadvisor 实例
     * @param applicationContext application上下文
     */
    public MethodPointcutAdvisor(ApplicationContext applicationContext) {
        MultiDataSourceSettingProperties multiDataSourceSettingProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(MultiDataSourceSettingProperties.PRE, MultiDataSourceSettingProperties.class);
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            /**
             * 调用
            */
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                Method method = invocation.getMethod();
                if (AnnotatedElementUtils.hasAnnotation(method, DS.class) || AnnotatedElementUtils.hasAnnotation(invocation.getThis().getClass(), DS.class)) {
                    DynamicDataSourceAspect dynamicDataSourceAspect = new DynamicDataSourceAspect();
                    dynamicDataSourceAspect.setApplicationContext(applicationContext);
                    dynamicDataSourceAspect.beforeSwitchDS(invocation, multiDataSourceSettingProperties);
                    try {
                        return invocation.proceed();
                    } finally {
                        dynamicDataSourceAspect.afterSwitchDS();
                    }
                }
                return invocation.proceed();
            }
        });
    }

    @Override
    /**
     * Matches
    */
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, DS.class) || AnnotatedElementUtils.hasAnnotation(targetClass, DS.class);
    }
}

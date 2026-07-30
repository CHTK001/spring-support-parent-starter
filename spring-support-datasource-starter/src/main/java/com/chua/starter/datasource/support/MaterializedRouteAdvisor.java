package com.chua.starter.datasource.support;

import com.chua.common.support.data.materialized.MaterializedRouteDefinition;
import com.chua.starter.datasource.annotation.MaterializedRoute;
import com.chua.starter.datasource.materialized.MaterializedRouteContext;
import com.chua.starter.datasource.properties.MaterializedRouteProperties;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 物理化注解切面。
 *
 * @author CH
 * @since 2026/4/2
 */
public class MaterializedRouteAdvisor extends StaticMethodMatcherPointcutAdvisor {

    public MaterializedRouteAdvisor(MaterializedRouteProperties properties) {
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                MaterializedRoute route = resolveRoute(invocation.getMethod(), invocation.getThis().getClass());
                if (route == null) {
                    return invocation.proceed();
                }
                MaterializedRouteContext.set(new MaterializedRouteDefinition(
                        route.threshold() > 0 ? route.threshold() : properties.getDefaultThreshold(),
                        route.dataSource()));
                try {
                    return invocation.proceed();
                } finally {
                    MaterializedRouteContext.clear();
                }
            }
        });
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return resolveRoute(method, targetClass) != null;
    }

    private MaterializedRoute resolveRoute(Method method, Class<?> targetClass) {
        MaterializedRoute route = AnnotatedElementUtils.findMergedAnnotation(method, MaterializedRoute.class);
        if (route != null) {
            return route;
        }
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, MaterializedRoute.class);
    }
}

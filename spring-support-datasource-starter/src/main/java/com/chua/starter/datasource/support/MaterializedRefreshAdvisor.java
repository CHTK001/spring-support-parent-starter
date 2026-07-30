package com.chua.starter.datasource.support;

import com.chua.common.support.data.materialized.MaterializedRouteDefinition;
import com.chua.starter.datasource.annotation.MaterializedRefresh;
import com.chua.starter.datasource.materialized.MaterializedRefreshContext;
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
 * 手动刷新注解切面。
 *
 * @author CH
 * @since 2026/4/2
 */
public class MaterializedRefreshAdvisor extends StaticMethodMatcherPointcutAdvisor {

    public MaterializedRefreshAdvisor(MaterializedRouteProperties properties) {
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                MaterializedRefresh refresh = resolveRefresh(invocation.getMethod(), invocation.getThis().getClass());
                if (refresh == null) {
                    return invocation.proceed();
                }
                MaterializedRefreshContext.set(new MaterializedRouteDefinition(
                        refresh.threshold() > 0 ? refresh.threshold() : properties.getDefaultThreshold(),
                        refresh.dataSource()));
                try {
                    return invocation.proceed();
                } finally {
                    MaterializedRefreshContext.clear();
                }
            }
        });
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return resolveRefresh(method, targetClass) != null;
    }

    private MaterializedRefresh resolveRefresh(Method method, Class<?> targetClass) {
        MaterializedRefresh refresh = AnnotatedElementUtils.findMergedAnnotation(method, MaterializedRefresh.class);
        if (refresh != null) {
            return refresh;
        }
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, MaterializedRefresh.class);
    }
}

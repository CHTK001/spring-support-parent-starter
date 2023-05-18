package com.chua.starter.datasource.support;

import com.chua.starter.datasource.DS;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * 切入点
 *
 * @author CH
 */
public class MethodPointcut implements ApplicationContextAware {


    private ApplicationContext applicationContext;

    /**
     * 注册拦截器
     *
     * @return 注册拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public MethodPointcutAdvisor methodPointcutAdvisor() {
        MethodPointcutAdvisor methodPointcutAdvisor = new MethodPointcutAdvisor();
        methodPointcutAdvisor.setAdvice(new MethodInterceptor() {
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
        return methodPointcutAdvisor;
    }

    /**
     * 注册拦截器
     *
     * @return 注册拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator = new DefaultAdvisorAutoProxyCreator();
        defaultAdvisorAutoProxyCreator.setProxyTargetClass(true);
        return defaultAdvisorAutoProxyCreator;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

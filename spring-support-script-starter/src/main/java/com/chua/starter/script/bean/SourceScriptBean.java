package com.chua.starter.script.bean;

import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.script.marker.Marker;
import com.chua.starter.script.watchdog.DelegateWatchdog;
import com.chua.starter.script.watchdog.Watchdog;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.util.ReflectionUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * @author CH
 */
public class SourceScriptBean implements ScriptBean {

    private final Marker marker;
    private String id;
    private Watchdog watchdog;
    private Class<?> type;
    private Object bean;
    private Object proxy;

    public SourceScriptBean(String source, String suffix, Map<String, Object> stringObjectMap, Watchdog watchdog, Class<?> type) {
        this.watchdog = Optional.ofNullable(watchdog).orElse(DelegateWatchdog.getInstance());
        this.type = type;
        this.marker = Marker.create(suffix);
        this.watchdog.register(stringObjectMap, this);
        this.initial();
    }

    private void initial() {
        ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.setBeanFactory(SpringBeanUtils.getApplicationContext());
        proxyFactoryBean.setBeanClassLoader(ClassLoader.getSystemClassLoader());
        proxyFactoryBean.setTargetClass(type == null ? Object.class : type);
        proxyFactoryBean.addAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                if (null == bean) {
                    return null;
                }

                Method method = invocation.getMethod();
                ReflectionUtils.makeAccessible(method);
                return ReflectionUtils.invokeMethod(method, bean, invocation.getArguments());
            }
        });
        this.proxy = proxyFactoryBean.getObject();
    }

    @Override
    public Object getObject() throws Exception {
        return proxy;
    }

    @Override
    public Class<?> getObjectType() {
        return type;
    }

    @Override
    public synchronized void refresh(String source) {
        if (null == source) {
            return;
        }

        Object bean1 = marker.getBean(source);
        if (null != bean1) {
            this.bean = bean1;
            AutowireCapableBeanFactory autowireCapableBeanFactory = SpringBeanUtils.getApplicationContext().getAutowireCapableBeanFactory();
            autowireCapableBeanFactory.autowireBean(bean);
            this.type = bean1.getClass();
        }
    }
}

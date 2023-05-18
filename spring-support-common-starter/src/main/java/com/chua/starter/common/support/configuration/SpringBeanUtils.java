package com.chua.starter.common.support.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * spring bean 工具类
 *
 * @author CH
 * @since 2021-07-21
 */
@Slf4j
public class SpringBeanUtils {
    /**
     * 上下文
     */
    private static final ThreadLocal<ApplicationContext> APPLICATION_CONTEXT = new ThreadLocal<ApplicationContext>() {
        private final Lock LOCK = new ReentrantLock();
        private ApplicationContext applicationContext;

        @Override
        public ApplicationContext get() {
            try {
                return applicationContext;
            } finally {
                super.get();
            }
        }

        @Override
        public void set(ApplicationContext value) {
            LOCK.lock();
            try {
                super.set(value);
                this.applicationContext = value;
            } finally {
                LOCK.unlock();
            }
        }
    };
    private static ConversionService conversionService;

    /**
     * 设置上下文
     *
     * @param applicationContext 上下文
     * @see ApplicationContext
     * @see org.springframework.context.ApplicationContextAware
     */
    public static void setApplicationContext(ApplicationContext applicationContext) {
        APPLICATION_CONTEXT.set(applicationContext);
    }

    /**
     * 获取上下文
     *
     * @return 上下文
     * @see ApplicationContext
     * @see org.springframework.context.ApplicationContextAware
     */
    public static ApplicationContext getApplicationContext() {
        ApplicationContext applicationContext = null;
        try {
            return (applicationContext = APPLICATION_CONTEXT.get());
        } finally {
            APPLICATION_CONTEXT.set(applicationContext);
            APPLICATION_CONTEXT.remove();
        }
    }


    public static <T> T getBean(String name, Class<T> target) {
        return getApplicationContext().getBean(name, target);
    }

    public static <T> Collection<T> getBeanList(Class<T> target) {
        return getApplicationContext().getBeansOfType(target).values();
    }

    /**
     * 注册Controller
     *
     * @param controllerBeanName controllerBeanName
     * @throws Exception ex
     */
    public static void registerController(String controllerBeanName, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        Object controller = getApplicationContext().getBean(controllerBeanName);
        if (controller == null) {
            return;
        }
        try {

            //注册Controller
            Method method = ReflectionUtils.findMethod(requestMappingHandlerMapping.getClass(), "detectHandlerMethods", Object.class);
            //将private改为可使用
            method.setAccessible(true);
            method.invoke(requestMappingHandlerMapping, controllerBeanName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 去掉Controller的Mapping
     *
     * @param controllerBeanName
     */
    public static void unregisterController(String controllerBeanName, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        Object controller = getApplicationContext().getBean(controllerBeanName);
        if (controller == null) {
            return;
        }
        final Class<?> targetClass = controller.getClass();
        ReflectionUtils.doWithMethods(targetClass, new ReflectionUtils.MethodCallback() {
            public void doWith(Method method) {
                Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
                try {
                    Method createMappingMethod = ReflectionUtils.findMethod(requestMappingHandlerMapping.getClass(), "getMappingForMethod", Method.class, Class.class);
                    createMappingMethod.setAccessible(true);
                    RequestMappingInfo requestMappingInfo = (RequestMappingInfo)
                            createMappingMethod.invoke(requestMappingHandlerMapping, specificMethod, targetClass);
                    if (requestMappingInfo != null) {
                        requestMappingHandlerMapping.unregisterMapping(requestMappingInfo);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, ReflectionUtils.USER_DECLARED_METHODS);
    }

    /**
     * 注册对象
     *
     * @param beanName       name
     * @param beanDefinition 定义
     */
    public static void registerBean(String beanName, BeanDefinition beanDefinition) {
        ConfigurableApplicationContext configurableApplicationContext = (ConfigurableApplicationContext) getApplicationContext();
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) configurableApplicationContext.getBeanFactory();
        defaultListableBeanFactory.registerBeanDefinition(beanName, beanDefinition);
    }
    /**
     * 转化
     * @param value 值
     * @param type 类型
     * @return 结果
     * @param <T> 类型
     */
    public static <T>T convertIfNecessary(Object value, Type type) {
        return type instanceof Class ? convertIfNecessary(value, (Class<? extends T>) type) : null;
    }
    /**
     * 转化
     * @param value 值
     * @param type 类型
     * @return 结果
     * @param <T> 类型
     */
    public static <T>T convertIfNecessary(Object value, Class<T> type) {
        if (null == conversionService) {
            synchronized (SpringBeanUtils.class) {
                if (null == conversionService) {
                    conversionService = SpringBeanUtils.getApplicationContext().getBean(ConversionService.class);
                }
            }
        }

        return null == conversionService ? null : conversionService.convert(value, type);
    }

    public static Environment getEnvironment() {
        return getApplicationContext().getEnvironment();
    }
}

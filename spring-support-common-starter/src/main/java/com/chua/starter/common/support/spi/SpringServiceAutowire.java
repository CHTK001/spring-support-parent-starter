package com.chua.starter.common.support.spi;

import com.chua.common.support.objects.scanner.annotations.AutoInject;
import com.chua.common.support.spi.autowire.ServiceAutowire;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * spring
 */
public class SpringServiceAutowire implements ServiceAutowire {
    @Override
    public Object autowire(Object object) {
        if (null == object) {
            return null;
        }
        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        AutowireCapableBeanFactory autowireCapableBeanFactory = applicationContext.getAutowireCapableBeanFactory();
        autowireCapableBeanFactory.autowireBean(object);
        Class<?> aClass = object.getClass();
        ReflectionUtils.doWithFields(aClass, field -> {
            set(doInject(field, object, applicationContext), field, object);
        });
        return object;
    }

    private void set(Object doInject, Field field, Object object) {
        if (null == doInject) {
            return;
        }

        ClassUtils.setAccessible(field);
        ClassUtils.setFieldValue(field, doInject, object);
    }

    private Object doInject(Field field, Object object, ApplicationContext applicationContext) {
        Autowired autowired = field.getDeclaredAnnotation(Autowired.class);
        if (null != autowired) {
            return doInject(field, object, applicationContext, autowired);
        }

        Resource resource = field.getDeclaredAnnotation(Resource.class);
        if (null != resource) {
            return doInject(field, object, applicationContext, resource);
        }
        AutoInject autoInject = field.getDeclaredAnnotation(AutoInject.class);
        if (null != autoInject) {
            return doInject(field, object, applicationContext, autoInject);
        }
        return null;
    }

    private Object doInject(Field field, Object object, ApplicationContext applicationContext, AutoInject autoInject) {
        return byName(autoInject.value(), applicationContext, field.getType());
    }

    private Object doInject(Field field, Object object, ApplicationContext applicationContext, Resource resource) {
        return byName(resource.name(), applicationContext, field.getType());
    }

    private Object byName(String value, ApplicationContext applicationContext, Class<?> type) {
        if (StringUtils.hasText(value)) {
            Object bean = null;
            try {
                bean = applicationContext.getBean(value, type);
            } catch (BeansException ignored) {
            }
            if (null != bean) {
                return bean;
            }
        }

        Map<String, ?> beansOfType = null;
        try {
            beansOfType = applicationContext.getBeansOfType(type);
        } catch (BeansException ignored) {
        }

        if (CollectionUtils.size(beansOfType) > 0) {
            return CollectionUtils.findFirst(beansOfType);
        }

        return null;
    }

    private Object doInject(Field field, Object object, ApplicationContext applicationContext, Autowired autowired) {
        Qualifier qualifier = field.getDeclaredAnnotation(Qualifier.class);
        if (null != qualifier) {
            return byName(qualifier.value(), applicationContext, field.getType());
        }
        return byName(null, applicationContext, field.getType());
    }

    @Override
    public Object createBean(Class<?> implClass) {
        return SpringBeanUtils.getApplicationContext().getAutowireCapableBeanFactory().createBean(implClass);
    }
}

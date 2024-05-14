package com.chua.starter.common.support.objectcontent;

import com.chua.common.support.collection.SortedArrayList;
import com.chua.common.support.collection.SortedList;
import com.chua.common.support.objects.definition.BeanDefinition;
import com.chua.common.support.objects.definition.ObjectElementDefinition;
import com.chua.common.support.objects.source.TypeDefinitionScanner;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.chua.common.support.objects.source.AbstractTypeDefinitionScanner.COMPARABLE;

/**
 * spring
 * @author CH
 */
public class SpringApplicationContextTypeDefinitionScanner implements TypeDefinitionScanner {
    @Override
    public boolean isMatch(BeanDefinition typeDefinition) {
        return false;
    }

    @Override
    public SortedList<BeanDefinition> getBean(String name, Class<?> targetType) {
        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        if(null == applicationContext) {
            return SortedList.emptyList();
        }
        Object bean = null;
        try {
            bean = applicationContext.getBean(name, targetType);
        } catch (BeansException e) {
            return SortedList.emptyList();
        }
        return new SortedArrayList<>(new ObjectElementDefinition(name, bean), COMPARABLE);
    }

    @Override
    public SortedList<BeanDefinition> getBean(String name) {
        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        if(null == applicationContext) {
            return SortedList.emptyList();
        }
        Object bean = null;
        try {
            bean = applicationContext.getBean(name);
        } catch (BeansException ignored) {
            return SortedList.emptyList();
        }
        return new SortedArrayList<>(new ObjectElementDefinition(name, bean), COMPARABLE);
    }

    @Override
    public SortedList<BeanDefinition> getBean(Class<?> targetType) {
        SortedList<BeanDefinition> rs = new SortedArrayList<>(COMPARABLE);
        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        if(null == applicationContext) {
            return rs;
        }
        String[] beanNamesForType = applicationContext.getBeanNamesForType(targetType);
        for (String s : beanNamesForType) {
            rs.add(new ObjectElementDefinition(s, applicationContext.getBean(s, targetType)));
        }

        return rs;
    }

    @Override
    public void unregister(BeanDefinition typeDefinition) {

    }

    @Override
    public void unregister(String name) {

    }

    @Override
    public void register(BeanDefinition definition) {

    }

    @Override
    public SortedList<BeanDefinition> getBeanByMethod(Class<? extends Annotation> annotationType) {
        return SortedList.emptyList();
    }

    @Override
    public Set<String> getBeanDefinitionNames() {
        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        if(null == applicationContext) {
            return Collections.emptySet();
        }
        return new HashSet<>(Set.of(applicationContext.getBeanDefinitionNames()));
    }

    @Override
    public void refresh() {

    }

    @Override
    public boolean hashDefinition(Class<?> aClass) {
        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        if(null == applicationContext) {
            return false;
        }
        return applicationContext.containsBeanDefinition(aClass.getName());
    }
}

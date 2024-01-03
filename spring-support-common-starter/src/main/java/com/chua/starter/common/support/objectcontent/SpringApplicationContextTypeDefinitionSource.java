package com.chua.starter.common.support.objectcontent;

import com.chua.common.support.collection.SortedArrayList;
import com.chua.common.support.collection.SortedList;
import com.chua.common.support.objects.definition.ObjectTypeDefinition;
import com.chua.common.support.objects.definition.TypeDefinition;
import com.chua.common.support.objects.source.TypeDefinitionSource;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import java.lang.annotation.Annotation;

import static com.chua.common.support.objects.source.AbstractTypeDefinitionSource.COMPARABLE;

/**
 * spring
 * @author CH
 */
public class SpringApplicationContextTypeDefinitionSource implements TypeDefinitionSource {
    @Override
    public boolean isMatch(TypeDefinition typeDefinition) {
        return false;
    }

    @Override
    public SortedList<TypeDefinition> getBean(String name, Class<?> targetType) {
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
        return new SortedArrayList<>(new ObjectTypeDefinition(name, bean), COMPARABLE);
    }

    @Override
    public SortedList<TypeDefinition> getBean(String name) {
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
        return new SortedArrayList<>(new ObjectTypeDefinition(name, bean), COMPARABLE);
    }

    @Override
    public SortedList<TypeDefinition> getBean(Class<?> targetType) {
        SortedList<TypeDefinition> rs = new SortedArrayList<>(COMPARABLE);
        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        if(null == applicationContext) {
            return rs;
        }
        String[] beanNamesForType = applicationContext.getBeanNamesForType(targetType);
        for (String s : beanNamesForType) {
            rs.add(new ObjectTypeDefinition(s, applicationContext.getBean(s, targetType)));
        }

        return rs;
    }

    @Override
    public void unregister(TypeDefinition typeDefinition) {

    }

    @Override
    public void unregister(String name) {

    }

    @Override
    public void register(TypeDefinition definition) {

    }

    @Override
    public SortedList<TypeDefinition> getBeanByMethod(Class<? extends Annotation> annotationType) {
        return SortedList.emptyList();
    }
}

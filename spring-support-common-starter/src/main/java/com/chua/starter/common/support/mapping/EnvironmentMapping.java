package com.chua.starter.common.support.mapping;

import com.chua.common.support.utils.ClassUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 环境映射
 *
 * @author CH
 */
public class EnvironmentMapping {
    private final Map<String, List<BeanField>> fieldMapping = new ConcurrentHashMap<>();
    private final Environment environment;

    private ConversionService conversionService;

    public EnvironmentMapping(Environment environment, ConversionService conversionService) {
        this.environment = environment;
        this.conversionService = conversionService;
    }

    /**
     * 注册
     *
     * @param bean     bean
     * @param beanName bean名称
     */
    public void register(Object bean, String beanName) {
        Class<?> aClass = bean.getClass();
        ReflectionUtils.doWithFields(aClass, field -> {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                return;
            }

            Value value = field.getDeclaredAnnotation(Value.class);
            if (null == value) {
                return;
            }

            ClassUtils.setAccessible(field);
            String value1 = value.value();
            String replace = value1.replace("${", "").replace("}", "");
            replace = replace.contains(":") ? replace.substring(0, replace.indexOf(":")) : replace;
            fieldMapping.computeIfAbsent(replace, it -> new LinkedList<>())
                    .add(new BeanField(field, bean, beanName));
        });
    }

    /**
     * 执行
     *
     * @param name  名称
     * @param value 值
     */
    public void execute(String name, String value) {
        if (!fieldMapping.containsKey(name)) {
            return;
        }

        List<BeanField> beanFields = fieldMapping.get(name);
        for (BeanField beanField : beanFields) {
            beanField.setValue(value);
        }
    }
    @Data
    @AllArgsConstructor
    final class BeanField {

        private Field field;

        private Object bean;

        private String beanName;

        public void setValue(String s2) {
            Class<?> type = field.getType();
            if (null == conversionService) {
                synchronized (this) {
                    if (null == conversionService) {
                        conversionService = SpringBeanUtils.getApplicationContext().getBean(ConversionService.class);
                    }
                }
            }
            Object convert = conversionService.convert(s2, type);
            if (null == convert) {
                return;
            }

            try {
                field.set(bean, convert);
            } catch (IllegalAccessException ignored) {
            }
        }
    }
}

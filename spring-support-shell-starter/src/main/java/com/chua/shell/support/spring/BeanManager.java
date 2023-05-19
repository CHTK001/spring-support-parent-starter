package com.chua.shell.support.spring;

import com.chua.starter.common.support.configuration.SpringBeanUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对象管理器
 *
 * @author CH
 */
public class BeanManager {

    private ConversionService conversionService;

    private static final BeanManager BEAN_MANAGER = new BeanManager();

    public static BeanManager getInstance() {
        return BEAN_MANAGER;
    }

    private final Map<String, List<BeanField>> fieldMapping = new ConcurrentHashMap<>();

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

            field.setAccessible(true);
            String value1 = value.value();
            String replace = value1.replace("${", "").replace("}", "");
            replace = replace.contains(":") ? replace.substring(0, replace.indexOf(":")) : replace;
            fieldMapping.computeIfAbsent(replace, it -> new LinkedList<>())
                    .add(new BeanField(field, bean, beanName));
        });
    }

    public void execute(String s1, String s2) {
        if (!fieldMapping.containsKey(s1)) {
            return;
        }

        List<BeanField> beanFields = fieldMapping.get(s1);
        for (BeanField beanField : beanFields) {
            beanField.setValue(s2);
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
                synchronized (getInstance()) {
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

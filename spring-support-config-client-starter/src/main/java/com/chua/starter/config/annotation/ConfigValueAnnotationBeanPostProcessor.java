package com.chua.starter.config.annotation;

import com.chua.common.support.utils.Md5Utils;
import com.chua.starter.common.support.processor.AnnotationInjectedBeanPostProcessor;
import com.chua.starter.config.entity.KeyValue;
import com.chua.starter.config.event.ConfigValueReceivedEvent;
import com.chua.starter.config.protocol.ProtocolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.MethodParameter;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.core.annotation.AnnotationUtils.getAnnotation;

/**
 * @author CH
 * @since 2022/7/30 11:38
 */
@Slf4j
public class ConfigValueAnnotationBeanPostProcessor extends AnnotationInjectedBeanPostProcessor<ConfigValue> implements BeanFactoryAware, EnvironmentAware, ApplicationListener<ConfigValueReceivedEvent> {
    /**
     * The name of {@link ConfigValueAnnotationBeanPostProcessor} bean
     */
    public static final String BEAN_NAME = "ConfigValueAnnotationBeanPostProcessor";

    private static final String PLACEHOLDER_PREFIX = "${";

    private static final String PLACEHOLDER_SUFFIX = "}";

    private static final String VALUE_SEPARATOR = ":";

    /**
     * placeholder, ConfigValueTarget
     */
    private Map<String, List<ConfigValueTarget>> placeholderConfigValueTargetMap
            = new HashMap<>();

    private ConfigurableListableBeanFactory beanFactory;

    private Environment environment;

    @Override
    protected Object doGetInjectedBean(ConfigValue annotation, Object bean, String beanName, Class<?> injectedType,
                                       InjectionMetadata.InjectedElement injectedElement) {
        String annotationValue = annotation.value();
        String value = beanFactory.resolveEmbeddedValue(annotationValue);

        Member member = injectedElement.getMember();
        if (member instanceof Field) {
            return convertIfNecessary((Field) member, value);
        }

        if (member instanceof Method) {
            return convertIfNecessary((Method) member, value);
        }

        return null;
    }

    @Override
    protected String buildInjectedObjectCacheKey(ConfigValue annotation, Object bean, String beanName,
                                                 Class<?> injectedType,
                                                 InjectionMetadata.InjectedElement injectedElement) {
        return bean.getClass().getName() + annotation;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "ConfigValueAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory");
        }
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        if(!(environment instanceof ConfigurableEnvironment)) {
           return;
        }
        ProtocolProvider protocolProvider = null;
        try {
            protocolProvider = beanFactory.getBeansOfType(ProtocolProvider.class).get(ProtocolProvider.class.getTypeName() + "@" + environment.getProperty("plugin.configuration.protocol", "http"));
        } catch (Exception ignored) {
        }
        if(null != protocolProvider) {
            protocolProvider.register(this);
            protocolProvider.postProcessEnvironment((ConfigurableEnvironment)environment);
        }
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, final String beanName)
            throws BeansException {

        doWithFields(bean, beanName);

        doWithMethods(bean, beanName);

        return super.postProcessBeforeInitialization(bean, beanName);
    }

    @Override
    public void onApplicationEvent(ConfigValueReceivedEvent event) {
        for (Map.Entry<String, List<ConfigValueTarget>> entry : placeholderConfigValueTargetMap.entrySet()) {
            String key = environment.resolvePlaceholders(entry.getKey());
            String newValue = environment.getProperty(key);
            if (newValue == null) {
                continue;
            }
            List<ConfigValueTarget> beanPropertyList = entry.getValue();
            for (ConfigValueTarget target : beanPropertyList) {
                String md5String = Md5Utils.getInstance().getMd5String(newValue);
                boolean isUpdate = !target.lastMD5.equals(md5String);
                if (isUpdate) {
                    target.updateLastMD5(md5String);
                    if (target.method == null) {
                        setField(target, newValue);
                    } else {
                        setMethod(target, newValue);
                    }
                }
            }
        }
    }

    public void onChange(KeyValue keyValue) {
        if (keyValue.getData() == null) {
            return;
        }
        String newValue = keyValue.getData();
        List<ConfigValueTarget> beanPropertyList = placeholderConfigValueTargetMap.get(keyValue.getDataId());
        if(null == beanPropertyList) {
            return;
        }

        for (ConfigValueTarget target : beanPropertyList) {
            String md5String = Md5Utils.getInstance().getMd5String(newValue);
            boolean isUpdate = !target.lastMD5.equals(md5String);
            if (isUpdate) {
                target.updateLastMD5(md5String);
                if (target.method == null) {
                    setField(target, newValue);
                } else {
                    setMethod(target, newValue);
                }
            }
        }
    }

    private Object convertIfNecessary(Field field, Object value) {
        TypeConverter converter = beanFactory.getTypeConverter();
        return converter.convertIfNecessary(value, field.getType(), field);
    }

    private Object convertIfNecessary(Method method, Object value) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] arguments = new Object[paramTypes.length];

        TypeConverter converter = beanFactory.getTypeConverter();

        if (arguments.length == 1) {
            return converter.convertIfNecessary(value, paramTypes[0], new MethodParameter(method, 0));
        }

        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = converter.convertIfNecessary(value, paramTypes[i], new MethodParameter(method, i));
        }

        return arguments;
    }

    private void doWithFields(final Object bean, final String beanName) {
        ReflectionUtils.doWithFields(bean.getClass(), new ReflectionUtils.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException {
                ConfigValue annotation = getAnnotation(field, ConfigValue.class);
                doWithAnnotation(beanName, bean, annotation, field.getModifiers(), null, field);
            }
        });
    }

    private void doWithMethods(final Object bean, final String beanName) {
        ReflectionUtils.doWithMethods(bean.getClass(), new ReflectionUtils.MethodCallback() {
            @Override
            public void doWith(Method method) throws IllegalArgumentException {
                ConfigValue annotation = getAnnotation(method, ConfigValue.class);
                doWithAnnotation(beanName, bean, annotation, method.getModifiers(), method, null);
            }
        });
    }

    private void doWithAnnotation(String beanName, Object bean, ConfigValue annotation, int modifiers, Method method,
                                  Field field) {
        if (annotation != null) {
            if (Modifier.isStatic(modifiers)) {
                return;
            }

            if (annotation.autoRefreshed()) {
                String placeholder = resolvePlaceholder(annotation.value());

                if (placeholder == null) {
                    return;
                }

                ConfigValueTarget configValueTarget = new ConfigValueTarget(bean, beanName, method, field);
                put2ListMap(placeholderConfigValueTargetMap, placeholder, configValueTarget);
            }
        }
    }

    private String resolvePlaceholder(String placeholder) {
        if (!placeholder.startsWith(PLACEHOLDER_PREFIX)) {
            return null;
        }

        if (!placeholder.endsWith(PLACEHOLDER_SUFFIX)) {
            return null;
        }

        if (placeholder.length() <= PLACEHOLDER_PREFIX.length() + PLACEHOLDER_SUFFIX.length()) {
            return null;
        }

        int beginIndex = PLACEHOLDER_PREFIX.length();
        int endIndex = placeholder.length() - PLACEHOLDER_PREFIX.length() + 1;
        placeholder = placeholder.substring(beginIndex, endIndex);

        int separatorIndex = placeholder.indexOf(VALUE_SEPARATOR);
        if (separatorIndex != -1) {
            return placeholder.substring(0, separatorIndex);
        }

        return placeholder;
    }

    private <K, V> void put2ListMap(Map<K, List<V>> map, K key, V value) {
        List<V> valueList = map.get(key);
        if (valueList == null) {
            valueList = new ArrayList<V>();
        }
        valueList.add(value);
        map.put(key, valueList);
    }

    private void setMethod(ConfigValueTarget configValueTarget, String propertyValue) {
        Method method = configValueTarget.method;
        ReflectionUtils.makeAccessible(method);
        try {
            method.invoke(configValueTarget.bean, convertIfNecessary(method, propertyValue));

            if (log.isDebugEnabled()) {
                log.debug("Update value with {} (method) in {} (bean) with {}",
                        method.getName(), configValueTarget.beanName, propertyValue);
            }
        } catch (Throwable e) {
            if (log.isErrorEnabled()) {
                log.error(
                        "Can't update value with " + method.getName() + " (method) in "
                                + configValueTarget.beanName + " (bean)", e);
            }
        }
    }

    private void setField(final ConfigValueTarget configValueTarget, final String propertyValue) {
        final Object bean = configValueTarget.bean;

        Field field = configValueTarget.field;

        String fieldName = field.getName();

        try {
            ReflectionUtils.makeAccessible(field);
            field.set(bean, convertIfNecessary(field, propertyValue));

            if (log.isDebugEnabled()) {
                log.debug("Update value of the {}" + " (field) in {} (bean) with {}",
                        fieldName, configValueTarget.beanName, propertyValue);
            }
        } catch (Throwable e) {
            if (log.isErrorEnabled()) {
                log.error(
                        "Can't update value of the " + fieldName + " (field) in "
                                + configValueTarget.beanName + " (bean)", e);
            }
        }
    }

    private static class ConfigValueTarget {

        private final Object bean;

        private final String beanName;

        private final Method method;

        private final Field field;

        private String lastMD5;

        ConfigValueTarget(Object bean, String beanName, Method method, Field field) {
            this.bean = bean;

            this.beanName = beanName;

            this.method = method;

            this.field = field;

            this.lastMD5 = "";
        }

        protected void updateLastMD5(String newMD5) {
            this.lastMD5 = newMD5;
        }

    }

}

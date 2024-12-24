package com.chua.report.client.starter.function;

import com.chua.common.support.invoke.annotation.RequestLine;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.protocol.server.ProtocolServer;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.client.starter.annotation.ConfigValue;
import com.chua.report.client.starter.endpoint.ModuleType;
import com.chua.report.client.starter.event.ConfigValueReceivedEvent;
import com.chua.report.client.starter.setting.SettingFactory;
import com.chua.starter.common.support.processor.AnnotationInjectedBeanPostProcessor;
import com.google.common.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.core.MethodParameter;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.springframework.core.annotation.AnnotationUtils.getAnnotation;

/**
 * 配置文件插件
 *
 * @author CH
 */
@Slf4j
public class ReportConfigValueConfiguration extends AnnotationInjectedBeanPostProcessor<ConfigValue> implements BeanFactoryAware, ApplicationListener<ConfigValueReceivedEvent>, ApplicationContextAware {

    /**
     *
     */
    public static final String BEAN_NAME = "ConfigValueAnnotationBeanPostProcessor";

    private static final String PLACEHOLDER_PREFIX = "${";

    private static final String PLACEHOLDER_SUFFIX = "}";

    private static final String VALUE_SEPARATOR = ":";

    /**
     * placeholder, ConfigValueTarget
     */
    private final Map<String, List<ConfigValueTarget>> placeholderConfigValueTargetMap
            = new HashMap<>();

    private ConfigurableListableBeanFactory beanFactory;

    private Environment environment;
    private Set<String> actives;

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
        if(SettingFactory.getInstance().isServer()) {
            return;
        }

        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        String[] beanNamesForType = this.beanFactory.getBeanNamesForType(ProtocolServer.class);
        if(beanNamesForType.length == 0) {
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        if (!(environment instanceof ConfigurableEnvironment)) {
            return;
        }

        if(!SettingFactory.getInstance().isEnable()) {
            return;
        }

        postProcessEnvironment((ConfigurableEnvironment) environment);
    }

    void postProcessEnvironment(ConfigurableEnvironment environment) {
        MutablePropertySources propertySources = environment.getPropertySources();
        try {
            register(propertySources);
        } catch (Exception ignored) {
        }

    }

    public void register(MutablePropertySources propertySources) {
        if(!SettingFactory.getInstance().isEndpointActive(ModuleType.CONFIG)) {
            return;
        }

        List<com.chua.report.client.starter.entity.ConfigValue> configValues = SettingFactory.getInstance().sendRequest(ModuleType.CONFIG,
                new TypeToken<List<com.chua.report.client.starter.entity.ConfigValue>>() {});
        log.info("CONFIG 订阅成功");
        Map<String, Object> map = new LinkedHashMap<>();
        for (com.chua.report.client.starter.entity.ConfigValue configValue : configValues) {
            if(!SettingFactory.getInstance().isProfileActive(configValue.getProfileActive())) {
                continue;
            }
            map.put(configValue.getName(), configValue.getValue());
        }
        MapPropertySource propertySource = new MapPropertySource("report-cloudy", map);
        propertySources.addFirst(propertySource);

    }


    @RequestLine("config")
    public Response listen(Request request) {
        onListener(Json.fromJson(request.getBody(), com.chua.report.client.starter.entity.ConfigValue.class));
        return Response.ok(request);
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
                String md5String = DigestUtils.md5Hex(newValue);
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

    public void onChange(com.chua.report.client.starter.entity.ConfigValue  configValue) {
        if (configValue.getValue() == null) {
            return;
        }

        if(!SettingFactory.getInstance().isProfileActive(configValue.getProfileActive())) {
            return;
        }

        String newValue = configValue.getValue();
        List<ConfigValueTarget> beanPropertyList = placeholderConfigValueTargetMap.get(configValue.getName());
        if (null == beanPropertyList) {
            return;
        }

        for (ConfigValueTarget target : beanPropertyList) {
            String md5String = DigestUtils.md5Hex(newValue);
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

                if(StringUtils.isEmpty(placeholder)) {
                    placeholder = annotation.value();
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

//    @Override
    public void onListener(com.chua.report.client.starter.entity.ConfigValue keyValue) {
        onChange(keyValue);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
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

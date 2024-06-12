package com.chua.starter.monitor.configuration;

import com.chua.common.support.function.Splitter;
import com.chua.common.support.invoke.annotation.RequestLine;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonArray;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.Md5Utils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.processor.AnnotationInjectedBeanPostProcessor;
import com.chua.starter.monitor.annotation.ConfigValue;
import com.chua.starter.monitor.entity.KeyValue;
import com.chua.starter.monitor.event.ConfigValueReceivedEvent;
import com.chua.starter.monitor.factory.MonitorFactory;
import lombok.Setter;
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
import org.springframework.core.env.*;
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
public class ConfigValueConfiguration extends AnnotationInjectedBeanPostProcessor<ConfigValue> implements BeanFactoryAware, ApplicationListener<ConfigValueReceivedEvent>, ApplicationContextAware {

    /**
     *
     */
    public static final String BEAN_NAME = "ConfigValueAnnotationBeanPostProcessor";

    private static final String PLACEHOLDER_PREFIX = "${";

    private static final String PLACEHOLDER_SUFFIX = "}";

    private static final String VALUE_SEPARATOR = ":";

    private BootProtocolServer protocolServer;
    private BootProtocolClient protocolClient;

    /**
     * placeholder, ConfigValueTarget
     */
    private final Map<String, List<ConfigValueTarget>> placeholderConfigValueTargetMap
            = new HashMap<>();

    private ConfigurableListableBeanFactory beanFactory;

    private Environment environment;
    @Setter
    private ApplicationContext applicationContext;
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
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        String[] beanNamesForType = this.beanFactory.getBeanNamesForType(BootProtocolServer.class);
        if(beanNamesForType.length == 0) {
            return;
        }
        if(!MonitorFactory.getInstance().isEnable()) {
            return;
        }
        this.protocolServer = this.beanFactory.getBean(BootProtocolServer.class);
        this.protocolClient = this.beanFactory.getBean(BootProtocolClient.class);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        if(null == this.protocolServer) {
            return;
        }
        if (!(environment instanceof ConfigurableEnvironment)) {
            return;
        }

        if(!MonitorFactory.getInstance().isEnable()) {
            return;
        }


        String active = MonitorFactory.getInstance().getActive();
        if(StringUtils.isNotBlank(active)) {
            this.actives = Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(active);
        }
        postProcessEnvironment((ConfigurableEnvironment) environment);
    }

    void postProcessEnvironment(ConfigurableEnvironment environment) {
        MutablePropertySources propertySources = environment.getPropertySources();
        try {
            register(environment, propertySources);
        } catch (Exception ignored) {
        }

    }

    public void register(ConfigurableEnvironment environment, MutablePropertySources propertySources) {
        doInjectSubscribe(propertySources);
    }


    @RequestLine("config")
    public BootResponse listen(BootRequest request) {
        if(request.getCommandType() != CommandType.REGISTER) {
            return BootResponse.notSupport("The non-register command is not supported");
        }

        KeyValue keyValue = new KeyValue();
        JsonObject jsonObject = Json.getJsonObject(request.getContent());
        keyValue.setDataId(jsonObject.getString("configName"));
        keyValue.setData(jsonObject.getString("configValue"));
        keyValue.setProfile(jsonObject.getString("configProfile"));
        onListener(keyValue);
        return BootResponse.ok();
    }

    private void doInjectSubscribe(MutablePropertySources propertySources) {
        this.protocolServer.addMapping(this);
        if(!MonitorFactory.getInstance().containsKey("CONFIG")) {
            return;
        }
        BootResponse response = protocolClient.get(BootRequest.builder()
                        .moduleType("CONFIG")
                        .commandType(CommandType.SUBSCRIBE)
                        .appName(MonitorFactory.getInstance().getAppName())
                        .profile(MonitorFactory.getInstance().getActive())
                        .content(MonitorFactory.getInstance().getSubscribeApps())
                .build()
        );
        if(response.getCommandType() != CommandType.RESPONSE) {
            return;
        }

        log.info("CONFIG 订阅成功");
        JsonArray jsonArray = Json.getJsonArray(MapUtils.getString(response.getData(), "data"));
        int size = jsonArray.size();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            register(jsonArray.getJsonObject(i), map);
        }
        PropertySource propertySource = new MapPropertySource("monitor-cloud", map);
        propertySources.addFirst(propertySource);

    }

    private void register(JsonObject jsonObject, Map<String, Object> map) {
        map.put(jsonObject.getString("unifiedConfigName"), jsonObject.get("unifiedConfigValue"));
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

        if(CollectionUtils.isNotEmpty(actives) ) {
            if(StringUtils.isBlank(keyValue.getProfile())) {
                return;
            }

            if(!actives.contains(keyValue.getProfile())) {
                return;
            }
        }

        String newValue = keyValue.getData();
        List<ConfigValueTarget> beanPropertyList = placeholderConfigValueTargetMap.get(keyValue.getDataId());
        if (null == beanPropertyList) {
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
    public void onListener(KeyValue keyValue) {
        onChange(keyValue);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
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

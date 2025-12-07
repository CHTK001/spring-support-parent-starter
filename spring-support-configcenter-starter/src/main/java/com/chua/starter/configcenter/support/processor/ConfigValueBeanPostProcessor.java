package com.chua.starter.configcenter.support.processor;

import com.chua.common.support.config.ConfigCenter;
import com.chua.common.support.config.ConfigListener;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.objects.annotation.ConfigValue;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ConfigValue注解Bean后置处理器
 * <p>
 * 在Bean初始化后扫描@ConfigValue注解，注入配置值并支持热更新。
 * </p>
 *
 * <h3>功能</h3>
 * <ul>
 *     <li>扫描@ConfigValue注解字段和方法</li>
 *     <li>从Environment中获取配置值并注入</li>
 *     <li>注册热更新监听器</li>
 *     <li>配置变化时自动更新字段值</li>
 * </ul>
 *
 * @author CH
 * @since 2024-12-05
 * @version 1.0.0
 */
@Slf4j
public class ConfigValueBeanPostProcessor implements BeanPostProcessor, EnvironmentAware {

    /**
     * 占位符正则表达式
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    /**
     * Spring环境
     */
    private Environment environment;

    /**
     * 配置中心
     */
    private final ConfigCenter configCenter;

    /**
     * 是否启用热更新
     */
    private final boolean hotReloadEnabled;

    /**
     * 绑定信息映射
     * key: 配置键
     * value: 绑定信息列表
     */
    private final Map<String, List<BindingInfo>> bindingsByKey = new ConcurrentHashMap<>();

    /**
     * 已注册监听的配置键
     */
    private final Set<String> registeredListeners = Collections.synchronizedSet(new HashSet<>());

    /**
     * 构造函数
     *
     * @param configCenter 配置中心
     */
    public ConfigValueBeanPostProcessor(ConfigCenter configCenter) {
        this(configCenter, true);
    }

    /**
     * 构造函数
     *
     * @param configCenter     配置中心
     * @param hotReloadEnabled 是否启用热更新
     */
    public ConfigValueBeanPostProcessor(ConfigCenter configCenter, boolean hotReloadEnabled) {
        this.configCenter = configCenter;
        this.hotReloadEnabled = hotReloadEnabled;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();

        // 扫描字段
        scanFields(bean, beanName, clazz);

        // 扫描方法
        scanMethods(bean, beanName, clazz);

        return bean;
    }

    /**
     * 扫描字段
     *
     * @param bean     Bean实例
     * @param beanName Bean名称
     * @param clazz    类
     */
    private void scanFields(Object bean, String beanName, Class<?> clazz) {
        for (Field field : getAllFields(clazz)) {
            ConfigValue annotation = field.getAnnotation(ConfigValue.class);
            if (annotation == null) {
                continue;
            }

            String expression = annotation.value();
            ParsedExpression parsed = parseExpression(expression);

            // 注入初始值
            String value = resolveValue(parsed.key, parsed.defaultValue);
            injectFieldValue(bean, field, value);

            // 如果支持热更新，注册监听
            if (annotation.hotReload()) {
                BindingInfo binding = BindingInfo.builder()
                        .configKey(parsed.key)
                        .defaultValue(parsed.defaultValue)
                        .bean(bean)
                        .beanName(beanName)
                        .field(field)
                        .callback(annotation.callback())
                        .targetType(field.getType())
                        .build();

                registerBinding(binding);
                log.debug("注册热更新绑定: key={}, bean={}, field={}",
                        parsed.key, beanName, field.getName());
            }
        }
    }

    /**
     * 扫描方法
     *
     * @param bean     Bean实例
     * @param beanName Bean名称
     * @param clazz    类
     */
    private void scanMethods(Object bean, String beanName, Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            ConfigValue annotation = method.getAnnotation(ConfigValue.class);
            if (annotation == null || method.getParameterCount() != 1) {
                continue;
            }

            String expression = annotation.value();
            ParsedExpression parsed = parseExpression(expression);

            // 注入初始值
            String value = resolveValue(parsed.key, parsed.defaultValue);
            injectMethodValue(bean, method, value);

            // 如果支持热更新，注册监听
            if (annotation.hotReload()) {
                BindingInfo binding = BindingInfo.builder()
                        .configKey(parsed.key)
                        .defaultValue(parsed.defaultValue)
                        .bean(bean)
                        .beanName(beanName)
                        .method(method)
                        .callback(annotation.callback())
                        .targetType(method.getParameterTypes()[0])
                        .build();

                registerBinding(binding);
                log.debug("注册热更新绑定: key={}, bean={}, method={}",
                        parsed.key, beanName, method.getName());
            }
        }
    }

    /**
     * 注册绑定并添加监听
     *
     * @param binding 绑定信息
     */
    private void registerBinding(BindingInfo binding) {
        String configKey = binding.configKey;

        bindingsByKey.computeIfAbsent(configKey, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(binding);

        // 注册配置中心监听（只有启用热更新时才注册）
        if (hotReloadEnabled && configCenter != null && configCenter.isSupportListener() 
                && !registeredListeners.contains(configKey)) {
            configCenter.addListener(configKey, new ConfigValueListener(configKey));
            registeredListeners.add(configKey);
            log.info("【配置中心】注册配置监听: key={}", configKey);
        }
    }

    /**
     * 解析配置值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    private String resolveValue(String key, String defaultValue) {
        if (environment != null) {
            String value = environment.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    /**
     * 注入字段值
     *
     * @param bean  Bean实例
     * @param field 字段
     * @param value 值
     */
    private void injectFieldValue(Object bean, Field field, String value) {
        try {
            field.setAccessible(true);
            Object convertedValue = convertValue(value, field.getType());
            field.set(bean, convertedValue);
        } catch (Exception e) {
            log.error("注入配置值失败: field={}", field.getName(), e);
        }
    }

    /**
     * 注入方法值
     *
     * @param bean   Bean实例
     * @param method 方法
     * @param value  值
     */
    private void injectMethodValue(Object bean, Method method, String value) {
        try {
            method.setAccessible(true);
            Object convertedValue = convertValue(value, method.getParameterTypes()[0]);
            method.invoke(bean, convertedValue);
        } catch (Exception e) {
            log.error("注入配置值失败: method={}", method.getName(), e);
        }
    }

    /**
     * 转换值类型
     *
     * @param value      字符串值
     * @param targetType 目标类型
     * @return 转换后的值
     */
    private Object convertValue(String value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        return Converter.convertIfNecessary(value, targetType);
    }

    /**
     * 解析占位符表达式
     *
     * @param expression 表达式
     * @return 解析结果
     */
    private ParsedExpression parseExpression(String expression) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(expression);
        if (matcher.find()) {
            String key = matcher.group(1);
            String defaultValue = matcher.group(2);
            return new ParsedExpression(key, defaultValue);
        }
        return new ParsedExpression(expression, null);
    }

    /**
     * 获取类的所有字段
     *
     * @param clazz 类
     * @return 字段列表
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    // ==================== 内部类 ====================

    /**
     * 配置值监听器
     */
    private class ConfigValueListener implements ConfigListener {

        private final String configKey;

        ConfigValueListener(String configKey) {
            this.configKey = configKey;
        }

        @Override
        public void onChange(String key, String oldValue, String newValue) {
            // 由 onUpdate 处理
        }

        @Override
        public void onDelete(String key, String oldValue) {
            List<BindingInfo> bindings = bindingsByKey.get(configKey);
            if (bindings == null) {
                return;
            }

            for (BindingInfo binding : bindings) {
                // 使用默认值
                updateBinding(binding, oldValue, binding.defaultValue);
            }
        }

        @Override
        public void onUpdate(String key, String oldValue, String newValue) {
            List<BindingInfo> bindings = bindingsByKey.get(configKey);
            if (bindings == null) {
                return;
            }

            for (BindingInfo binding : bindings) {
                updateBinding(binding, oldValue, newValue);
            }
        }

        /**
         * 更新绑定值
         *
         * @param binding  绑定信息
         * @param oldValue 旧值
         * @param newValue 新值
         */
        private void updateBinding(BindingInfo binding, String oldValue, String newValue) {
            Object oldConvertedValue = null;

            // 获取旧值
            if (binding.field != null) {
                try {
                    binding.field.setAccessible(true);
                    oldConvertedValue = binding.field.get(binding.bean);
                } catch (Exception e) {
                    log.error("获取旧值失败", e);
                }
            }

            // 更新值
            if (binding.field != null) {
                injectFieldValue(binding.bean, binding.field, newValue);
            } else if (binding.method != null) {
                injectMethodValue(binding.bean, binding.method, newValue);
            }

            log.info("热更新配置: key={}, bean={}, oldValue={}, newValue={}",
                    configKey, binding.beanName, oldValue, newValue);

            // 调用回调
            invokeCallback(binding, oldConvertedValue, newValue);
        }

        /**
         * 调用回调方法
         *
         * @param binding  绑定信息
         * @param oldValue 旧值
         * @param newValue 新值
         */
        private void invokeCallback(BindingInfo binding, Object oldValue, String newValue) {
            String callbackName = binding.callback;
            if (StringUtils.isEmpty(callbackName)) {
                return;
            }

            try {
                Method callback = binding.bean.getClass().getDeclaredMethod(
                        callbackName, String.class, Object.class, Object.class);
                callback.setAccessible(true);
                Object convertedNewValue = convertValue(newValue, binding.targetType);
                callback.invoke(binding.bean, configKey, oldValue, convertedNewValue);
            } catch (NoSuchMethodException e) {
                log.warn("回调方法不存在: {}.{}", binding.beanName, callbackName);
            } catch (Exception e) {
                log.error("调用回调方法失败: {}.{}", binding.beanName, callbackName, e);
            }
        }
    }

    /**
     * 绑定信息
     */
    @lombok.Builder
    private static class BindingInfo {
        String configKey;
        String defaultValue;
        Object bean;
        String beanName;
        Field field;
        Method method;
        String callback;
        Class<?> targetType;
    }

    /**
     * 解析后的表达式
     */
    private static class ParsedExpression {
        final String key;
        final String defaultValue;

        ParsedExpression(String key, String defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }
    }
}

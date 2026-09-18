package com.chua.starter.configcenter.support.processor;

import com.chua.common.support.config.center.ConfigCenter;
import com.chua.common.support.config.center.ConfigListener;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* @价值观注解Bean后置处理器
* <p>
* 在Bean初始化后扫描@价值观注解，注入配置值并支持热更新和配置缓存。
* </p>
*
* <h3>功能</h3>
* <ul>
*     <li>扫描@Value注解字段和方法</li>
*     <li>从Environment中获取配置值并注入</li>
*     <li>缓存配置值，提高性能</li>
*     <li>注册热更新监听器</li>
*     <li>配置变化时自动更新字段值</li>
* </ul>
*
* @since 2024-12-07
* @版本 1.0.0
* @author CH
 */
@Slf4j
public class ValueAnnotationBeanPostProcessor implements BeanPostProcessor, EnvironmentAware, BeanFactoryAware {

    /**
    * 占位符正则表达式
    */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    /**
    * 默认配置刷新延迟时间（毫秒）
    */
    private static final long DEFAULT_REFRESH_DELAY_MS = 100L;

    /**
    * Spring环境
    */
    private Environment environment;

    /**
    * Bean工厂
    */
    private ConfigurableListableBeanFactory beanFactory;

    /**
    * 配置中心
    */
    private final ConfigCenter configCenter;

    /**
    * 是否启用热更新
    */
    private final boolean hotReloadEnabled;

    /**
    * 配置值缓存
    * 钥匙: 配置键
    * 价值观: 缓存的配置值
    */
    private final Map<String, String> configValueCache = new ConcurrentHashMap<>();

    /**
    * 绑定信息映射
    * 钥匙: 配置键
    * 价值观: 绑定信息列表
    */
    private final Map<String, List<ValueBindingInfo>> bindingsByKey = new ConcurrentHashMap<>();

    /**
    * 已注册监听的配置键
    */
    private final Set<String> registeredListeners = Collections.synchronizedSet(new HashSet<>());

    /**
    * 配置变更后的延迟刷新时间（毫秒）
    */
    private final long refreshDelayMs;

    /**
    * 配置刷新时间缓存
    * 钥匙: 配置键
    * 价值观: 上次刷新时间戳
    */
    private final Map<String, Long> lastRefreshTimeByKey = new ConcurrentHashMap<>();

    /**
    * 构造函数
    *
    * @param configCenter 配置中心
    */
    public ValueAnnotationBeanPostProcessor(ConfigCenter configCenter) {
        /**
        * this。
        * @param configCenter 配置center
        * @param true true
        * @param DEFAULT_REFRESH_DELAY_MS 默认refresh延迟ms
        */
        this(configCenter, true, DEFAULT_REFRESH_DELAY_MS);
    }

    /**
    * 构造函数
    *
    * @param configCenter     配置中心
    * @param hotReloadEnabled 是否启用热更新
    */
    public ValueAnnotationBeanPostProcessor(ConfigCenter configCenter, boolean hotReloadEnabled) {
        /**
        * this。
        * @param configCenter 配置center
        * @param hotReloadEnabled hotreload已启用
        * @param DEFAULT_REFRESH_DELAY_MS 默认refresh延迟ms
        */
        this(configCenter, hotReloadEnabled, DEFAULT_REFRESH_DELAY_MS);
    }

    /**
    * 构造函数
    *
    * @param configCenter     配置中心
    * @param hotReloadEnabled 是否启用热更新
    * @param refreshDelayMs   配置变更后的延迟刷新时间（毫秒）
    */
    public ValueAnnotationBeanPostProcessor(ConfigCenter configCenter, boolean hotReloadEnabled, long refreshDelayMs) {
        this.configCenter = configCenter;
        this.hotReloadEnabled = hotReloadEnabled;
        this.refreshDelayMs = refreshDelayMs;
    }

    @Override
    /**
    * 设置环境
    * @param environment 环境
    */
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    /**
    * 设置Bean工厂
    * @param beanFactory Bean工厂
    */
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof ConfigurableListableBeanFactory) {
            this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        }
    }

    @Override
    /**
    * Post处理之后initialization
    * @param bean Bean
    * @param beanName Bean名称
    */
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (beanFactory != null && !beanFactory.isSingleton(beanName)) {
            return bean;
        }

        Class<?> clazz = bean.getClass();

        // 扫描字段
        /**
        * 扫描字段。
        * @param bean Bean
        * @param beanName Bean名称
        * @param clazz clazz
        */
        scanFields(bean, beanName, clazz);

        // 扫描方法
        /**
        * 扫描方法。
        * @param bean Bean
        * @param beanName Bean名称
        * @param clazz clazz
        */
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
        ReflectionUtils.doWithFields(clazz, field -> {
            Value annotation = field.getAnnotation(Value.class);
            if (annotation == null) {
                return;
            }

            String expression = annotation.value();
            ParsedExpression parsed = parseExpression(expression);

 // 从缓存或环境中获取值
            String value = getCachedOrResolveValue(parsed.key(), parsed.defaultValue());
            
            // 注入初始值
            /**
            * inject字段价值观。
            * @param bean Bean
            * @param field 字段
            * @param value 价值观
            */
            injectFieldValue(bean, field, value);

            // 如果支持热更新，注册监听
            if (hotReloadEnabled) {
                ValueBindingInfo binding = new ValueBindingInfo(
                        parsed.key(),
                        parsed.defaultValue(),
                        bean,
                        beanName,
                        field,
                        null,
                        field.getType()
                );

                /**
                * 注册binding。
                * @param binding binding
                */
                registerBinding(binding);
                log.debug("注册@Value热更新绑定: key={}, bean={}, field={}",
                        parsed.key(), beanName, field.getName());
            }
        });
    }

    /**
    * 扫描方法
    *
    * @param bean     Bean实例
    * @param beanName Bean名称
    * @param clazz    类
    */
    private void scanMethods(Object bean, String beanName, Class<?> clazz) {
        ReflectionUtils.doWithMethods(clazz, method -> {
            Value annotation = method.getAnnotation(Value.class);
            if (annotation == null || method.getParameterCount() != 1) {
                return;
            }

            String expression = annotation.value();
            ParsedExpression parsed = parseExpression(expression);

 // 从缓存或环境中获取值
            String value = getCachedOrResolveValue(parsed.key(), parsed.defaultValue());
            
            // 注入初始值
            /**
            * inject方法价值观。
            * @param bean Bean
            * @param method 方法
            * @param value 价值观
            */
            injectMethodValue(bean, method, value);

            // 如果支持热更新，注册监听
            if (hotReloadEnabled) {
                ValueBindingInfo binding = new ValueBindingInfo(
                        parsed.key(),
                        parsed.defaultValue(),
                        bean,
                        beanName,
                        null,
                        method,
                        method.getParameterTypes()[0]
                );

                /**
                * 注册binding。
                * @param binding binding
                */
                registerBinding(binding);
                log.debug("注册@Value热更新绑定: key={}, bean={}, method={}",
                        parsed.key(), beanName, method.getName());
            }
        });
    }

    /**
    * 从缓存或环境中获取配置值
    *
    * @param key          配置键
    * @param defaultValue 默认值
    * @return 配置值
    */
    private String getCachedOrResolveValue(String key, String defaultValue) {
        // 先从缓存获取
        String cachedValue = configValueCache.get(key);
        if (cachedValue != null) {
            return cachedValue;
        }

 // 从环境获取
        String value = null;
        if (environment != null) {
            value = environment.getProperty(key);
        }

 // 如果值为空，使用默认值
        if (value == null) {
            value = defaultValue;
        }

        // 缓存配置值
        if (value != null) {
            configValueCache.put(key, value);
        }

        return value;
    }

    /**
    * 注册绑定并添加监听
    *
    * @param binding 绑定信息
    */
    private void registerBinding(ValueBindingInfo binding) {
        String configKey = binding.configKey;

        bindingsByKey.computeIfAbsent(configKey, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(binding);

        // 注册配置中心监听（只有启用热更新时才注册）
        if (hotReloadEnabled && configCenter != null && configCenter.isSupportListener() 
                && !registeredListeners.contains(configKey)) {
            configCenter.addListener(configKey, new ValueConfigListener(configKey));
            registeredListeners.add(configKey);
            log.info("[配置中心]注册@Value配置监听: key={}", configKey);
        }
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
            Object convertedValue = convertValue(value, field.getType());
            boolean ok = ReflectUtils.setField(bean, field.getName(), convertedValue);
            if (!ok) {
                log.error("注入@Value配置值失败: field={}", field.getName());
            }
        } catch (Exception e) {
            log.error("注入@Value配置值失败: field={}", field.getName(), e);
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
            Object convertedValue = convertValue(value, method.getParameterTypes()[0]);
            Object result = ReflectUtils.invoke(bean, method.getName(), method.getReturnType(),
                    method.getParameterTypes(), convertedValue);
            if (result == null) {
                log.error("注入@Value配置值失败: method={}", method.getName());
            }
        } catch (Exception e) {
            log.error("注入@Value配置值失败: method={}", method.getName(), e);
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
            /**
            * 解析expression。
            * @param key 钥匙
            * @param defaultValue 默认价值观
            * @return 解析expression的结果
            */
            return new ParsedExpression(key, defaultValue);
        }
        /**
        * 解析expression。
        * @param expression expression
        * @param null 空
        * @return 解析expression的结果
        */
        return new ParsedExpression(expression, null);
    }

    /**
    * 更新配置值缓存
    *
    * @param key   配置键
    * @param value 配置值
    */
    private void updateCache(String key, String value) {
        if (value != null) {
            configValueCache.put(key, value);
        } else {
            configValueCache.remove(key);
        }
    }

    // ==================== 内部类 ====================

    /**
    * 配置值监听器
    * @author CH
    * @since 4.0.0
    */
    private class ValueConfigListener implements ConfigListener {

        /** 配置密钥 */
        private final String configKey;

        /**
        * 价值观配置监听器。
        * @param configKey 配置钥匙
        */
        ValueConfigListener(String configKey) {
            this.configKey = configKey;
        }

        @Override
        /**
        * on改变
        * @param key 钥匙
        * @param oldValue 旧价值观
        * @param newValue 新价值观
        */
        public void onChange(String key, String oldValue, String newValue) {
 // 由 on更新 处理
        }

        @Override
        /**
        * On删除
        * @param key 钥匙
        * @param oldValue 旧价值观
        */
        public void onDelete(String key, String oldValue) {
            List<ValueBindingInfo> bindings = bindingsByKey.get(configKey);
            if (bindings == null) {
                return;
            }

            // 更新缓存
            /**
            * 更新缓存。
            * @param configKey 配置钥匙
            * @param null 空
            */
            updateCache(configKey, null);

            for (ValueBindingInfo binding : bindings) {
                // 使用默认值
                /**
                * 更新binding。
                * @param binding binding
                * @param oldValue 旧价值观
                * @param binding.defaultValue binding.默认价值观
                */
                updateBinding(binding, oldValue, binding.defaultValue);
            }
        }

        @Override
        /**
        * On更新
        * @param key 钥匙
        * @param oldValue 旧价值观
        * @param newValue 新价值观
        */
        public void onUpdate(String key, String oldValue, String newValue) {
            List<ValueBindingInfo> bindings = bindingsByKey.get(configKey);
            if (bindings == null) {
                return;
            }

            long now = System.currentTimeMillis();
            Long lastRefreshTime = lastRefreshTimeByKey.get(configKey);
            if (lastRefreshTime != null && now - lastRefreshTime < refreshDelayMs) {
                if (log.isDebugEnabled()) {
                    log.debug("配置更新过于频繁，跳过本次@Value更新: key={}", configKey);
                }
                return;
            }
            lastRefreshTimeByKey.put(configKey, now);

            // 更新缓存
            /**
            * 更新缓存。
            * @param configKey 配置钥匙
            * @param newValue 新价值观
            */
            updateCache(configKey, newValue);

 // 从环境重新获取值（可能包含占位符解析）
            String resolvedValue = null;
            if (environment != null) {
                resolvedValue = environment.getProperty(configKey);
            }
            if (resolvedValue == null) {
                resolvedValue = newValue;
            }

            for (ValueBindingInfo binding : bindings) {
                /**
                * 更新binding。
                * @param binding binding
                * @param oldValue 旧价值观
                * @param resolvedValue resolved价值观
                */
                updateBinding(binding, oldValue, resolvedValue);
            }
        }

        /**
        * 更新绑定值
        *
        * @param binding  绑定信息
        * @param oldValue 旧值
        * @param newValue 新值
        */
        private void updateBinding(ValueBindingInfo binding, String oldValue, String newValue) {
            Object oldConvertedValue = null;

            // 获取旧值
            if (binding.field() != null) {
                oldConvertedValue = ReflectUtils.getField(binding.bean(), binding.field().getName());
            }

            // 更新值
            if (binding.field() != null) {
                /**
                * inject字段价值观。
                * @param binding.bean( binding.Bean(
                */
                injectFieldValue(binding.bean(), binding.field(), newValue);
            } else if (binding.method() != null) {
                /**
                * inject方法价值观。
                * @param binding.bean( binding.Bean(
                */
                injectMethodValue(binding.bean(), binding.method(), newValue);
            }

            log.info("热更新@Value配置: key={}, bean={}, oldValue={}, newValue={}",
                    configKey, binding.beanName(), oldValue, newValue);
        }
    }

    /**
    * 绑定信息
    */
    @lombok.Builder
    private record ValueBindingInfo(String configKey,
                                    String defaultValue,
                                    Object bean,
                                    String beanName,
                                    Field field,
                                    Method method,
                                    Class<?> targetType) {
    }

    /**
    * 解析后的表达式
    * @param key 钥匙
    * @param defaultValue 默认价值观
    * @return 解析expression的结果
    */
    private record ParsedExpression(String key, String defaultValue) {
    }
}

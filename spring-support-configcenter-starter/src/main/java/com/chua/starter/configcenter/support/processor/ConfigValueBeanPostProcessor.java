package com.chua.starter.configcenter.support.processor;

import com.chua.common.support.config.center.ConfigCenter;
import com.chua.common.support.config.center.ConfigListener;
import com.chua.common.support.objects.annotation.ConfigValue;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置值注解Bean后置处理器
 * <p>
 * 在Bean初始化后扫描@配置值注解，注入配置值并支持热更新和配置推送。
 * </p>
 *
 * <h3>功能</h3>
 * <ul>
 *     <li>扫描@ConfigValue注解字段和方法</li>
 *     <li>从Environment中获取配置值并注入</li>
 *     <li>注册热更新监听器</li>
 *     <li>配置变化时自动更新字段值</li>
 *     <li>支持配置推送到配置中心（强制推送或仅当不存在时推送）</li>
 * </ul>
 *
 * @since 2024-12-05
 * @author CH
 */
@Slf4j
public class ConfigValueBeanPostProcessor implements BeanPostProcessor, EnvironmentAware, BeanFactoryAware {

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
     * 配置变更后的延迟刷新时间（毫秒）
     */
    private final long refreshDelayMs;

    /**
     * 是否在配置变更时打印日志
     */
    private final boolean logOnChange;

    /**
     * 绑定信息映射
     * Key: 配置键
     * 值: 绑定信息列表
     */
    private final Map<String, List<BindingInfo>> bindingsByKey = new ConcurrentHashMap<>();

    /**
     * 已注册监听的配置键
     */
    private final Set<String> registeredListeners = Collections.synchronizedSet(new HashSet<>());

    /**
     * 配置值缓存
     * Key: 配置键
     * 值: 缓存的配置值
     */
    private final Map<String, String> configValueCache = new ConcurrentHashMap<>();

    /**
     * 配置刷新时间缓存
     * Key: 配置键
     * 值: 上次刷新时间戳
     */
    private final Map<String, Long> lastRefreshTimeByKey = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param configCenter 配置中心
     */
    public ConfigValueBeanPostProcessor(ConfigCenter configCenter) {
        this(configCenter, true, DEFAULT_REFRESH_DELAY_MS, true);
    }

    /**
     * 构造函数
     *
     * @param configCenter     配置中心
     * @param hotReloadEnabled 是否启用热更新
     */
    public ConfigValueBeanPostProcessor(ConfigCenter configCenter, boolean hotReloadEnabled) {
        this(configCenter, hotReloadEnabled, DEFAULT_REFRESH_DELAY_MS, true);
    }

    /**
     * 构造函数
     *
     * @param configCenter     配置中心
     * @param hotReloadEnabled 是否启用热更新
     * @param refreshDelayMs   配置变更后的延迟刷新时间（毫秒）
     * @param logOnChange      是否在配置变更时打印日志
     */
    public ConfigValueBeanPostProcessor(ConfigCenter configCenter, boolean hotReloadEnabled, long refreshDelayMs,
                                        boolean logOnChange) {
        this.configCenter = configCenter;
        this.hotReloadEnabled = hotReloadEnabled;
        this.refreshDelayMs = refreshDelayMs;
        this.logOnChange = logOnChange;
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
     * @return 返回值
     * @throws BeansException 异常
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
     * @return 返回值
     * @throws BeansException 异常
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
        for (Field field : getAllFields(clazz)) {
            ConfigValue annotation = field.getAnnotation(ConfigValue.class);
            if (annotation == null) {
                continue;
            }

            String expression = annotation.value();
            ParsedExpression parsed = parseExpression(expression);

            // 注入初始值
            String value = resolveValue(parsed.key(), parsed.defaultValue());
            /**
             * inject字段值。
             * @param bean Bean
             * @param field 字段
             * @param value 值
             */
            injectFieldValue(bean, field, value);

            // 如果支持热更新，注册监听
            if (annotation.hotReload()) {
                BindingInfo binding = new BindingInfo(
                        parsed.key(),
                        parsed.defaultValue(),
                        bean,
                        beanName,
                        field,
                        null,
                        annotation.callback(),
                        field.getType()
                );

                /**
                 * 注册binding。
                 * @param binding binding
                 */
                registerBinding(binding);
                log.debug("注册热更新绑定: key={}, bean={}, field={}",
                        parsed.key(), beanName, field.getName());
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
            String value = resolveValue(parsed.key(), parsed.defaultValue());
            /**
             * inject方法值。
             * @param bean Bean
             * @param method 方法
             * @param value 值
             */
            injectMethodValue(bean, method, value);

            // 如果支持热更新，注册监听
            if (annotation.hotReload()) {
                BindingInfo binding = new BindingInfo(
                        parsed.key(),
                        parsed.defaultValue(),
                        bean,
                        beanName,
                        null,
                        method,
                        annotation.callback(),
                        method.getParameterTypes()[0]
                );

                /**
                 * 注册binding。
                 * @param binding binding
                 */
                registerBinding(binding);
                log.debug("注册热更新绑定: key={}, bean={}, method={}",
                        parsed.key(), beanName, method.getName());
            }
        }
    }

    /**
     * 注册绑定并添加监听
     *
     * @param binding 绑定信息
     */
    private void registerBinding(BindingInfo binding) {
        String configKey = binding.configKey();

        bindingsByKey.computeIfAbsent(configKey, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(binding);

        // 注册配置中心监听（只有启用热更新时才注册）
        if (hotReloadEnabled && configCenter != null && configCenter.isSupportListener() 
                && !registeredListeners.contains(configKey)) {
            configCenter.addListener(configKey, new ConfigValueListener(configKey));
            registeredListeners.add(configKey);
            log.info("[配置中心]注册配置监听: key={}", configKey);
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
                log.error("注入配置值失败: field={}", field.getName());
            }
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
            Object convertedValue = convertValue(value, method.getParameterTypes()[0]);
            Object result = ReflectUtils.invoke(bean, method.getName(), method.getReturnType(),
                    method.getParameterTypes(), convertedValue);
            if (result == null) {
                log.error("注入配置值失败: method={}", method.getName());
            }
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
            /**
             * 解析expression。
             * @param key Key
             * @param defaultValue 默认值
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
     * @author CH
     * @since 4.0.0
     */
    private class ConfigValueListener implements ConfigListener {

        /**
         * 配置密钥
        */
        private final String configKey;

        /**
         * 配置值监听器。
         * @param configKey 配置Key
         * @return 返回值
        */
        ConfigValueListener(String configKey) {
            this.configKey = configKey;
        }

        @Override
        /**
         * on改变
         * @param key Key
         * @param oldValue 旧值
         * @param newValue 新值
         */
        public void onChange(String key, String oldValue, String newValue) {
 // 由 on更新 处理
        }

        @Override
        /**
         * On删除
         * @param key Key
         * @param oldValue 旧值
         */
        public void onDelete(String key, String oldValue) {
            List<BindingInfo> bindings = bindingsByKey.get(configKey);
            if (bindings == null) {
                return;
            }

            // 更新缓存
            /**
             * 更新缓存。
             * @param configKey 配置Key
             * @param null 空
             */
            updateCache(configKey, null);

            for (BindingInfo binding : bindings) {
                // 使用默认值
                /**
                 * 更新binding。
                 * @param binding binding
                 * @param oldValue 旧值
                 * @param binding.defaultValue( binding.默认值(
                 */
                updateBinding(binding, oldValue, binding.defaultValue());
            }
        }

        @Override
        /**
         * On更新
         * @param key Key
         * @param oldValue 旧值
         * @param newValue 新值
         */
        public void onUpdate(String key, String oldValue, String newValue) {
            List<BindingInfo> bindings = bindingsByKey.get(configKey);
            if (bindings == null) {
                return;
            }

            long now = System.currentTimeMillis();
            Long lastRefreshTime = lastRefreshTimeByKey.get(configKey);
            if (lastRefreshTime != null && now - lastRefreshTime < refreshDelayMs) {
                if (log.isDebugEnabled()) {
                    log.debug("配置更新过于频繁，跳过本次@ConfigValue更新: key={}", configKey);
                }
                return;
            }
            lastRefreshTimeByKey.put(configKey, now);

            // 更新缓存
            /**
             * 更新缓存。
             * @param configKey 配置Key
             * @param newValue 新值
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

            for (BindingInfo binding : bindings) {
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
        private void updateBinding(BindingInfo binding, String oldValue, String newValue) {
            Object oldConvertedValue = null;

            // 获取旧值
            if (binding.field() != null) {
                oldConvertedValue = ReflectUtils.getField(binding.bean(), binding.field().getName());
            }

            // 更新值
            if (binding.field() != null) {
                injectFieldValue(binding.bean(), binding.field(), newValue);
            } else if (binding.method() != null) {
                injectMethodValue(binding.bean(), binding.method(), newValue);
            }

            if (logOnChange && log.isInfoEnabled()) {
                log.info("热更新@ConfigValue配置: key={}, bean={}, oldValue={}, newValue={}",
                        configKey, binding.beanName(), oldValue, newValue);
            }

            // 调用回调
            /**
             * invokecallback。
             * @param binding binding
             * @param oldConvertedValue 旧转换值
             * @param newValue 新值
             */
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
            String callbackName = binding.callback();
            if (StringUtils.isEmpty(callbackName)) {
                return;
            }

            try {
 // 预检方法是否存在（框架签名兼容，仅做存在性校验），方法查找统一经 ReflectUtils
                if (ReflectUtils.findDeclaredMethod(binding.bean().getClass(), callbackName,
                        String.class, Object.class, Object.class) == null) {
                    log.warn("回调方法不存在: {}.{}", binding.beanName(), callbackName);
                    return;
                }
                Object convertedNewValue = convertValue(newValue, binding.targetType());
                Object result = ReflectUtils.invoke(binding.bean(), callbackName,
                        Object.class, new Class<?>[]{String.class, Object.class, Object.class},
                        configKey, oldValue, convertedNewValue);
                if (result == null) {
                    log.warn("回调方法不存在: {}.{}", binding.beanName(), callbackName);
                }
            } catch (Exception e) {
                log.error("调用回调方法失败: {}.{}", binding.beanName(), callbackName, e);
            }
        }
    }

    /**
     * 绑定信息
     * <p>
     * 注意：record 类型不支持 @构建器 注解，直接使用构造函数
     * </p>
     *
     * <p>一条绑定记录代表「某个配置键发生变化时，需要把新值写回哪个 Bean 的哪个成员」。
     * 由 {@code scanFields} / {@code scanMethods} 在 {@code @ConfigValue#hotReload()} 为
     * {@code true} 时创建，收集进 {@code bindingsByKey}，配置中心推送变更时按配置键
     * 取出同键的全部绑定逐条回写。因此本 record 同时承载「注入目标」与「回调契约」
     * 两类信息：{@link #field} 与 {@link #method} 二者互斥，标识注入点；
     * {@link #targetType} 描述注入点期望的类型；{@link #callback} 是可选的变更回调方法名。</p>
     *
     * <p>可空性：{@link #configKey}、{@link #bean}、{@link #beanName}、{@link #targetType}
     * 在紧凑构造器中已做 {@code requireNonNull} 校验，四个组件不允许为 {@code null}；
     * {@link #defaultValue}、{@link #field}、{@link #method}、{@link #callback}
     * 允许为 {@code null}，调用方须按下方说明判空。</p>
     *
     * @param configKey 配置 键，作为 {@code bindingsByKey} 的索引键，也是向配置中心
     *                  注册监听的键与回调方法的第一个入参。取值来源为
     *                  {@code @ConfigValue#value()} 表达式经
     *                  {@code parseExpression} 解析出的占位符键（{@code ${key:default}} 中的
     *                  {@code key} 部分）；表达式不含占位符时整串作为键。紧凑构造器已
     *                  校验不允许为 {@code null}，但允许为空白串（此时
     *                  {@code registerBinding} 仍会注册，查找配置时自然取不到值）
     * @param defaultValue 默认 值，占位符中 {@code :} 之后的回退文本，即
     *                     {@code ${key:default}} 的 {@code default} 部分。取值来源为
     *                     {@code parseExpression} 的第二个捕获组；表达式未写默认值时
     *                     该组为 {@code null}，故本组件<b>允许为 {@code null}</b>。
     *                     用途有二：初始化时 {@code resolveValue} 在环境取不到值时用它回退；
     *                     配置被删除（{@code onDelete}）时用它把成员回退到默认值。
     *                     传入 {@code null} 时两者都变为「无值可写」，成员保持原状
     * @param bean 目标 Bean 实例，即实际持有被注入成员的 Spring 单例对象，所有字段
     *             回写与方法调用都作用在它身上。取值来源为
     *             {@code postProcessAfterInitialization} 收到的 bean 参数。
     *             紧凑构造器已校验不允许为 {@code null}
     * @param beanName 目标 Bean 在 容器中的注册名，仅用于日志输出（注册绑定、注入失败、
     *                热更新成功、回调方法缺失等处的 log）与管理，不参与反射查找，
     *                因此即使与 {@link #bean} 的实际类不匹配也不影响功能。
     *                取值来源为 {@code postProcessAfterInitialization} 的 beanName 形参。
     *                紧凑构造器已校验不允许为 {@code null}
     * @param field 被注入的 字段，为 {@code scanFields} 路径专属：保存带
     *              {@code @ConfigValue} 的 {@link Field} 声明，回写时经
     *              {@code ReflectUtils.getField} 读旧值、{@code ReflectUtils.setField}
     *              写新值。字段路径下固定为非 {@code null}；方法路径下显式传
     *              {@code null}，此时<b>允许为 {@code null}</b>，更新逻辑改走
     *              {@link #method}
     * @param method 被调用的 注入方法，为 {@code scanMethods} 路径专属：保存带
     *               {@code @ConfigValue} 的单参 {@link Method}（形参个数不为 1 的方法
     *               在扫描阶段即被跳过），更新时以单参调用它完成赋值。方法路径下固定为
     *               非 {@code null}；字段路径下显式传 {@code null}，此时<b>允许为
     *               {@code null}</b>。与 {@link #field} 严格互斥，更新时按
     *               {@code field} 优先的顺序二选一
     * @param callback 配置 变更 回调方法名，为空字符串时表示不回调（此时
     *                 {@code invokeCallback} 直接返回）。取值来源为
     *                 {@code @ConfigValue#callback()}，未配置时为 {@code null} 或空串，
     *                 故本组件<b>允许为 {@code null}</b>。非空时要求该 Bean 中存在
     *                 同名且形参为 {@code (String, Object, Object)} 的方法，
     *                 三参依次为「配置键、旧值、新值」，其中新值已按
     *                 {@link #targetType} 转换；方法不存在时只记警告不抛异常
     * @param targetType 注入目标的 期望类型，即配置字符串的转换目标类型，也是回调方法
     *                   新值入参的转换类型。字段路径取 {@code field.getType()}；
     *                   方法路径取 {@code method.getParameterTypes()[0]}。
     *                   转换由 {@code Converter.convertIfNecessary} 完成，
     *                   允许转换失败（异常在注入与回调内被捕获并记日志）。
     *                   紧凑构造器已校验不允许为 {@code null}
     */
    private record BindingInfo(String configKey,
                               String defaultValue,
                               Object bean,
                               String beanName,
                               Field field,
                               Method method,
                               String callback,
                               Class<?> targetType) {

        /**
         * 规范构造器：必填组件做空值校验。
         *
         * <p>value class 前置条件——值类要求空值敌对；{@code defaultValue} 可为空，
         * {@code field} 与 {@code method} 互斥（其一时另一为 {@code null}），
         * {@code callback} 未配置时亦为空，故均不做校验。</p>
         *
         * @param configKey 配置键
         * @param bean 目标 Bean 实例
         * @param beanName Bean 名称
         * @param targetType 目标类型
         */
        public BindingInfo {
            configKey = Objects.requireNonNull(configKey, "configKey 不能为 null");
            bean = Objects.requireNonNull(bean, "bean 不能为 null");
            beanName = Objects.requireNonNull(beanName, "beanName 不能为 null");
            targetType = Objects.requireNonNull(targetType, "targetType 不能为 null");
        }
    }

    /**
     * 解析后的表达式
     * @param key Key
     * @param defaultValue 默认值
     * @return 解析expression的结果
     */
    private record ParsedExpression(String key, String defaultValue) {
    }
}

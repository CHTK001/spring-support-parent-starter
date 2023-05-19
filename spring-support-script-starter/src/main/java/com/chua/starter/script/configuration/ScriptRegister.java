package com.chua.starter.script.configuration;

import com.chua.starter.script.ScriptExtension;
import com.chua.starter.script.adaptor.Adaptor;
import com.chua.starter.script.watchdog.Watchdog;
import com.google.common.base.Strings;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册器
 *
 * @author CH
 */
public class ScriptRegister implements InitializingBean, DisposableBean {

    final ScriptProperties scriptProperties;
    private BeanDefinitionRegistry registry;
    final Map<ScriptProperties.Type, List<ScriptProperties.Config>> config = new ConcurrentHashMap<>();
    final Map<String, Watchdog> watchdog = new ConcurrentHashMap<>();
    final Map<String, Adaptor> adaptor = new ConcurrentHashMap<>();

    public ScriptRegister(ScriptProperties scriptProperties, BeanDefinitionRegistry registry) {
        this.scriptProperties = scriptProperties;
        this.registry = registry;
    }

    public void register(BeanDefinitionRegistry registry) {
        List<BeanDefinition> definitions = createDefinition();
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
        for (int i = 0; i < definitions.size(); i++) {
            BeanDefinition definition = definitions.get(i);
            Object bean = definition.getAttribute("bean");
            String beanName = definition.getBeanClassName() + pattern.format(LocalDateTime.now()) + i;
            if (bean instanceof String && !Strings.isNullOrEmpty(bean.toString())) {
                beanName = bean.toString();
            }
            registry.registerBeanDefinition(beanName, definition);
        }
    }

    /**
     * 初始化定义
     *
     * @return 定义
     */
    private List<BeanDefinition> createDefinition() {
        List<BeanDefinition> rs = new LinkedList<>();
        for (List<ScriptProperties.Config> list : config.values()) {
            rs.addAll(createDefinition(list));
        }

        return rs;
    }

    /**
     * 初始化定义
     *
     * @param list 配置
     * @return 定义
     */
    private Collection<? extends BeanDefinition> createDefinition(List<ScriptProperties.Config> list) {
        List<BeanDefinition> rs = new LinkedList<>();
        for (ScriptProperties.Config config1 : list) {
            ScriptProperties.Type type = config1.getType();
            Adaptor adaptor1 = this.adaptor.get(type.name().toUpperCase());
            if (null == adaptor1) {
                continue;
            }

            rs.addAll(adaptor1.analysis(config1, watchdog.get(type.name().toUpperCase())));
        }

        return rs;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initialAdaptor();
        initialWatchdog();
        initialConfig();
        this.register(registry);

    }

    private void initialConfig() {
        List<ScriptProperties.Config> config1 = Optional.ofNullable(scriptProperties.getConfig()).orElse(Collections.emptyList());
        for (ScriptProperties.Config config2 : config1) {
            config.computeIfAbsent(config2.getType(), it -> new LinkedList<>()).add(config2);
        }

    }

    private void initialWatchdog() {
        Map<ScriptProperties.Type, String> watchdog1 = Optional.ofNullable(scriptProperties.getWatchdog()).orElse(Collections.emptyMap());
        Map<String, String> tpl = new HashMap<>(watchdog1.size());
        for (Map.Entry<ScriptProperties.Type, String> entry : watchdog1.entrySet()) {
            tpl.put(entry.getKey().name(), entry.getValue());
        }

        List<Watchdog> watchdogs = SpringFactoriesLoader.loadFactories(Watchdog.class, ClassLoader.getSystemClassLoader());
        for (Watchdog watchdog : watchdogs) {
            ScriptExtension scriptExtension = watchdog.getClass().getDeclaredAnnotation(ScriptExtension.class);
            if (null == scriptExtension) {
                continue;
            }

            String upperCase = scriptExtension.value().toUpperCase();
            if (tpl.isEmpty() || tpl.containsKey(upperCase)) {
                watchdog.timeout(scriptProperties.getTimeout());
                this.watchdog.put(upperCase, watchdog);
            }
        }

    }

    private void initialAdaptor() {
        List<Adaptor> adaptors = SpringFactoriesLoader.loadFactories(Adaptor.class, ClassLoader.getSystemClassLoader());
        for (Adaptor adaptor1 : adaptors) {
            ScriptExtension scriptExtension = adaptor1.getClass().getDeclaredAnnotation(ScriptExtension.class);
            if (null == scriptExtension) {
                continue;
            }

            this.adaptor.put(scriptExtension.value().toUpperCase(), adaptor1);
        }
    }

    @Override
    public void destroy() throws Exception {
        for (Watchdog value : watchdog.values()) {
            try {
                value.close();
            } catch (Exception ignored) {
            }
        }
    }
}

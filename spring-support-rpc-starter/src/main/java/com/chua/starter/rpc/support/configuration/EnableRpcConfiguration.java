package com.chua.starter.rpc.support.configuration;

import com.chua.starter.common.support.utils.BeanDefinitionUtils;
import com.chua.starter.rpc.support.properties.RpcProperties;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 远程配置
 *
 * @author CH
 */
@Slf4j
@ConditionalOnProperty(prefix = "plugin.rpc", name = "open", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(RpcProperties.class)
public class EnableRpcConfiguration implements ImportBeanDefinitionRegistrar, ApplicationContextAware {

    private RpcProperties rpcProperties;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        List<ProtocolConfig> protocol = rpcProperties.getProtocols();
        RegistryConfig registry1 = rpcProperties.getRegistry();
        BeanDefinitionUtils.registerTypePropertiesBeanDefinition(registry, ApplicationConfig.class, create(rpcProperties.getApplication()));

        ConsumerConfig consumer = rpcProperties.getConsumer();
        MonitorConfig monitor = rpcProperties.getMonitor();
        ReferenceConfig reference = rpcProperties.getReference();

        log.info(rpcProperties.getApplication().toString());
        log.info(registry1.toString());

        BeanDefinitionUtils.registerTypePropertiesBeanDefinition(registry, RegistryConfig.class, create(registry1));

        for (ProtocolConfig protocolConfig : protocol) {
            if (Strings.isNullOrEmpty(protocolConfig.getSerialization())) {
                protocolConfig.setSerialization(RpcProperties.DEFAULT_SERIALIZATION);
            }
            log.info(protocolConfig.toString());
            BeanDefinitionUtils.registerTypePropertiesBeanDefinition(protocolConfig.getName(), registry, ProtocolConfig.class, create(protocolConfig));
        }

        if (null == consumer) {
            consumer = new ConsumerConfig();
            consumer.setCheck(false);
        }
        log.info(consumer.toString());
        BeanDefinitionUtils.registerTypePropertiesBeanDefinition(registry, ConsumerConfig.class, create(consumer));

        if (null != monitor) {
            BeanDefinitionUtils.registerTypePropertiesBeanDefinition(registry, MonitorConfig.class, create(monitor));
        }

        if (null != reference) {
            BeanDefinitionUtils.registerTypePropertiesBeanDefinition(registry, ReferenceConfig.class, create(reference));
        }

    }


    @SuppressWarnings("ALL")
    public Map create(Object source) {
        Map rs = new HashMap();
        if (source instanceof Map) {
            rs.putAll((Map<? extends String, ?>) source);
            return rs;
        }

        Class<?> aClass = source.getClass();
        ReflectionUtils.doWithFields(aClass, field -> {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                return;
            }

            String k = field.getName();
            boolean hasWritter = false;
            try {
                hasWritter = null != field.getDeclaringClass().getDeclaredMethod("set" + toFirstUpperCase(k), field.getType());
            } catch (Throwable ignore) {
            }

            if (!hasWritter) {
                return;
            }

            field.setAccessible(true);
            Object v = field.get(source);
            if (null == v) {
                return;
            }

            rs.put(k, v);
        });
        return rs;
    }


    /**
     * 首字母大写
     * <pre>
     *     toFirstUpperCase("user-name") = User-name
     * </pre>
     *
     * @param source 原始数据
     * @return 下划线数据
     */
    public static String toFirstUpperCase(String source) {
        if (source == null || source.length() == 0) {
            return source;
        }

        char firstChar = source.charAt(0);
        if (Character.isTitleCase(firstChar)) {
            return source;
        }

        return Character.toTitleCase(firstChar) +
                source.substring(1);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.rpcProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(RpcProperties.PRE, RpcProperties.class);
    }
}

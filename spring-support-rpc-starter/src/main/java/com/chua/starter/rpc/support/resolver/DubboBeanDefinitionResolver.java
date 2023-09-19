package com.chua.starter.rpc.support.resolver;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.rpc.RpcProtocolConfig;
import com.chua.starter.common.support.utils.BeanDefinitionUtils;
import com.chua.starter.rpc.support.properties.RpcProperties;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.*;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * doubbobean释义分解器
 *
 * @author CH
 */
@Slf4j
public class DubboBeanDefinitionResolver implements BeanDefinitionResolver{
    @Override
    public void register(RpcProperties rpcProperties, BeanDefinitionRegistry registry) {
        List<RpcProtocolConfig> protocols = rpcProperties.getProtocols();
        List<ProtocolConfig> protocol = BeanUtils.copyPropertiesList(protocols, ProtocolConfig.class);
        RegistryConfig registry1 = rpcProperties.getRegistry();
        BeanDefinitionUtils.registerTypePropertiesBeanDefinition(registry, ApplicationConfig.class, create(rpcProperties.getApplication()));

        ConsumerConfig consumer = BeanUtils.copyProperties(rpcProperties.getConsumer(), ConsumerConfig.class);
        MonitorConfig monitor = null;
        ReferenceConfig reference = BeanUtils.copyProperties(rpcProperties.getReference(), ReferenceConfig.class);

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
}

package com.chua.starter.config.plugin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.lang.expression.ExpressionProvider;
import com.chua.common.support.lang.expression.listener.DelegateRefreshListener;
import com.chua.common.support.lang.expression.listener.Listener;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.entity.KeyValue;
import com.chua.starter.config.properties.ConfigProperties;
import com.chua.starter.config.protocol.ProtocolProvider;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 脚本
 *
 * @author CH
 */
@Slf4j
@Spi(ConfigConstant.BEAN)
public class ScriptValuePlugin implements Plugin, BeanDefinitionRegistryPostProcessor, ApplicationContextAware {
    @Setter
    private ConfigProperties configProperties;
    @Setter
    private ProtocolProvider protocolProvider;

    private static final Map<String, ExpressionProvider> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Listener> LISTENER_MAP = new ConcurrentHashMap<>();

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        List<ConfigProperties.Subscribe> subscribe = configProperties.getSubscribe();
        List<ConfigProperties.Subscribe> collect = subscribe.stream().filter(it -> ConfigConstant.BEAN.equals(it.getDataType())).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(collect)) {
            log.info("无订阅脚本");
            return;
        }

        ConfigProperties.Subscribe first = CollectionUtils.findFirst(collect);
        protocolProvider.subscribe(first.getSubscribe(), first.getDataType(), new LinkedHashMap<>(), new Consumer<Map<String, Object>>() {
            @Override
            public void accept(Map<String, Object> stringObjectMap) {
                log.info(">>>>>>> 已从配置中心拉取Bean");
                for (Map.Entry<String, Object> entry : stringObjectMap.entrySet()) {
                    JSONArray value = (JSONArray) entry.getValue();
                    registerBean(value, registry);
                }
            }
        });

    }

    private void registerBean(JSONArray value, BeanDefinitionRegistry registry) {
        value.forEach((item) -> {
            JSONObject jsonObject = (JSONObject) item;
            String beanTypeName = jsonObject.getString("beanTypeName");
            if(!ClassUtils.isPresent(beanTypeName)) {
                log.warn("{}不存在", beanTypeName);
                return;
            }
            Object o = jsonObject.get(ConfigConstant.FILE_TYPE_CONTENT);
            byte[] con = new byte[0];
            if(null != o) {
                con = Converter.convertIfNecessary(o, byte[].class);
            }
            String s = new String(con);
            String beanName = jsonObject.getString("beanName");
            Listener listener = LISTENER_MAP.computeIfAbsent(beanName, new Function<String, Listener>() {
                @Override
                public Listener apply(String key) {
                    return new DelegateRefreshListener(s);
                }
            });

            ExpressionProvider expressionProvider = ExpressionProvider.newSource().scriptType(
                    jsonObject.getString(ConfigConstant.FILE_TYPE))
                    .source(s)
                    .listener(listener).build();
            CACHE.put(beanName, expressionProvider);
            registry.registerBeanDefinition(beanName, BeanDefinitionBuilder
                    .rootBeanDefinition(ScriptFactoryBean.class)
                    .addConstructorArgValue(listener)
                    .addConstructorArgValue(expressionProvider)
                    .addConstructorArgValue(jsonObject)
                    .addConstructorArgValue(ClassUtils.forName(beanTypeName))
                    .getBeanDefinition()
            );
        });
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void onListener(KeyValue keyValue) {
        System.out.println();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.configProperties = SpringBeanUtils.bindOrCreate(ConfigProperties.PRE, ConfigProperties.class);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(protocolProvider);
        if (protocolProvider instanceof ApplicationContextAware) {
            ((ApplicationContextAware) protocolProvider).setApplicationContext(applicationContext);
        }
    }


    static
    class ScriptFactoryBean implements FactoryBean<Object> {

        private Listener listener;
        private ExpressionProvider expressionProvider;
        private JSONObject jsonObject;

        private Class<?> type;

        private Object bean;


        public ScriptFactoryBean(Listener listener, ExpressionProvider expressionProvider, JSONObject jsonObject, Class<?> type) {
            this.listener = listener;
            this.expressionProvider = expressionProvider;
            this.jsonObject = jsonObject;
            this.type = type;
        }

        @Override
        public Object getObject() throws Exception {
            if(null == bean) {
                return (bean = expressionProvider.createProxy(type));
            }

            return bean;
        }

        @Override
        public Class<?> getObjectType() {
            return type;
        }
    }

}

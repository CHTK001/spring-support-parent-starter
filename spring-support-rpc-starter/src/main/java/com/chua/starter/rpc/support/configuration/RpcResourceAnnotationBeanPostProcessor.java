package com.chua.starter.rpc.support.configuration;

import com.chua.common.support.rpc.RpcClient;
import com.chua.common.support.rpc.RpcResource;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.rpc.support.attrbute.RpcAttribute;
import com.chua.starter.rpc.support.properties.RpcProperties;
import com.chua.starter.rpc.support.resource.RpcResourceBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.lang.reflect.Member;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.chua.common.support.utils.StringUtils.isBlank;

/**
 * rpc资源注释配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/07
 */
public class RpcResourceAnnotationBeanPostProcessor extends AbstractAnnotationBeanPostProcessor implements BeanFactoryPostProcessor {

    /**
     *
     */
    public static final String BEAN_NAME = "referenceAnnotationBeanPostProcessor";

    /**
     * Cache size
     */
    private static final int CACHE_SIZE = Integer.getInteger(BEAN_NAME + ".cache.size", 32);

    private final ConcurrentMap<InjectionMetadata.InjectedElement, String> injectedFieldReferenceBeanCache =
            new ConcurrentHashMap<>(CACHE_SIZE);

    private final ConcurrentMap<InjectionMetadata.InjectedElement, String> injectedMethodReferenceBeanCache =
            new ConcurrentHashMap<>(CACHE_SIZE);

    private RpcProperties rpcProperties;
    private RpcClient rpcClient;

    public RpcResourceAnnotationBeanPostProcessor() {
        super(RpcResource.class);
    }


    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if(!rpcProperties.isEnable()) {
            return;
        }
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Class<?> beanType = beanFactory.getType(beanName);
            if (beanType != null) {
                AnnotatedInjectionMetadata metadata = findInjectionMetadata(beanName, beanType, null);
                try {
                    prepareInjection(metadata);
                } catch (BeansException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException("Prepare rpc reference injection element failed", e);
                }
            }
        }

        if (beanFactory instanceof AbstractBeanFactory) {
            List<BeanPostProcessor> beanPostProcessors = ((AbstractBeanFactory) beanFactory).getBeanPostProcessors();
            for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
                if (beanPostProcessor == this) {
                    beanDefinitionRegistry.removeBeanDefinition(BEAN_NAME);
                    break;
                }
            }
        }
    }

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if(!rpcProperties.isEnable()) {
            return;
        }
        AnnotatedInjectionMetadata metadata = findInjectionMetadata(beanName, beanType, null);
        metadata.checkConfigMembers(beanDefinition);
        try {
            prepareInjection(metadata);
        } catch (Exception e) {
            throw new IllegalStateException("Prepare reference injection element failed", e);
        }
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
        if(!rpcProperties.isEnable()) {
            return pvs;
        }
        try {
            AnnotatedInjectionMetadata metadata = findInjectionMetadata(beanName, bean.getClass(), pvs);
            prepareInjection(metadata);
            metadata.inject(bean, beanName, pvs);
        } catch (BeansException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new BeanCreationException(beanName, "Injection of @" + getAnnotationType().getSimpleName()
                    + " dependencies is failed", ex);
        }
        return pvs;
    }


    @Override
    protected Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType, AnnotatedInjectElement injectedElement) throws Exception {
        if(!rpcProperties.isEnable()) {
            return bean;
        }
        if (injectedElement.injectedObject == null) {
            throw new IllegalStateException("The AnnotatedInjectElement of bean should be inited before injection");
        }


        return getBeanFactory().getBean((String) injectedElement.injectedObject);
    }

    protected void prepareInjection(AnnotatedInjectionMetadata metadata) throws BeansException {
        try {
            for (AnnotatedFieldElement fieldElement : metadata.getFieldElements()) {
                if (fieldElement.injectedObject != null) {
                    continue;
                }
                Class<?> injectedType = fieldElement.field.getType();
                AnnotationAttributes attributes = fieldElement.attributes;
                String referenceBeanName = registerReferenceBean(fieldElement.getPropertyName(), injectedType, attributes, fieldElement.field);

                fieldElement.injectedObject = referenceBeanName;
                injectedFieldReferenceBeanCache.put(fieldElement, referenceBeanName);

            }

            for (AnnotatedMethodElement methodElement : metadata.getMethodElements()) {
                if (methodElement.injectedObject != null) {
                    continue;
                }
                Class<?> injectedType = methodElement.getInjectedType();
                AnnotationAttributes attributes = methodElement.attributes;
                String referenceBeanName = registerReferenceBean(methodElement.getPropertyName(), injectedType, attributes, methodElement.method);

                methodElement.injectedObject = referenceBeanName;
                injectedMethodReferenceBeanCache.put(methodElement, referenceBeanName);
            }
        } catch (ClassNotFoundException e) {
            throw new BeanCreationException("prepare reference annotation failed", e);
        }
    }

    public String registerReferenceBean(String propertyName, Class<?> injectedType, Map<String, Object> attributes, Member member) throws BeansException {

        boolean renameable = true;
        String referenceBeanName = MapUtils.getString(attributes, RpcAttribute.ID);
        if (StringUtils.hasText(referenceBeanName)) {
            renameable = false;
        } else {
            referenceBeanName = propertyName;
        }

        String checkLocation = "Please check " + member.toString();


        // get interface
        String interfaceName = MapUtils.getString(attributes, RpcAttribute.INTERFACE);
        if (isBlank(interfaceName)) {
            throw new BeanCreationException("Need to specify the 'interfaceName' or 'interfaceClass' attribute of 'bean' if enable generic. " + checkLocation);
        }

        //check bean definition
        if (beanDefinitionRegistry.containsBeanDefinition(referenceBeanName)) {
            BeanDefinition prevBeanDefinition = beanDefinitionRegistry.getBeanDefinition(referenceBeanName);
            String prevBeanType = prevBeanDefinition.getBeanClassName();
            String prevBeanDesc = referenceBeanName + "[" + prevBeanType + "]";

            int index = 2;
            if (renameable) {
                String newReferenceBeanName = null;
                while (newReferenceBeanName == null || beanDefinitionRegistry.containsBeanDefinition(newReferenceBeanName)
                        || beanDefinitionRegistry.isAlias(newReferenceBeanName)) {
                    newReferenceBeanName = referenceBeanName + "#" + index;
                    index++;
                }
                referenceBeanName = newReferenceBeanName;
            }
        }
        attributes.put(RpcAttribute.ID, referenceBeanName);

        // Register the reference bean definition to the beanFactory
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClassName(RpcResourceBean.class.getName());
        beanDefinition.getPropertyValues().add(RpcAttribute.ID, referenceBeanName);
        beanDefinition.getPropertyValues().add("rpcClient", rpcClient);

        // set attribute instead of property values
//        beanDefinition.setAttribute(Constants.REFERENCE_PROPS, attributes);
        beanDefinition.setAttribute(RpcAttribute.INTERFACE_CLASS, injectedType);
        beanDefinition.setAttribute(RpcAttribute.INTERFACE_NAME, interfaceName);

        // create decorated definition for reference bean, Avoid being instantiated when getting the beanType of ReferenceBean
        // see org.springframework.beans.order.support.AbstractBeanFactory#getTypeForFactoryBean()
        GenericBeanDefinition targetDefinition = new GenericBeanDefinition();
        targetDefinition.setBeanClass(injectedType);
        beanDefinition.setDecoratedDefinition(new BeanDefinitionHolder(targetDefinition, referenceBeanName + "_decorated"));

        // signal object type since Spring 5.2
//        beanDefinition.setAttribute(Constants.OBJECT_TYPE_ATTRIBUTE, interfaceClass);

        beanDefinitionRegistry.registerBeanDefinition(referenceBeanName, beanDefinition);
//        referenceBeanManager.registerReferenceKeyAndBeanName(referenceKey, referenceBeanName);
        return referenceBeanName;
    }


    @Override
    public void setEnvironment(Environment environment) {
        super.setEnvironment(environment);
        BindResult<RpcProperties> bindResult = Binder.get(environment).bind(RpcProperties.PRE, RpcProperties.class);
        if(bindResult.isBound()) {
            rpcProperties = bindResult.get();
            this.rpcClient = RpcClient.createClient(rpcProperties.getType().name(),
                    rpcProperties.getRegistry(),
                    rpcProperties.getConsumer(),
                    rpcProperties.getApplicationName());
            return;
        }
        rpcProperties = new RpcProperties();
        rpcProperties.setEnable(false);

    }

}

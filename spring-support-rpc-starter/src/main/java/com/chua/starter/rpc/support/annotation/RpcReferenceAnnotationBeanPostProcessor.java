package com.chua.starter.rpc.support.annotation;

import com.chua.rpc.support.annotation.RpcInject;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.Constants;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.context.event.DubboConfigInitEvent;
import org.apache.dubbo.config.spring.reference.ReferenceAttributes;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;
import org.apache.dubbo.config.spring.reference.ReferenceBeanSupport;
import org.apache.dubbo.config.spring.util.SpringCompatUtils;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.MethodMetadata;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.alibaba.spring.util.AnnotationUtils.getAttribute;
import static org.apache.dubbo.common.utils.AnnotationUtils.filterDefaultValues;
import static org.springframework.util.StringUtils.hasText;

/**
 * remote
 *
 * @author CH
 */
@Slf4j
@ConditionalOnProperty(prefix = "plugin.rpc", name = "open", havingValue = "true", matchIfMissing = false)
public class RpcReferenceAnnotationBeanPostProcessor extends AbstractAnnotationBeanPostProcessor
        implements ApplicationContextAware, BeanFactoryPostProcessor {

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

    private ApplicationContext applicationContext;

    private ReferenceBeanManager referenceBeanManager;
    private BeanDefinitionRegistry beanDefinitionRegistry;

    /**
     * {@link com.alibaba.dubbo.config.annotation.Reference @com.alibaba.dubbo.config.annotation.Reference} has been supported since 2.7.3
     * <p>
     * {@link DubboReference bean} has been supported since 2.7.7
     */
    public RpcReferenceAnnotationBeanPostProcessor() {
        super(RpcInject.class);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

        String[] beanNames = beanFactory.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Class<?> beanType;
            if (beanFactory.isFactoryBean(beanName)) {
                BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
                if (isReferenceBean(beanDefinition)) {
                    continue;
                }
                if (isAnnotatedReferenceBean(beanDefinition)) {
                    processReferenceAnnotatedBeanDefinition(beanName, (AnnotatedBeanDefinition) beanDefinition);
                    continue;
                }

                String beanClassName = beanDefinition.getBeanClassName();
                beanType = ClassUtils.resolveClass(beanClassName, getClassLoader());
            } else {
                beanType = beanFactory.getType(beanName);
            }
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

        try {
            applicationContext.publishEvent(new DubboConfigInitEvent(applicationContext));
        } catch (Exception e) {
            log.warn("publish early application event failed, please upgrade spring version to 4.2.x or later: " + e);
        }
    }

    /**
     * check whether is bean at java-config @bean method
     */
    private boolean isAnnotatedReferenceBean(BeanDefinition beanDefinition) {
        if (beanDefinition instanceof AnnotatedBeanDefinition) {
            AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDefinition;
            String beanClassName = SpringCompatUtils.getFactoryMethodReturnType(annotatedBeanDefinition);
            if (ReferenceBean.class.getName().equals(beanClassName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param beanName
     * @param beanDefinition
     */
    private void processReferenceAnnotatedBeanDefinition(String beanName, AnnotatedBeanDefinition beanDefinition) {

        MethodMetadata factoryMethodMetadata = SpringCompatUtils.getFactoryMethodMetadata(beanDefinition);
        Class<?> beanClass = getBeanFactory().getType(beanName);
        if (beanClass == Object.class) {
            beanClass = SpringCompatUtils.getGenericTypeOfReturnType(factoryMethodMetadata);
        }
        if (beanClass == Object.class) {
            return;
        }

        if (beanClass == null) {
            String beanMethodSignature = factoryMethodMetadata.getDeclaringClassName() + "#" + factoryMethodMetadata.getMethodName() + "()";
            throw new BeanCreationException("The ReferenceBean is missing necessary generic type, which returned by the @Bean method of Java-config class. " +
                    "The generic type of the returned Bean must be specified as the referenced interface type, " +
                    "such as Bean. Please check bean method: " + beanMethodSignature);
        }

        Map<String, Object> annotationAttributes = null;
        for (Class<? extends Annotation> annotationType : getAnnotationTypes()) {
            if (factoryMethodMetadata.isAnnotated(annotationType.getName())) {
                annotationAttributes = factoryMethodMetadata.getAnnotationAttributes(annotationType.getName());
                annotationAttributes = filterDefaultValues(annotationType, annotationAttributes);
                break;
            }
        }

        if (annotationAttributes != null) {
            LinkedHashMap<String, Object> attributes = new LinkedHashMap<>(annotationAttributes);
            attributes.put(ReferenceAttributes.ID, beanName);
            ReferenceBeanSupport.convertReferenceProps(attributes, beanClass);

            String interfaceName = (String) attributes.get(ReferenceAttributes.INTERFACE);

            if (!StringUtils.isEquals(interfaceName, beanClass.getName()) && beanClass != GenericService.class) {
                String beanMethodSignature = factoryMethodMetadata.getDeclaringClassName() + "#" + factoryMethodMetadata.getMethodName() + "()";
                throw new BeanCreationException("The 'interfaceClass' or 'interfaceName' attribute value of bean annotation " +
                        "is inconsistent with the generic type of the ReferenceBean returned by the bean method. " +
                        "The interface class of bean is: " + interfaceName + ", but return ReferenceBean<" + beanClass.getName() + ">. " +
                        "Please remove the 'interfaceClass' and 'interfaceName' attributes from bean annotation. " +
                        "Please check bean method: " + beanMethodSignature);
            }

            Class interfaceClass = beanClass;

            beanDefinition.setAttribute(Constants.REFERENCE_PROPS, attributes);
            beanDefinition.setAttribute(ReferenceAttributes.INTERFACE_CLASS, interfaceClass);
            beanDefinition.setAttribute(ReferenceAttributes.INTERFACE_NAME, interfaceName);
        } else {
            // raw reference bean
            beanDefinition.setAttribute(ReferenceAttributes.INTERFACE_CLASS, beanClass);
            if (beanClass != GenericService.class) {
                beanDefinition.setAttribute(ReferenceAttributes.INTERFACE_NAME, beanClass.getName());
            }
        }

        // set id
        beanDefinition.getPropertyValues().add(ReferenceAttributes.ID, beanName);
    }

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if (beanType != null) {
            if (isReferenceBean(beanDefinition)) {
                List<PropertyValue> propertyValues = beanDefinition.getPropertyValues().getPropertyValueList();
                for (PropertyValue propertyValue : propertyValues) {
                    propertyValue.setOptional(true);
                }
            } else if (isAnnotatedReferenceBean(beanDefinition)) {
            } else {
                AnnotatedInjectionMetadata metadata = findInjectionMetadata(beanName, beanType, null);
                metadata.checkConfigMembers(beanDefinition);
                try {
                    prepareInjection(metadata);
                } catch (Exception e) {
                    throw new IllegalStateException("Prepare reference injection element failed", e);
                }
            }
        }
    }

    @Override
    public PropertyValues postProcessPropertyValues(
            PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeansException {

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

    private boolean isReferenceBean(BeanDefinition beanDefinition) {
        return ReferenceBean.class.getName().equals(beanDefinition.getBeanClassName());
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

                Map<String, ConsumerConfig> beansOfType = applicationContext.getBeansOfType(ConsumerConfig.class, true, false);
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
        String referenceBeanName = getAttribute(attributes, ReferenceAttributes.ID);
        if (hasText(referenceBeanName)) {
            renameable = false;
        } else {
            referenceBeanName = propertyName;
        }

        String checkLocation = "Please check " + member.toString();

        // convert annotation props
        ReferenceBeanSupport.convertReferenceProps(attributes, injectedType);

        // get interface
        String interfaceName = (String) attributes.get(ReferenceAttributes.INTERFACE);
        if (StringUtils.isBlank(interfaceName)) {
            throw new BeanCreationException("Need to specify the 'interfaceName' or 'interfaceClass' attribute of 'bean' if enable generic. " + checkLocation);
        }

        String referenceKey = ReferenceBeanSupport.generateReferenceKey(attributes, applicationContext);

        List<String> registeredReferenceBeanNames = referenceBeanManager.getBeanNamesByKey(referenceKey);
        if (registeredReferenceBeanNames.size() > 0) {
            if (registeredReferenceBeanNames.contains(referenceBeanName)) {
                return referenceBeanName;
            }
        }

        //check bean definition
        if (beanDefinitionRegistry.containsBeanDefinition(referenceBeanName)) {
            BeanDefinition prevBeanDefinition = beanDefinitionRegistry.getBeanDefinition(referenceBeanName);
            String prevBeanType = prevBeanDefinition.getBeanClassName();
            String prevBeanDesc = referenceBeanName + "[" + prevBeanType + "]";
            String newBeanDesc = referenceBeanName + "[" + referenceKey + "]";

            if (isReferenceBean(prevBeanDefinition)) {
                //check reference key
                String prevReferenceKey = ReferenceBeanSupport.generateReferenceKey(prevBeanDefinition, applicationContext);
                if (StringUtils.isEquals(prevReferenceKey, referenceKey)) {
                    //found matched dubbo reference bean, ignore register
                    return referenceBeanName;
                }
                //get interfaceName from attribute
                Assert.notNull(prevBeanDefinition, "The interface class of ReferenceBean is not initialized");
                prevBeanDesc = referenceBeanName + "[" + prevReferenceKey + "]";
            }

            // bean name from attribute 'id' or java-config bean, cannot be renamed
            if (!renameable) {
                throw new BeanCreationException("Already exists another bean definition with the same bean name [" + referenceBeanName + "], " +
                        "but cannot rename the reference bean name (specify the id attribute or java-config bean), " +
                        "please modify the name of one of the beans: " +
                        "prev: " + prevBeanDesc + ", new: " + newBeanDesc + ". " + checkLocation);
            }

            int index = 2;
            String newReferenceBeanName = null;
            while (newReferenceBeanName == null || beanDefinitionRegistry.containsBeanDefinition(newReferenceBeanName)
                    || beanDefinitionRegistry.isAlias(newReferenceBeanName)) {
                newReferenceBeanName = referenceBeanName + "#" + index;
                index++;
                if (registeredReferenceBeanNames.contains(newReferenceBeanName)) {
                    return newReferenceBeanName;
                }
            }
            newBeanDesc = newReferenceBeanName + "[" + referenceKey + "]";

            log.warn("Already exists another bean definition with the same bean name [" + referenceBeanName + "], " +
                    "rename dubbo reference bean to [" + newReferenceBeanName + "]. " +
                    "It is recommended to modify the name of one of the beans to avoid injection problems. " +
                    "prev: " + prevBeanDesc + ", new: " + newBeanDesc + ". " + checkLocation);
            referenceBeanName = newReferenceBeanName;
        }
        attributes.put(ReferenceAttributes.ID, referenceBeanName);

        // If registered matched reference before, just register alias
        if (registeredReferenceBeanNames.size() > 0) {
            beanDefinitionRegistry.registerAlias(registeredReferenceBeanNames.get(0), referenceBeanName);
            referenceBeanManager.registerReferenceKeyAndBeanName(referenceKey, referenceBeanName);
            return referenceBeanName;
        }

        Class interfaceClass = injectedType;

        // TODO Only register one reference bean for same (group, interface, version)

        // Register the reference bean definition to the beanFactory
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClassName(ReferenceBean.class.getName());
        beanDefinition.getPropertyValues().add(ReferenceAttributes.ID, referenceBeanName);

        // set attribute instead of property values
        beanDefinition.setAttribute(Constants.REFERENCE_PROPS, attributes);
        beanDefinition.setAttribute(ReferenceAttributes.INTERFACE_CLASS, interfaceClass);
        beanDefinition.setAttribute(ReferenceAttributes.INTERFACE_NAME, interfaceName);

        // create decorated definition for reference bean, Avoid being instantiated when getting the beanType of ReferenceBean
        // see org.springframework.beans.factory.support.AbstractBeanFactory#getTypeForFactoryBean()
        GenericBeanDefinition targetDefinition = new GenericBeanDefinition();
        targetDefinition.setBeanClass(interfaceClass);
        beanDefinition.setDecoratedDefinition(new BeanDefinitionHolder(targetDefinition, referenceBeanName + "_decorated"));

        // signal object type since Spring 5.2
        beanDefinition.setAttribute(Constants.OBJECT_TYPE_ATTRIBUTE, interfaceClass);

        beanDefinitionRegistry.registerBeanDefinition(referenceBeanName, beanDefinition);
        referenceBeanManager.registerReferenceKeyAndBeanName(referenceKey, referenceBeanName);
        log.info("Register dubbo reference bean: " + referenceBeanName + " = " + referenceKey + " at " + member);
        return referenceBeanName;
    }

    @Override
    protected Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                       AnnotatedInjectElement injectedElement) throws Exception {

        if (injectedElement.injectedObject == null) {
            throw new IllegalStateException("The AnnotatedInjectElement of bean should be inited before injection");
        }

        return getBeanFactory().getBean((String) injectedElement.injectedObject);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.referenceBeanManager = applicationContext.getBean(ReferenceBeanManager.BEAN_NAME, ReferenceBeanManager.class);
        this.beanDefinitionRegistry = (BeanDefinitionRegistry) applicationContext.getAutowireCapableBeanFactory();
    }

    @Override
    public void destroy() throws Exception {
        super.destroy();
        this.injectedFieldReferenceBeanCache.clear();
        this.injectedMethodReferenceBeanCache.clear();
    }

    /**
     * Gets all beans of {@link ReferenceBean}
     */
    @Deprecated
    public Collection<ReferenceBean<?>> getReferenceBeans() {
        return Collections.emptyList();
    }

    /**
     * Get {@link ReferenceBean} {@link Map} in injected field.
     *
     * @return non-null {@link Map}
     * @since 2.5.11
     */
    public Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> getInjectedFieldReferenceBeanMap() {
        Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> map = new HashMap<>();
        for (Map.Entry<InjectionMetadata.InjectedElement, String> entry : injectedFieldReferenceBeanCache.entrySet()) {
            map.put(entry.getKey(), referenceBeanManager.getById(entry.getValue()));
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Get {@link ReferenceBean} {@link Map} in injected method.
     *
     * @return non-null {@link Map}
     * @since 2.5.11
     */
    public Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> getInjectedMethodReferenceBeanMap() {
        Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> map = new HashMap<>();
        for (Map.Entry<InjectionMetadata.InjectedElement, String> entry : injectedMethodReferenceBeanCache.entrySet()) {
            map.put(entry.getKey(), referenceBeanManager.getById(entry.getValue()));
        }
        return Collections.unmodifiableMap(map);
    }

}



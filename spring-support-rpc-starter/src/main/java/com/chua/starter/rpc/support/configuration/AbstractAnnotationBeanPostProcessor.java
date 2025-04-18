package com.chua.starter.rpc.support.configuration;

import com.chua.common.support.utils.MapUtils;
import com.chua.starter.rpc.support.attrbute.RpcAttribute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.springframework.core.BridgeMethodResolver.findBridgedMethod;
import static org.springframework.core.BridgeMethodResolver.isVisibilityBridgeMethodPair;

/**
 * @author CH
 */
@Slf4j
public abstract class AbstractAnnotationBeanPostProcessor implements MergedBeanDefinitionPostProcessor,
        ApplicationContextAware,
        BeanFactoryAware, BeanClassLoaderAware, EnvironmentAware, DisposableBean, SmartInstantiationAwareBeanPostProcessor {

    private final static int CACHE_SIZE = Integer.getInteger("", 32);


    private final Class<? extends Annotation>[] annotationTypes;

    private final ConcurrentMap<String, AnnotatedInjectionMetadata> injectionMetadataCache =
            new ConcurrentHashMap<String, AnnotatedInjectionMetadata>(CACHE_SIZE);

    private ConfigurableListableBeanFactory beanFactory;

    private Environment environment;

    ClassLoader classLoader;

    private final int order = Ordered.LOWEST_PRECEDENCE;
    protected ApplicationContext applicationContext;
    protected BeanDefinitionRegistry beanDefinitionRegistry;
    /**
     * @param annotationTypes the multiple types of {@link Annotation annotations}
     */
    public AbstractAnnotationBeanPostProcessor(Class<? extends Annotation>... annotationTypes) {
        Assert.notEmpty(annotationTypes, "The argument of annotations' types must not empty");
        this.annotationTypes = annotationTypes;
    }

    private static <T> Collection<T> combine(Collection<? extends T>... elements) {
        List<T> allElements = new ArrayList<T>();
        for (Collection<? extends T> e : elements) {
            allElements.addAll(e);
        }
        return allElements;
    }
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.beanDefinitionRegistry = (BeanDefinitionRegistry) applicationContext.getAutowireCapableBeanFactory();
    }
    /**
     * Annotation type
     *
     * @return non-null
     * @deprecated 2.7.3, uses {@link #getAnnotationTypes()}
     */
    @Deprecated
    public final Class<? extends Annotation> getAnnotationType() {
        return annotationTypes[0];
    }

    protected final Class<? extends Annotation>[] getAnnotationTypes() {
        return annotationTypes;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory,
                "AnnotationInjectedBeanPostProcessor requires a ConfigurableListableBeanFactory");
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    /**
     * Finds {@link InjectionMetadata.InjectedElement} Metadata from annotated fields
     *
     * @param beanClass The {@link Class} of Bean
     * @return non-null {@link List}
     */
    private List<AnnotatedFieldElement> findFieldAnnotationMetadata(final Class<?> beanClass) {

        final List<AnnotatedFieldElement> elements = new LinkedList<>();

        ReflectionUtils.doWithFields(beanClass, field -> {

            for (Class<? extends Annotation> annotationType : getAnnotationTypes()) {
                Annotation annotation = field.getDeclaredAnnotation(annotationType);
                if(null == annotation) {
                    return;
                }
                AnnotationAttributes attributes = AnnotationUtils.getAnnotationAttributes(annotation, false, false);

                if (!MapUtils.containsValue(attributes, RpcAttribute.INTERFACE)) {
                    attributes.put(RpcAttribute.INTERFACE, field.getType().getTypeName());
                }

                if(!MapUtils.containsValue(attributes, RpcAttribute.INTERFACE_NAME)) {
                    attributes.put(RpcAttribute.INTERFACE_NAME, field.getType().getTypeName());
                }

                if(!MapUtils.containsValue(attributes, RpcAttribute.INTERFACE_CLASS)) {
                    attributes.put(RpcAttribute.INTERFACE_CLASS, field.getType());
                }
                if (Modifier.isStatic(field.getModifiers())) {
                    if (log.isWarnEnabled()) {
                        log.warn("@" + annotationType.getName() + " is not supported on static fields: " + field);
                    }
                    return;
                }

                elements.add(new AnnotatedFieldElement(field, attributes));
            }
        });

        return elements;

    }

    /**
     * Finds {@link InjectionMetadata.InjectedElement} Metadata from annotated methods
     *
     * @param beanClass The {@link Class} of Bean
     * @return non-null {@link List}
     */
    private List<AnnotatedMethodElement> findAnnotatedMethodMetadata(final Class<?> beanClass) {

        final List<AnnotatedMethodElement> elements = new LinkedList<>();

        ReflectionUtils.doWithMethods(beanClass, method -> {

            Method bridgedMethod = findBridgedMethod(method);

            if (!isVisibilityBridgeMethodPair(method, bridgedMethod)) {
                return;
            }

            if (method.getAnnotation(Bean.class) != null) {
                // DO NOT inject to Java-config class's @Bean method
                return;
            }

            for (Class<? extends Annotation> annotationType : getAnnotationTypes()) {
                Annotation annotation = method.getDeclaredAnnotation(annotationType);
                if(null == annotation) {
                    return;
                }
                AnnotationAttributes attributes = AnnotationUtils.getAnnotationAttributes(annotation, true, true);

                if (method.equals(ClassUtils.getMostSpecificMethod(method, beanClass))) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        throw new IllegalStateException("When using @" + annotationType.getName() + " to inject interface proxy, it is not supported on static methods: " + method);
                    }
                    if (method.getParameterTypes().length != 1) {
                        throw new IllegalStateException("When using @" + annotationType.getName() + " to inject interface proxy, the method must have only one parameter: " + method);
                    }
                    PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, beanClass);
                    elements.add(new AnnotatedMethodElement(method, pd, attributes));
                }
            }
        });

        return elements;
    }

    private AnnotatedInjectionMetadata buildAnnotatedMetadata(final Class<?> beanClass) {
        Collection<AnnotatedFieldElement> fieldElements = findFieldAnnotationMetadata(beanClass);
        Collection<AnnotatedMethodElement> methodElements = findAnnotatedMethodMetadata(beanClass);
        return new AnnotatedInjectionMetadata(beanClass, fieldElements, methodElements);
    }

    protected AnnotatedInjectionMetadata findInjectionMetadata(String beanName, Class<?> clazz, PropertyValues pvs) {
        // Fall back to class name as cache key, for backwards compatibility with custom callers.
        String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
        // Quick check on the concurrent map first, with minimal locking.
        AnnotatedInjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
        if (needsRefreshInjectionMetadata(metadata, clazz)) {
            synchronized (this.injectionMetadataCache) {
                metadata = this.injectionMetadataCache.get(cacheKey);

                if (needsRefreshInjectionMetadata(metadata, clazz)) {
                    if (metadata != null) {
                        metadata.clear(pvs);
                    }
                    try {
                        metadata = buildAnnotatedMetadata(clazz);
                        this.injectionMetadataCache.put(cacheKey, metadata);
                    } catch (NoClassDefFoundError err) {
                        throw new IllegalStateException("Failed to introspect object class [" + clazz.getName() +
                                "] for annotation metadata: could not find class that it depends on", err);
                    }
                }
            }
        }
        return metadata;
    }

    // Use custom check method to compatible with Spring 4.x
    private boolean needsRefreshInjectionMetadata(AnnotatedInjectionMetadata metadata, Class<?> clazz) {
        return (metadata == null || metadata.needsRefresh(clazz));
    }

    @Override
    public void destroy() throws Exception {

        injectionMetadataCache.clear();

        if (log.isInfoEnabled()) {
            log.info(getClass() + " was destroying!");
        }

    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    protected Environment getEnvironment() {
        return environment;
    }

    protected ClassLoader getClassLoader() {
        return classLoader;
    }

    protected ConfigurableListableBeanFactory getBeanFactory() {
        return beanFactory;
    }

    /**
     * Get injected-object from specified {@link AnnotationAttributes annotation attributes} and Bean Class
     *
     * @param attributes      {@link AnnotationAttributes the annotation attributes}
     * @param bean            Current bean that will be injected
     * @param beanName        Current bean name that will be injected
     * @param injectedType    the type of injected-object
     * @param injectedElement {@link AnnotatedInjectElement}
     * @return An injected object
     * @throws Exception If getting is failed
     */
    protected Object getInjectedObject(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                       AnnotatedInjectElement injectedElement) throws Exception {
        return doGetInjectedBean(attributes, bean, beanName, injectedType, injectedElement);
    }

    /**
     * Prepare injection data after found injection elements
     *
     * @param metadata
     * @throws Exception
     */
    protected void prepareInjection(AnnotatedInjectionMetadata metadata) throws Exception {
    }

    /**
     * Subclass must implement this method to get injected-object. The context objects could help this method if
     * necessary :
     * <ul>
     * <li>{@link #getBeanFactory() BeanFactory}</li>
     * <li>{@link #getClassLoader() ClassLoader}</li>
     * <li>{@link #getEnvironment() Environment}</li>
     * </ul>
     *
     * @param attributes      {@link AnnotationAttributes the annotation attributes}
     * @param bean            Current bean that will be injected
     * @param beanName        Current bean name that will be injected
     * @param injectedType    the type of injected-object
     * @param injectedElement {@link AnnotatedInjectElement}
     * @return The injected object
     * @throws Exception If resolving an injected object is failed.
     */
    protected abstract Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                                AnnotatedInjectElement injectedElement) throws Exception;

    /**
     * {@link Annotation Annotated} {@link InjectionMetadata} implementation
     */
    protected static class AnnotatedInjectionMetadata extends InjectionMetadata {

        private final Class<?> targetClass;
        private final Collection<AnnotatedFieldElement> fieldElements;

        private final Collection<AnnotatedMethodElement> methodElements;

        public AnnotatedInjectionMetadata(Class<?> targetClass, Collection<AnnotatedFieldElement> fieldElements,
                                          Collection<AnnotatedMethodElement> methodElements) {
            super(targetClass, combine(fieldElements, methodElements));
            this.targetClass = targetClass;
            this.fieldElements = fieldElements;
            this.methodElements = methodElements;
        }

        public Collection<AnnotatedFieldElement> getFieldElements() {
            return fieldElements;
        }

        public Collection<AnnotatedMethodElement> getMethodElements() {
            return methodElements;
        }

        //@Override // since Spring 5.2.4
        protected boolean needsRefresh(Class<?> clazz) {
            if (this.targetClass == clazz) {
                return false;
            }
            //IGNORE Spring CGLIB enhanced class
            return !targetClass.isAssignableFrom(clazz) || !clazz.getName().contains("$$EnhancerBySpringCGLIB$$");
        }
    }

    /**
     * {@link Annotation Annotated} {@link Method} {@link InjectionMetadata.InjectedElement}
     */
    protected class AnnotatedInjectElement extends InjectionMetadata.InjectedElement {

        public final AnnotationAttributes attributes;

        public volatile Object injectedObject;

        private Class<?> injectedType;

        protected AnnotatedInjectElement(Member member, PropertyDescriptor pd, AnnotationAttributes attributes) {
            super(member, pd);
            this.attributes = attributes;
        }

        @Override
        protected void inject(Object bean, String beanName, PropertyValues pvs) throws Throwable {

            Object injectedObject = getInjectedObject(attributes, bean, beanName, getInjectedType(), this);

            if (member instanceof Field field) {
                ReflectionUtils.makeAccessible(field);
                field.set(bean, injectedObject);
            } else if (member instanceof Method method) {
                ReflectionUtils.makeAccessible(method);
                method.invoke(bean, injectedObject);
            }
        }

        public Class<?> getInjectedType() throws ClassNotFoundException {
            if (injectedType == null) {
                if (this.isField) {
                    injectedType = ((Field) this.member).getType();
                } else if (this.pd != null) {
                    return this.pd.getPropertyType();
                } else {
                    Method method = (Method) this.member;
                    if (method.getParameterTypes().length > 0) {
                        injectedType = method.getParameterTypes()[0];
                    } else {
                        throw new IllegalStateException("get injected type failed");
                    }
                }
            }
            return injectedType;
        }

        public String getPropertyName() {
            if (member instanceof Field field) {
                return field.getName();
            } else if (this.pd != null) {
                // If it is method element, using propertyName of PropertyDescriptor
                return pd.getName();
            } else {
                Method method = (Method) this.member;
                return method.getName();
            }
        }
    }

    protected class AnnotatedMethodElement extends AnnotatedInjectElement {

        public final Method method;

        protected AnnotatedMethodElement(Method method, PropertyDescriptor pd, AnnotationAttributes attributes) {
            super(method, pd, attributes);
            this.method = method;
        }
    }

    public class AnnotatedFieldElement extends AnnotatedInjectElement {

        public final Field field;

        protected AnnotatedFieldElement(Field field, AnnotationAttributes attributes) {
            super(field, null, attributes);
            this.field = field;
        }
    }
}

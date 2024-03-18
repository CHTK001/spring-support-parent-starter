package com.chua.starter.common.support.processor;

import com.chua.common.support.utils.ClassUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 注解注入器
 *
 * @author CH
 * @since 2022/7/30 11:36
 */
@SuppressWarnings("ALL")
public abstract class AnnotationInjectedBeanPostProcessor<A extends Annotation> implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware, BeanClassLoaderAware, EnvironmentAware, DisposableBean, SmartInstantiationAwareBeanPostProcessor {

    private static final int CACHE_SIZE = Integer.getInteger("", 32);
    private final Log logger = LogFactory.getLog(this.getClass());
    private final Class<A> annotationType;
    private final ConcurrentMap<String, AnnotatedInjectionMetadata> injectionMetadataCache;
    private final ConcurrentMap<String, Object> injectedObjectsCache;
    private ConfigurableListableBeanFactory beanFactory;
    private Environment environment;
    private ClassLoader classLoader;
    private int order;

    public AnnotationInjectedBeanPostProcessor() {
        this.injectionMetadataCache = new ConcurrentHashMap(CACHE_SIZE);
        this.injectedObjectsCache = new ConcurrentHashMap(CACHE_SIZE);
        this.order = Integer.MAX_VALUE;
        this.annotationType = ClassUtils.resolveGenericType(this.getClass());
    }

    private static <T> Collection<T> combine(Collection<? extends T>... elements) {
        List<T> allElements = new ArrayList();
        Collection[] var2 = elements;
        int var3 = elements.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            Collection<? extends T> e = var2[var4];
            allElements.addAll(e);
        }

        return allElements;
    }

    public final Class<A> getAnnotationType() {
        return this.annotationType;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory, "AnnotationInjectedBeanPostProcessor requires a ConfigurableListableBeanFactory");
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
        InjectionMetadata metadata = this.findInjectionMetadata(beanName, bean.getClass(), pvs);

        try {
            metadata.inject(bean, beanName, pvs);
            return pvs;
        } catch (BeanCreationException var7) {
            throw var7;
        } catch (Throwable var8) {
            throw new BeanCreationException(beanName, "Injection of @" + this.getAnnotationType().getName() + " dependencies is failed", var8);
        }
    }


    private List<AnnotatedFieldElement> findFieldAnnotationMetadata(Class<?> beanClass) {
        final List<AnnotatedFieldElement> elements = new LinkedList();
        ReflectionUtils.doWithFields(beanClass, new ReflectionUtils.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                A annotation = AnnotationUtils.getAnnotation(field, AnnotationInjectedBeanPostProcessor.this.getAnnotationType());
                if (annotation != null) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        if (AnnotationInjectedBeanPostProcessor.this.logger.isWarnEnabled()) {
                            AnnotationInjectedBeanPostProcessor.this.logger.warn("@" + AnnotationInjectedBeanPostProcessor.this.getAnnotationType().getName() + " is not supported on static fields: " + field);
                        }

                        return;
                    }

                    elements.add(AnnotationInjectedBeanPostProcessor.this.new AnnotatedFieldElement(field, annotation));
                }

            }
        });
        return elements;
    }

    private List<AnnotatedMethodElement> findAnnotatedMethodMetadata(final Class<?> beanClass) {
        final List<AnnotatedMethodElement> elements = new LinkedList();
        ReflectionUtils.doWithMethods(beanClass, new ReflectionUtils.MethodCallback() {
            @Override
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
                if (BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
                    A annotation = AnnotationUtils.findAnnotation(bridgedMethod, AnnotationInjectedBeanPostProcessor.this.getAnnotationType());
                    if (annotation != null && method.equals(org.springframework.util.ClassUtils.getMostSpecificMethod(method, beanClass))) {
                        if (Modifier.isStatic(method.getModifiers())) {
                            if (AnnotationInjectedBeanPostProcessor.this.logger.isWarnEnabled()) {
                                AnnotationInjectedBeanPostProcessor.this.logger.warn("@" + AnnotationInjectedBeanPostProcessor.this.getAnnotationType().getSimpleName() + " annotation is not supported on static methods: " + method);
                            }

                            return;
                        }

                        if (method.getParameterTypes().length == 0 && AnnotationInjectedBeanPostProcessor.this.logger.isWarnEnabled()) {
                            AnnotationInjectedBeanPostProcessor.this.logger.warn("@" + AnnotationInjectedBeanPostProcessor.this.getAnnotationType().getSimpleName() + " annotation should only be used on methods with parameters: " + method);
                        }

                        PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, beanClass);
                        elements.add(AnnotationInjectedBeanPostProcessor.this.new AnnotatedMethodElement(method, pd, annotation));
                    }

                }
            }
        });
        return elements;
    }

    private AnnotatedInjectionMetadata buildAnnotatedMetadata(Class<?> beanClass) {
        Collection<AnnotatedFieldElement> fieldElements = this.findFieldAnnotationMetadata(beanClass);
        Collection<AnnotatedMethodElement> methodElements = this.findAnnotatedMethodMetadata(beanClass);
        return new AnnotatedInjectionMetadata(beanClass, fieldElements, methodElements);
    }

    private InjectionMetadata findInjectionMetadata(String beanName, Class<?> clazz, PropertyValues pvs) {
        String cacheKey = StringUtils.hasLength(beanName) ? beanName : clazz.getName();
        AnnotatedInjectionMetadata metadata = (AnnotatedInjectionMetadata) this.injectionMetadataCache.get(cacheKey);
        if (InjectionMetadata.needsRefresh(metadata, clazz)) {
            synchronized (this.injectionMetadataCache) {
                metadata = (AnnotatedInjectionMetadata) this.injectionMetadataCache.get(cacheKey);
                if (InjectionMetadata.needsRefresh(metadata, clazz)) {
                    if (metadata != null) {
                        metadata.clear(pvs);
                    }

                    try {
                        metadata = this.buildAnnotatedMetadata(clazz);
                        this.injectionMetadataCache.put(cacheKey, metadata);
                    } catch (NoClassDefFoundError var9) {
                        throw new IllegalStateException("Failed to introspect object class [" + clazz.getName() + "] for annotation metadata: could not find class that it depends on", var9);
                    }
                }
            }
        }

        return metadata;
    }

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if (beanType != null) {
            InjectionMetadata metadata = this.findInjectionMetadata(beanName, beanType, (PropertyValues) null);
            metadata.checkConfigMembers(beanDefinition);
        }

    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public void destroy() throws Exception {
        Iterator var1 = this.injectedObjectsCache.values().iterator();

        while (var1.hasNext()) {
            Object object = var1.next();
            if (this.logger.isInfoEnabled()) {
                this.logger.info(object + " was destroying!");
            }

            if (object instanceof DisposableBean) {
                ((DisposableBean) object).destroy();
            }
        }

        this.injectionMetadataCache.clear();
        this.injectedObjectsCache.clear();
        if (this.logger.isInfoEnabled()) {
            this.logger.info(this.getClass() + " was destroying!");
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
        return this.environment;
    }

    protected ClassLoader getClassLoader() {
        return this.classLoader;
    }

    protected ConfigurableListableBeanFactory getBeanFactory() {
        return this.beanFactory;
    }

    protected Collection<Object> getInjectedObjects() {
        return this.injectedObjectsCache.values();
    }

    protected Object getInjectedObject(A annotation, Object bean, String beanName, Class<?> injectedType, InjectionMetadata.InjectedElement injectedElement) throws Exception {
        String cacheKey = this.buildInjectedObjectCacheKey(annotation, bean, beanName, injectedType, injectedElement);
        Object injectedObject = this.injectedObjectsCache.get(cacheKey);
        if (injectedObject == null) {
            injectedObject = this.doGetInjectedBean(annotation, bean, beanName, injectedType, injectedElement);
            this.injectedObjectsCache.putIfAbsent(cacheKey, injectedObject);
        }

        return injectedObject;
    }

    /**
     * 生成对象
     *
     * @param annotation      注解
     * @param bean            bean
     * @param beanName        名称
     * @param injectedType    类型
     * @param injectedElement 元素
     * @return 对象
     * @throws Exception ex
     */
    protected abstract Object doGetInjectedBean(A annotation, Object bean, String beanName, Class<?> injectedType,
                                                InjectionMetadata.InjectedElement injectedElement) throws Exception;

    /**
     * /**
     * 对象索引
     *
     * @param annotation      注解
     * @param bean            bean
     * @param beanName        名称
     * @param injectedType    类型
     * @param injectedElement 元素
     * @return 对象索引
     */
    protected abstract String buildInjectedObjectCacheKey(A annotation, Object bean, String beanName,
                                                          Class<?> injectedType,
                                                          InjectionMetadata.InjectedElement injectedElement);

    protected Map<InjectionMetadata.InjectedElement, Object> getInjectedFieldObjectsMap() {
        Map<InjectionMetadata.InjectedElement, Object> injectedElementBeanMap = new LinkedHashMap<>();
        Iterator var2 = this.injectionMetadataCache.values().iterator();

        while (var2.hasNext()) {
            AnnotatedInjectionMetadata metadata = (AnnotatedInjectionMetadata) var2.next();
            Collection<AnnotatedFieldElement> fieldElements = metadata.getFieldElements();
            Iterator<AnnotatedFieldElement> var5 = fieldElements.iterator();

            while (var5.hasNext()) {
                AnnotatedFieldElement fieldElement = (AnnotatedFieldElement) var5.next();
                injectedElementBeanMap.put(fieldElement, fieldElement.bean);
            }
        }

        return Collections.unmodifiableMap(injectedElementBeanMap);
    }

    protected Map<InjectionMetadata.InjectedElement, Object> getInjectedMethodObjectsMap() {
        Map<InjectionMetadata.InjectedElement, Object> injectedElementBeanMap = new LinkedHashMap<>();
        Iterator var2 = this.injectionMetadataCache.values().iterator();

        while (var2.hasNext()) {
            AnnotatedInjectionMetadata metadata = (AnnotatedInjectionMetadata) var2.next();
            Collection<AnnotatedMethodElement> methodElements = metadata.getMethodElements();
            Iterator<AnnotatedMethodElement> var5 = methodElements.iterator();

            while (var5.hasNext()) {
                AnnotatedMethodElement methodElement = var5.next();
                injectedElementBeanMap.put(methodElement, methodElement.object);
            }
        }

        return Collections.unmodifiableMap(injectedElementBeanMap);
    }

    public class AnnotatedFieldElement extends InjectionMetadata.InjectedElement {
        private final Field field;
        private final A annotation;
        private volatile Object bean;

        protected AnnotatedFieldElement(Field field, A annotation) {
            super(field, (PropertyDescriptor) null);
            this.field = field;
            this.annotation = annotation;
        }

        @Override
        protected void inject(Object bean, String beanName, PropertyValues pvs) throws Throwable {
            Class<?> injectedType = this.field.getType();
            Object injectedObject = AnnotationInjectedBeanPostProcessor.this.getInjectedObject(this.annotation, bean, beanName, injectedType, this);
            ReflectionUtils.makeAccessible(this.field);
            this.field.set(bean, injectedObject);
        }
    }

    private class AnnotatedMethodElement extends InjectionMetadata.InjectedElement {
        private final Method method;
        private final A annotation;
        private volatile Object object;

        protected AnnotatedMethodElement(Method method, PropertyDescriptor pd, A annotation) {
            super(method, pd);
            this.method = method;
            this.annotation = annotation;
        }

        @Override
        protected void inject(Object bean, String beanName, PropertyValues pvs) throws Throwable {
            Class<?> injectedType = this.pd.getPropertyType();
            Object injectedObject = AnnotationInjectedBeanPostProcessor.this.getInjectedObject(this.annotation, bean, beanName, injectedType, this);
            ReflectionUtils.makeAccessible(this.method);
            this.method.invoke(bean, injectedObject);
        }
    }

    private class AnnotatedInjectionMetadata extends InjectionMetadata {
        private final Collection<AnnotatedFieldElement> fieldElements;
        private final Collection<AnnotatedMethodElement> methodElements;

        public AnnotatedInjectionMetadata(Class<?> targetClass, Collection<AnnotatedFieldElement> fieldElements, Collection<AnnotatedMethodElement> methodElements) {
            super(targetClass, AnnotationInjectedBeanPostProcessor.combine(fieldElements, methodElements));
            this.fieldElements = fieldElements;
            this.methodElements = methodElements;
        }

        public Collection<AnnotatedFieldElement> getFieldElements() {
            return this.fieldElements;
        }

        public Collection<AnnotatedMethodElement> getMethodElements() {
            return this.methodElements;
        }
    }
}

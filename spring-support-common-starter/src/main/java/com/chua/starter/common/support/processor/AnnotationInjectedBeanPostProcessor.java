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
 * 注解注入Bean后置处理器
 * <p>
 * 用于处理自定义注解的依赖注入，支持字段注入和方法注入。
 * 子类需要实现 {@link #doGetInjectedBean} 方法来提供注入对象。
 * </p>
 *
 * @param <A> 注解类型
 * @since 2022/7/30 11:36
 * @version 1.0.0
 */
@SuppressWarnings("ALL")
public abstract class AnnotationInjectedBeanPostProcessor<A extends Annotation> implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware, BeanClassLoaderAware, EnvironmentAware, DisposableBean, SmartInstantiationAwareBeanPostProcessor {

    /** Cache_size */
    private static final int CACHE_SIZE = Integer.getInteger("", 32);
    /** Logger */
    private final Log logger = LogFactory.getLog(this.getClass());
    /** Annotation类型 */
    private final Class<A> annotationType;
    /** injectionMetadataCache */
    private final ConcurrentMap<String, AnnotatedInjectionMetadata> injectionMetadataCache;
    private final ConcurrentMap<String, Object> injectedObjectsCache;
    /** Bean工厂 */
    private ConfigurableListableBeanFactory beanFactory;
    /** 环境 */
    private Environment environment;
    /** Classloader */
    private ClassLoader classLoader;
    /** 排序 */
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
    /**
     * 设置BeanFactory
     * @param beanFactory beanFactory
     */
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory, "AnnotationInjectedBeanPostProcessor requires a ConfigurableListableBeanFactory");
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    /**
     * Post处理Properties
     * @param pvs pvs
     * @param bean bean
     * @param beanName beanName
     */
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


    /**
     * 查找FieldAnnotationMetadata
     * @param beanClass beanClass
     */
    private List<AnnotatedFieldElement> findFieldAnnotationMetadata(Class<?> beanClass) {
        final List<AnnotatedFieldElement> elements = new LinkedList();
        ReflectionUtils.doWithFields(beanClass, new ReflectionUtils.FieldCallback() {
            @Override
            /**
             * Do设置
             * @param field field
             * @param IllegalArgumentException IllegalArgumentException
             */
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

    @SuppressWarnings("removal")
    /**
     * 查找AnnotatedMethodMetadata
     * @param beanClass beanClass
     */
    private List<AnnotatedMethodElement> findAnnotatedMethodMetadata(final Class<?> beanClass) {
        final List<AnnotatedMethodElement> elements = new LinkedList();
        ReflectionUtils.doWithMethods(beanClass, new ReflectionUtils.MethodCallback() {
            @Override
            /**
             * Do设置
             * @param method method
             * @param IllegalArgumentException IllegalArgumentException
             */
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

    /**
     * 构建AnnotatedMetadata
     * @param beanClass beanClass
     */
    private AnnotatedInjectionMetadata buildAnnotatedMetadata(Class<?> beanClass) {
        Collection<AnnotatedFieldElement> fieldElements = this.findFieldAnnotationMetadata(beanClass);
        Collection<AnnotatedMethodElement> methodElements = this.findAnnotatedMethodMetadata(beanClass);
        return new AnnotatedInjectionMetadata(beanClass, fieldElements, methodElements);
    }

    /**
     * 查找InjectionMetadata
     * @param beanName beanName
     * @param clazz clazz
     * @param pvs pvs
     */
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
    /**
     * Post处理MergedBeanDefinition
     * @param beanDefinition beanDefinition
     * @param beanType beanType
     * @param beanName beanName
     */
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if (beanType != null) {
            InjectionMetadata metadata = this.findInjectionMetadata(beanName, beanType, (PropertyValues) null);
            metadata.checkConfigMembers(beanDefinition);
        }

    }

    @Override
    /** 获取Order */
    public int getOrder() {
        return this.order;
    }

    /**
     * 设置Order
     * @param order order
     */
    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    /** 销毁 */
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
    /**
     * 设置BeanClassLoader
     * @param classLoader classLoader
     */
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    /**
     * 设置Environment
     * @param environment environment
     */
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /** 获取Environment */
    protected Environment getEnvironment() {
        return this.environment;
    }

    /** 获取ClassLoader */
    protected ClassLoader getClassLoader() {
        return this.classLoader;
    }

    /** 获取BeanFactory */
    protected ConfigurableListableBeanFactory getBeanFactory() {
        return this.beanFactory;
    }

    /** 获取InjectedObjects */
    protected Collection<Object> getInjectedObjects() {
        return this.injectedObjectsCache.values();
    }

    /**
     * 获取InjectedObject
     * @param annotation annotation
     * @param bean bean
     * @param beanName beanName
     * @param injectedType injectedType
     * @param injectedElement injectedElement
     */
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
 * @author CH
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
    /**
     * public class AnnotatedFieldElement extends InjectionMetadata.InjectedElement {
     *
     * @author CH
     * @since 4.0.0.42
     */

    public class AnnotatedFieldElement extends InjectionMetadata.InjectedElement {
        /** Field */
        private final Field field;
        /** Annotation */
        private final A annotation;
        private volatile Object bean;

        protected AnnotatedFieldElement(Field field, A annotation) {
            super(field, (PropertyDescriptor) null);
            this.field = field;
            this.annotation = annotation;
        }

        @Override
        /**
         * Inject
         * @param bean bean
         * @param beanName beanName
         * @param pvs pvs
         */
        protected void inject(Object bean, String beanName, PropertyValues pvs) throws Throwable {
            Class<?> injectedType = this.field.getType();
            Object injectedObject = AnnotationInjectedBeanPostProcessor.this.getInjectedObject(this.annotation, bean, beanName, injectedType, this);
            ReflectionUtils.makeAccessible(this.field);
            this.field.set(bean, injectedObject);
        }
    }

    private class AnnotatedMethodElement extends InjectionMetadata.InjectedElement {
        /** Method */
        private final Method method;
        /** Annotation */
        private final A annotation;
        private volatile Object object;

        protected AnnotatedMethodElement(Method method, PropertyDescriptor pd, A annotation) {
            super(method, pd);
            this.method = method;
            this.annotation = annotation;
        }

        @Override
        /**
         * Inject
         * @param bean bean
         * @param beanName beanName
         * @param pvs pvs
         */
        protected void inject(Object bean, String beanName, PropertyValues pvs) throws Throwable {
            Class<?> injectedType = this.pd.getPropertyType();
            Object injectedObject = AnnotationInjectedBeanPostProcessor.this.getInjectedObject(this.annotation, bean, beanName, injectedType, this);
            ReflectionUtils.makeAccessible(this.method);
            this.method.invoke(bean, injectedObject);
        }
    }

    private class AnnotatedInjectionMetadata extends InjectionMetadata {
        /** Fieldelements */
        private final Collection<AnnotatedFieldElement> fieldElements;
        /** Methodelements */
        private final Collection<AnnotatedMethodElement> methodElements;

        public AnnotatedInjectionMetadata(Class<?> targetClass, Collection<AnnotatedFieldElement> fieldElements, Collection<AnnotatedMethodElement> methodElements) {
            super(targetClass, AnnotationInjectedBeanPostProcessor.combine(fieldElements, methodElements));
            this.fieldElements = fieldElements;
            this.methodElements = methodElements;
        }

        /** 获取FieldElements */
        public Collection<AnnotatedFieldElement> getFieldElements() {
            return this.fieldElements;
        }

        /** 获取MethodElements */
        public Collection<AnnotatedMethodElement> getMethodElements() {
            return this.methodElements;
        }
    }
}


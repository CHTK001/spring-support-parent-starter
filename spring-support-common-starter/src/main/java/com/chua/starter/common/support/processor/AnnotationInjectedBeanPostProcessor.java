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
* @版本 1.0.0
* @author CH
 */
@SuppressWarnings("ALL")
public abstract class AnnotationInjectedBeanPostProcessor<A extends Annotation> implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware, BeanClassLoaderAware, EnvironmentAware, DisposableBean, SmartInstantiationAwareBeanPostProcessor {

    /** 缓存_大小 */
    private static final int CACHE_SIZE = Integer.getInteger("", 32);
    /** 日志记录器 */
    private final Log logger = LogFactory.getLog(this.getClass());
    /** 注解类型 */
    private final Class<A> annotationType;
    private final ConcurrentMap<String, AnnotatedInjectionMetadata> injectionMetadataCache; // injectionmetadata缓存
    private final ConcurrentMap<String, Object> injectedObjectsCache; // injected对象缓存
    /** Bean工厂 */
    private ConfigurableListableBeanFactory beanFactory;
    /** 环境 */
    private Environment environment;
    /** Classloader */
    private ClassLoader classLoader;
    /** 排序 */
    private int order;

    /**
    * 注解injectedbeanpost处理器。
     */
    public AnnotationInjectedBeanPostProcessor() {
        this.injectionMetadataCache = new ConcurrentHashMap(CACHE_SIZE);
        this.injectedObjectsCache = new ConcurrentHashMap(CACHE_SIZE);
        this.order = Integer.MAX_VALUE;
        this.annotationType = ClassUtils.resolveGenericType(this.getClass());
    }

    /**
    * 组合。
    * @param elements elements
    * @return 组合的结果
     */
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

    /**
    * 获取注解类型。
    * @return 获取注解类型的结果
     */
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
            /**
            * Bean创建异常。
            * @param beanName Bean名称
            * @param this.getAnnotationType( this.获取注解类型(
            * @return Bean创建异常的结果
             */
            throw new BeanCreationException(beanName, "Injection of @" + this.getAnnotationType().getName() + " dependencies is failed", var8);
        }
    }


    /**
    * 查找字段注解metadata。
    * @param beanClass Bean类
    * @return find字段注解metadata的结果
     */
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

    @SuppressWarnings("removal")
    /**
    * findannotated方法metadata。
    * @param beanClass Bean类
    * @return findannotated方法metadata的结果
     */
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

    /**
    * 构建annotatedmetadata。
    * @param beanClass Bean类
    * @return 构建annotatedmetadata的结果
     */
    private AnnotatedInjectionMetadata buildAnnotatedMetadata(Class<?> beanClass) {
        Collection<AnnotatedFieldElement> fieldElements = this.findFieldAnnotationMetadata(beanClass);
        Collection<AnnotatedMethodElement> methodElements = this.findAnnotatedMethodMetadata(beanClass);
        /**
        * annotatedinjectionmetadata。
        * @param beanClass Bean类
        * @param fieldElements 字段elements
        * @param methodElements 方法elements
        * @return AnnotatedInjectionMetadata的结果
         */
        return new AnnotatedInjectionMetadata(beanClass, fieldElements, methodElements);
    }

    /**
    * findinjectionmetadata。
    * @param beanName Bean名称
    * @param clazz clazz
    * @param pvs pvs
    * @return findInjectionMetadata的结果
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
                        /**
                        * illegal状态异常。
                        * @param clazz.getName( clazz.获取名称(
                        * @return illegal状态异常的结果
                         */
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

    /**
    * 设置订单。
    * @param order 订单
     */
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

    /**
    * 获取环境。
    * @return 获取环境的结果
     */
    protected Environment getEnvironment() {
        return this.environment;
    }

    /**
    * 获取类加载。
    * @return 获取类加载的结果
     */
    protected ClassLoader getClassLoader() {
        return this.classLoader;
    }

    /**
    * 获取Bean工厂。
    * @return 获取Bean工厂的结果
     */
    protected ConfigurableListableBeanFactory getBeanFactory() {
        return this.beanFactory;
    }

    /**
    * 获取injected对象。
    * @return 获取injected对象的结果
     */
    protected Collection<Object> getInjectedObjects() {
        return this.injectedObjectsCache.values();
    }

    /**
    * 获取injected对象。
    * @param annotation 注解
    * @param bean Bean
    * @param beanName Bean名称
    * @param injectedType injected类型
    * @param injectedElement injectedelement
    * @return 获取injected对象的结果
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
    * @param bean            Bean
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
    * @param bean            Bean
    * @param beanName        名称
    * @param injectedType    类型
    * @param injectedElement 元素
    * @return 对象索引
     */
    protected abstract String buildInjectedObjectCacheKey(A annotation, Object bean, String beanName,
                                                          Class<?> injectedType,
                                                          InjectionMetadata.InjectedElement injectedElement);

    /**
    * 获取injected字段对象映射。
    * @return 获取injected字段对象映射的结果
     */
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

    /**
    * 获取injected方法对象映射。
    * @return 获取injected方法对象映射的结果
     */
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
    * annotated字段element类。
    *
    * @author CH
    * @since 4.0.0
     */
    public class AnnotatedFieldElement extends InjectionMetadata.InjectedElement {
        /** 字段 */
        private final Field field;
        /** 注解 */
        private final A annotation;
        private volatile Object bean; // Bean

        /**
        * annotated字段element。
        * @param field 字段
        * @param annotation 注解
         */
        protected AnnotatedFieldElement(Field field, A annotation) {
            /**
            * 父。
            * @param field 字段
            * @param (PropertyDescriptor (财产descriptor
             */
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

    /**
    * annotated方法element类。
    *
    * @author CH
    * @since 4.0.0
     */
    private class AnnotatedMethodElement extends InjectionMetadata.InjectedElement {
        /** 方法 */
        private final Method method;
        /** 注解 */
        private final A annotation;
        private volatile Object object; // 对象

        /**
        * annotated方法element。
        * @param method 方法
        * @param pd pd
        * @param annotation 注解
         */
        protected AnnotatedMethodElement(Method method, PropertyDescriptor pd, A annotation) {
            /**
            * 父。
            * @param method 方法
            * @param pd pd
             */
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

    /**
    * annotatedinjectionmetadata类。
    *
    * @author CH
    * @since 4.0.0
     */
    private class AnnotatedInjectionMetadata extends InjectionMetadata {
        /** Fieldelements */
        private final Collection<AnnotatedFieldElement> fieldElements;
        /** Methodelements */
        private final Collection<AnnotatedMethodElement> methodElements;

        /**
        * annotatedinjectionmetadata。
        * @param targetClass Target类
        * @param fieldElements 字段elements
        * @param methodElements 方法elements
         */
        public AnnotatedInjectionMetadata(Class<?> targetClass, Collection<AnnotatedFieldElement> fieldElements, Collection<AnnotatedMethodElement> methodElements) {
            /**
            * 父。
            * @param targetClass Target类
            * @param methodElements 方法elements
             */
            super(targetClass, AnnotationInjectedBeanPostProcessor.combine(fieldElements, methodElements));
            this.fieldElements = fieldElements;
            this.methodElements = methodElements;
        }

        /**
        * 获取字段elements。
        * @return 获取字段elements的结果
         */
        public Collection<AnnotatedFieldElement> getFieldElements() {
            return this.fieldElements;
        }

        /**
        * 获取方法elements。
        * @return 获取方法elements的结果
         */
        public Collection<AnnotatedMethodElement> getMethodElements() {
            return this.methodElements;
        }
    }
}


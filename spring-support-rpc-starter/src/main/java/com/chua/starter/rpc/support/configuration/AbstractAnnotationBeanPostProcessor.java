package com.chua.starter.rpc.support.configuration;

import com.chua.common.support.utils.MapUtils;
import com.chua.starter.rpc.support.attrbute.RpcAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
* @since 4.0.0
 */
public abstract class AbstractAnnotationBeanPostProcessor implements MergedBeanDefinitionPostProcessor,
        ApplicationContextAware,
        BeanFactoryAware, BeanClassLoaderAware, EnvironmentAware, DisposableBean, SmartInstantiationAwareBeanPostProcessor {

    /** 日志 */
    protected static final Logger log = LoggerFactory.getLogger(AbstractAnnotationBeanPostProcessor.class);

    private final static int CACHE_SIZE = Integer.getInteger("", 32);


    private final Class<? extends Annotation>[] annotationTypes;

    private final ConcurrentMap<String, AnnotatedInjectionMetadata> injectionMetadataCache =
            new ConcurrentHashMap<String, AnnotatedInjectionMetadata>(CACHE_SIZE);

    /** Bean工厂 */
    private ConfigurableListableBeanFactory beanFactory;

    /** 环境 */
    private Environment environment;

    ClassLoader classLoader;

    /** 排序 */
    private final int order = Ordered.LOWEST_PRECEDENCE;
    /** Application上下文 */
    protected ApplicationContext applicationContext;
    /** Beandefinition注册表 */
    protected BeanDefinitionRegistry beanDefinitionRegistry;
    /**
    * @param annotationTypes the 多个 类型 的 {@link Annotation annotations}
     */
    public AbstractAnnotationBeanPostProcessor(Class<? extends Annotation>... annotationTypes) {
        Assert.notEmpty(annotationTypes, "The argument of annotations' types must not empty");
        this.annotationTypes = annotationTypes;
    }

    /**
    * 组合。
    * @param elements elements
    * @return 组合的结果
     */
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
    * 注解 类型
    *
    * @return non-null
    * @deprecated 2.7.3, uses {@link #getAnnotationTypes()}
     */
    @Deprecated
    public final Class<? extends Annotation> getAnnotationType() {
        return annotationTypes[0];
    }

    /**
    * 获取注解类型。
    * @return 获取注解类型的结果
     */
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
    * 查找 {@link InjectionMetadata.InjectedElement} Metadata 从 annotated 字段
    *
    * @param beanClass The {@link Class} 的 Bean
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
    * 查找 {@link InjectionMetadata.InjectedElement} Metadata 从 annotated 方法
    *
    * @param beanClass The {@link Class} 的 Bean
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
 // 执行 NOT inject 转为 Java-配置 类's @Bean 方法
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
                        /**
                        * illegal状态异常。
                        * @param annotationType.getName( 注解类型.getname(
                        * @return illegal状态异常的结果
                         */
                        throw new IllegalStateException("When using @" + annotationType.getName() + " to inject interface proxy, it is not supported on static methods: " + method);
                    }
                    if (method.getParameterTypes().length != 1) {
                        /**
                        * illegal状态异常。
                        * @param annotationType.getName( 注解类型.getname(
                        * @return illegal状态异常的结果
                         */
                        throw new IllegalStateException("When using @" + annotationType.getName() + " to inject interface proxy, the method must have only one parameter: " + method);
                    }
                    PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, beanClass);
                    elements.add(new AnnotatedMethodElement(method, pd, attributes));
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
    private AnnotatedInjectionMetadata buildAnnotatedMetadata(final Class<?> beanClass) {
        Collection<AnnotatedFieldElement> fieldElements = findFieldAnnotationMetadata(beanClass);
        Collection<AnnotatedMethodElement> methodElements = findAnnotatedMethodMetadata(beanClass);
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
    protected AnnotatedInjectionMetadata findInjectionMetadata(String beanName, Class<?> clazz, PropertyValues pvs) {
 // 下降 back 转为 类 名称 as 缓存 钥匙, for backwards compatibility with 习俗 callers.
        String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
 // Quick 检查 on the 并发 映射 第一个, with minimal 锁.
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
                        /**
                        * illegal状态异常。
                        * @param clazz.getName( clazz.获取名称(
                        * @return illegal状态异常的结果
                         */
                        throw new IllegalStateException("Failed to introspect object class [" + clazz.getName() +
                                "] for annotation metadata: could not find class that it depends on", err);
                    }
                }
            }
        }
        return metadata;
    }

 // Use 习俗 检查 方法 转为 compatible with Spring 4.x
    /**
    * needsrefreshinjectionmetadata。
    * @param metadata metadata
    * @param clazz clazz
    * @return needsRefreshInjectionMetadata的结果
     */
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

    /**
    * 获取环境。
    * @return 获取环境的结果
     */
    protected Environment getEnvironment() {
        return environment;
    }

    /**
    * 获取类加载。
    * @return 获取类加载的结果
     */
    protected ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
    * 获取Bean工厂。
    * @return 获取Bean工厂的结果
     */
    protected ConfigurableListableBeanFactory getBeanFactory() {
        return beanFactory;
    }

    /**
    * 获取 injected-对象 从 specified {@link AnnotationAttributes annotation attributes} 和 Bean 类
    *
    * @param attributes      {@link AnnotationAttributes the annotation attributes}
    * @param bean            当前 Bean that will be injected
    * @param beanName        当前 Bean 名称 that will be injected
    * @param injectedType    the 类型 的 injected-对象
    * @param injectedElement {@link AnnotatedInjectElement}
    * @return An injected 对象
    * @throws Exception If getting 是否 失败
     */
    protected Object getInjectedObject(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                       AnnotatedInjectElement injectedElement) throws Exception {
        return doGetInjectedBean(attributes, bean, beanName, injectedType, injectedElement);
    }

    /**
    * Prepare injection 数据 之后 found injection elements
    *
    * @param metadata
    * @throws Exception
     */
    protected void prepareInjection(AnnotatedInjectionMetadata metadata) throws Exception {
    }

    /**
    * Subclass must implement this 方法 转为 获取 injected-对象. The 上下文 对象 could help this 方法 if
    * necessary :
    * <ul>
    * <li>{@link #getBeanFactory() BeanFactory}</li>
    * <li>{@link #getClassLoader() ClassLoader}</li>
    * <li>{@link #getEnvironment() Environment}</li>
    * </ul>
    *
    * @param attributes      {@link AnnotationAttributes the annotation attributes}
    * @param bean            当前 Bean that will be injected
    * @param beanName        当前 Bean 名称 that will be injected
    * @param injectedType    the 类型 的 injected-对象
    * @param injectedElement {@link AnnotatedInjectElement}
    * @return The injected 对象
    * @throws Exception If resolving an injected 对象 是否 失败.
     */
    protected abstract Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                                AnnotatedInjectElement injectedElement) throws Exception;

    /**
    * {@link Annotation Annotated} {@link InjectionMetadata} implementation
    * @author CH
    * @since 4.0.0
     */
    protected static class AnnotatedInjectionMetadata extends InjectionMetadata {

        private final Class<?> targetClass; // Target类
        /** Fieldelements */
        private final Collection<AnnotatedFieldElement> fieldElements;

        /** Methodelements */
        private final Collection<AnnotatedMethodElement> methodElements;

        public AnnotatedInjectionMetadata(Class<?> targetClass, Collection<AnnotatedFieldElement> fieldElements,
                                          Collection<AnnotatedMethodElement> methodElements) {
            /**
            * 父。
            * @param targetClass Target类
            * @param methodElements 方法elements
             */
            super(targetClass, combine(fieldElements, methodElements));
            this.targetClass = targetClass;
            this.fieldElements = fieldElements;
            this.methodElements = methodElements;
        }

        /**
        * 获取字段elements。
        * @return 获取字段elements的结果
         */
        public Collection<AnnotatedFieldElement> getFieldElements() {
            return fieldElements;
        }

        /**
        * 获取方法elements。
        * @return 获取方法elements的结果
         */
        public Collection<AnnotatedMethodElement> getMethodElements() {
            return methodElements;
        }

 // @Override // 自 Spring 5.2.4
        protected boolean needsRefresh(Class<?> clazz) {
            if (this.targetClass == clazz) {
                return false;
            }
 // IGNORE Spring CGLIB 增强 类
            return !targetClass.isAssignableFrom(clazz) || !clazz.getName().contains("$$EnhancerBySpringCGLIB$$");
        }
    }

    /**
    * {@link Annotation Annotated} {@link Method} {@link InjectionMetadata.InjectedElement}
    * @author CH
    * @since 4.0.0
     */
    protected class AnnotatedInjectElement extends InjectionMetadata.InjectedElement {

        /** Attributes */
        public final AnnotationAttributes attributes;

        public volatile Object injectedObject; // injected对象

        private Class<?> injectedType; // injected类型

        /**
        * annotatedinjectelement。
        * @param member member
        * @param pd pd
        * @param attributes attributes
         */
        protected AnnotatedInjectElement(Member member, PropertyDescriptor pd, AnnotationAttributes attributes) {
            /**
            * 父。
            * @param member member
            * @param pd pd
             */
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

        /**
        * 获取injected类型。
        * @return 获取injected类型的结果
         */
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
                        /**
                        * illegal状态异常。
                        * @param failed" 失败"
                        * @return illegal状态异常的结果
                         */
                        throw new IllegalStateException("get injected type failed");
                    }
                }
            }
            return injectedType;
        }

        /**
        * 获取财产名称。
        * @return 获取财产名称的结果
         */
        public String getPropertyName() {
            if (member instanceof Field field) {
                return field.getName();
            } else if (this.pd != null) {
 // If it 是否 方法 element, 使用 财产名称 的 财产descriptor
                return pd.getName();
            } else {
                Method method = (Method) this.member;
                return method.getName();
            }
        }
    }

    /**
    * annotated方法element类。
    *
    * @author CH
    * @since 4.0.0
     */
    protected class AnnotatedMethodElement extends AnnotatedInjectElement {

        /** 方法 */
        public final Method method;

        /**
        * annotated方法element。
        * @param method 方法
        * @param pd pd
        * @param attributes attributes
         */
        protected AnnotatedMethodElement(Method method, PropertyDescriptor pd, AnnotationAttributes attributes) {
            /**
            * 父。
            * @param method 方法
            * @param pd pd
            * @param attributes attributes
             */
            super(method, pd, attributes);
            this.method = method;
        }
    }

    /**
    * annotated字段element类。
    *
    * @author CH
    * @since 4.0.0
     */
    public class AnnotatedFieldElement extends AnnotatedInjectElement {

        /** 字段 */
        public final Field field;

        /**
        * annotated字段element。
        * @param field 字段
        * @param attributes attributes
         */
        protected AnnotatedFieldElement(Field field, AnnotationAttributes attributes) {
            /**
            * 父。
            * @param field 字段
            * @param null 空
            * @param attributes attributes
             */
            super(field, null, attributes);
            this.field = field;
        }
    }
}

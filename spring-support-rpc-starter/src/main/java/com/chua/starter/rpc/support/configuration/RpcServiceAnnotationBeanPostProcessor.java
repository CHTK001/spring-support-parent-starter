package com.chua.starter.rpc.support.configuration;

import com.chua.common.support.rpc.RpcServer;
import com.chua.common.support.rpc.RpcService;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.SpringCompatUtils;
import com.chua.starter.rpc.support.annotation.AnnotationPropertyValuesAdapter;
import com.chua.starter.rpc.support.attrbute.RpcAttribute;
import com.chua.starter.rpc.support.filter.ScanExcludeFilter;
import com.chua.starter.rpc.support.holder.ServicePackagesHolder;
import com.chua.starter.rpc.support.properties.RpcProperties;
import com.chua.starter.rpc.support.service.RpcServiceBean;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.util.*;

import static com.chua.starter.common.support.configuration.SpringBeanUtils.resolveInterfaceName;
import static org.springframework.context.annotation.AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR;
import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;
import static org.springframework.util.ClassUtils.resolveClassName;

/**
 * rpc资源注释配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/07
 */
@EnableConfigurationProperties(RpcProperties.class)
@Slf4j
public class RpcServiceAnnotationBeanPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware,
        ResourceLoaderAware, BeanClassLoaderAware, ApplicationContextAware, InitializingBean  {
    private ResourceLoader resourceLoader;
    private Environment environment;
    private Set<String> packagesToScan;
    private BeanDefinitionRegistry registry;
    private Set<String> resolvedPackagesToScan;
    private boolean scanned;
    private final static List<Class<? extends Annotation>> SERVICE_ANNOTATION_TYPES = Lists.newArrayList(
            RpcService.class
    );
    private final ServicePackagesHolder servicePackagesHolder = ServicePackagesHolder.getInstance();
    private ApplicationContext applicationContext;
    private ClassLoader classLoader;

    private RpcProperties rpcProperties;
    private RpcServer rpcServer;


    @Override
    public void afterPropertiesSet() throws Exception {
        rpcProperties = Binder.get(SpringBeanUtils.getEnvironment()).bindOrCreate(RpcProperties.PRE, RpcProperties.class);
        if(!rpcProperties.isOpen()) {
            return;
        }
        if (null == rpcProperties || CollectionUtils.isEmpty(rpcProperties.getScan())) {
            packagesToScan = Collections.singleton(applicationContext.getBeansWithAnnotation(SpringBootApplication.class).values().iterator().next().getClass().getPackage().getName());
        } else {
            packagesToScan = rpcProperties.getScan();
        }
        this.resolvedPackagesToScan = resolvePackagesToScan(packagesToScan);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if(!rpcProperties.isOpen()) {
            return;
        }
        if (this.registry == null) {
            // In spring 3.x, may be not call postProcessBeanDefinitionRegistry()
            this.registry = (BeanDefinitionRegistry) beanFactory;
        }

        // scan bean definitions
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            Map<String, Object> annotationAttributes = getServiceAnnotationAttributes(beanDefinition);
            if (annotationAttributes != null) {
                Object bean = beanFactory.getBean(beanName);
                //
                processAnnotatedBeanDefinition(beanName, (AnnotatedBeanDefinition) beanDefinition, annotationAttributes);
            }
        }

        if (!scanned) {
            // In spring 3.x, may be not call postProcessBeanDefinitionRegistry(), so scan service class here
            scanServiceBeans(resolvedPackagesToScan, registry);
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if(!rpcProperties.isOpen()) {
            return;
        }
        this.registry = registry;
        scanServiceBeans(resolvedPackagesToScan, registry);
    }


    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.rpcProperties = Binder.get(environment).bindOrCreate(RpcProperties.PRE, RpcProperties.class);
        this.rpcServer = RpcServer.createService(rpcProperties.getType().name(),
                rpcProperties.getRegistry(),
                rpcProperties.getProtocols(),
                environment.resolvePlaceholders(rpcProperties.getApplicationName())
        );
    }


    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    private void processAnnotatedBeanDefinition(String refServiceBeanName, AnnotatedBeanDefinition refServiceBeanDefinition, Map<String, Object> attributes) {

        Map<String, Object> serviceAnnotationAttributes = new LinkedHashMap<>(attributes);

        // get bean class from return type
        String returnTypeName = SpringCompatUtils.getFactoryMethodReturnType(refServiceBeanDefinition);
        Class<?> beanClass = ClassUtils.resolveClassName(returnTypeName, classLoader);

        String serviceInterface = SpringBeanUtils.resolveInterfaceName(serviceAnnotationAttributes, beanClass);

        // ServiceBean Bean name
        String serviceBeanName = generateServiceBeanName(serviceAnnotationAttributes, serviceInterface);

        AbstractBeanDefinition serviceBeanDefinition = buildServiceBeanDefinition(serviceAnnotationAttributes, serviceInterface, refServiceBeanName);

        // set id
        serviceBeanDefinition.getPropertyValues().add(RpcAttribute.ID, serviceBeanName);

        registerServiceBeanDefinition(serviceBeanName, serviceBeanDefinition, serviceInterface);
    }
    private Map<String, Object> getServiceAnnotationAttributes(BeanDefinition beanDefinition) {
        if (beanDefinition instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
            MethodMetadata factoryMethodMetadata = SpringCompatUtils.getFactoryMethodMetadata(annotatedBeanDefinition);
            if (factoryMethodMetadata != null) {
                // try all dubbo service annotation types
                for (Class<? extends Annotation> annotationType : SERVICE_ANNOTATION_TYPES) {
                    if (factoryMethodMetadata.isAnnotated(annotationType.getName())) {
                        // Since Spring 5.2
                        // return factoryMethodMetadata.getAnnotations().get(annotationType).filterDefaultValues().asMap();
                        // Compatible with Spring 4.x
                        return factoryMethodMetadata.getAnnotationAttributes(annotationType.getName());
                    }
                }
            }
        }
        return null;
    }
    private Set<String> resolvePackagesToScan(Set<String> packagesToScan) {
        Set<String> resolvedPackagesToScan = new LinkedHashSet<>(packagesToScan.size());
        for (String packageToScan : packagesToScan) {
            if (StringUtils.hasText(packageToScan)) {
                String resolvedPackageToScan = environment.resolvePlaceholders(packageToScan.trim());
                resolvedPackagesToScan.add(resolvedPackageToScan);
            }
        }
        return resolvedPackagesToScan;
    }

    private void scanServiceBeans(Set<String> packagesToScan, BeanDefinitionRegistry registry) {

        scanned = true;
        if (CollectionUtils.isEmpty(packagesToScan)) {
            if (log.isWarnEnabled()) {
                log.warn("packagesToScan is empty , ServiceBean registry will be ignored!");
            }
            return;
        }

       RpcClassPathBeanDefinitionScanner scanner =
                new RpcClassPathBeanDefinitionScanner(registry, environment, resourceLoader);

        BeanNameGenerator beanNameGenerator = resolveBeanNameGenerator(registry);
        scanner.setBeanNameGenerator(beanNameGenerator);
        for (Class<? extends Annotation> annotationType : SERVICE_ANNOTATION_TYPES) {
            scanner.addIncludeFilter(new AnnotationTypeFilter(annotationType));
        }

        ScanExcludeFilter scanExcludeFilter = new ScanExcludeFilter();
        scanner.addExcludeFilter(scanExcludeFilter);

        for (String packageToScan : packagesToScan) {

            // Registers @Service Bean first
            scanner.scan(packageToScan);

            // Finds all BeanDefinitionHolders of @Service whether @ComponentScan scans or not.
            Set<BeanDefinitionHolder> beanDefinitionHolders =
                    findServiceBeanDefinitionHolders(scanner, packageToScan, registry, beanNameGenerator);

            if (!CollectionUtils.isEmpty(beanDefinitionHolders)) {
                if (log.isInfoEnabled()) {
                    List<String> serviceClasses = new ArrayList<>(beanDefinitionHolders.size());
                    for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders) {
                        serviceClasses.add(beanDefinitionHolder.getBeanDefinition().getBeanClassName());
                    }
                    log.info("Found {} classes annotated by @Service under package [{}]: {}", beanDefinitionHolders.size(), packageToScan, serviceClasses);
                }

                for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders) {
                    processScannedBeanDefinition(beanDefinitionHolder);
                    servicePackagesHolder.addScannedClass(beanDefinitionHolder.getBeanDefinition().getBeanClassName());
                }
            } else {
                if (log.isWarnEnabled()) {
                    log.warn("No class annotated by @Service was found under package [{}], ignore re-scanned classes: {}", packageToScan, scanExcludeFilter.getExcludedCount());
                }
            }

            servicePackagesHolder.addScannedPackage(packageToScan);
        }
    }
    /**
     * 建议使用BeanNameGenerator实例，因为它可能引用了 bean 名称生成，这可能成为一个潜在的问题。
     *
     * @param registry {@link BeanDefinitionRegistry}， Bean定义注册表，用于注册bean定义。
     * @return {@link BeanNameGenerator} 实例，这是一个用于生成bean名称的实例。
     * @see SingletonBeanRegistry 单例Bean注册表。
     * @since 2.5.8 从此版本开始可用。
     */
    private BeanNameGenerator resolveBeanNameGenerator(BeanDefinitionRegistry registry) {

        BeanNameGenerator beanNameGenerator = null;

        if (registry instanceof SingletonBeanRegistry singletonBeanRegistry) {
            beanNameGenerator = (BeanNameGenerator) singletonBeanRegistry.getSingleton(CONFIGURATION_BEAN_NAME_GENERATOR);
        }

        if (beanNameGenerator == null) {

            if (log.isInfoEnabled()) {

                log.info("BeanNameGenerator bean can't be found in BeanFactory with name ["
                        + CONFIGURATION_BEAN_NAME_GENERATOR + "]");
                log.info("BeanNameGenerator will be a instance of {} , it maybe a potential problem on bean name generation.", AnnotationBeanNameGenerator.class.getName());
            }

            beanNameGenerator = new AnnotationBeanNameGenerator();

        }

        return beanNameGenerator;

    }

    /**
     * 查找类型上注解了指定注解的{@link BeanDefinitionHolder BeanDefinitionHolders}集合。
     *
     * @param scanner       用于扫描classpath中Bean定义的{@link ClassPathBeanDefinitionScanner}
     * @param packageToScan 需要扫描的包
     * @param registry      Bean定义注册表{@link BeanDefinitionRegistry}
     * @return 非空的BeanDefinitionHolder集合，包含所有匹配的Bean定义
     * @since 2.5.8
     */

    private Set<BeanDefinitionHolder> findServiceBeanDefinitionHolders(
            ClassPathBeanDefinitionScanner scanner, String packageToScan, BeanDefinitionRegistry registry,
            BeanNameGenerator beanNameGenerator) {

        Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents(packageToScan);

        Set<BeanDefinitionHolder> beanDefinitionHolders = new LinkedHashSet<>(beanDefinitions.size());

        for (BeanDefinition beanDefinition : beanDefinitions) {

            String beanName = beanNameGenerator.generateBeanName(beanDefinition, registry);
            BeanDefinitionHolder beanDefinitionHolder = new BeanDefinitionHolder(beanDefinition, beanName);
            beanDefinitionHolders.add(beanDefinitionHolder);

        }

        return beanDefinitionHolders;

    }

    private void processScannedBeanDefinition(BeanDefinitionHolder beanDefinitionHolder) {

        Class<?> beanClass = resolveClass(beanDefinitionHolder);

        Annotation service = findServiceAnnotation(beanClass);

        // The attributes of @Service annotation
        Map<String, Object> serviceAnnotationAttributes = AnnotationUtils.getAnnotationAttributes(service, true);

        String serviceInterface = resolveInterfaceName(serviceAnnotationAttributes, beanClass);

        String annotatedServiceBeanName = beanDefinitionHolder.getBeanName();

        // ServiceBean Bean name
        String beanName = generateServiceBeanName(serviceAnnotationAttributes, serviceInterface);

        AbstractBeanDefinition serviceBeanDefinition =
                buildServiceBeanDefinition(serviceAnnotationAttributes, serviceInterface, annotatedServiceBeanName);

        registerServiceBeanDefinition(beanName, serviceBeanDefinition, serviceInterface);

    }
    private void registerServiceBeanDefinition(String serviceBeanName, AbstractBeanDefinition serviceBeanDefinition, String serviceInterface) {
        // check service bean
        if (registry.containsBeanDefinition(serviceBeanName)) {
            BeanDefinition existingDefinition = registry.getBeanDefinition(serviceBeanName);
            if (existingDefinition.equals(serviceBeanDefinition)) {
                // exist equipment bean definition
                return;
            }

            String msg = "Found duplicated BeanDefinition of service interface [" + serviceInterface + "] with bean name [" + serviceBeanName +
                    "], existing definition [ " + existingDefinition + "], new definition [" + serviceBeanDefinition + "]";
            log.error(msg);
            throw new BeanDefinitionStoreException(serviceBeanDefinition.getResourceDescription(), serviceBeanName, msg);
        }

        registry.registerBeanDefinition(serviceBeanName, serviceBeanDefinition);
        if (log.isInfoEnabled()) {
            log.info("Register ServiceBean[{}]: {}", serviceBeanName, serviceBeanDefinition);
        }
    }
    /**
     * Find the {@link Annotation annotation} of @Service
     *
     * @param beanClass the {@link Class class} of Bean
     * @return <code>null</code> if not found
     * @since 2.7.3
     */
    private Annotation findServiceAnnotation(Class<?> beanClass) {
        return SERVICE_ANNOTATION_TYPES
                .stream()
                .map(annotationType -> findAnnotation(beanClass, annotationType))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }


    private String generateServiceBeanName(Map<String, Object> serviceAnnotationAttributes, String serviceInterface) {
        String sb = "RpcServiceBean" + serviceInterface +
                serviceAnnotationAttributes.get("version") +
                serviceAnnotationAttributes.get("group");
        return environment.resolvePlaceholders(sb);
    }

    private Class<?> resolveClass(BeanDefinitionHolder beanDefinitionHolder) {

        BeanDefinition beanDefinition = beanDefinitionHolder.getBeanDefinition();

        return resolveClass(beanDefinition);

    }

    private Class<?> resolveClass(BeanDefinition beanDefinition) {

        String beanClassName = beanDefinition.getBeanClassName();

        return resolveClassName(Objects.requireNonNull(beanClassName), classLoader);

    }

    /**
     * Build the {@link AbstractBeanDefinition Bean Definition}
     *
     * @param serviceAnnotationAttributes 服务注释属性
     * @param serviceInterface            服务接口
     * @param refServiceBeanName          ref服务bean名称
     * @return {@link AbstractBeanDefinition}
     * @since 2.7.3
     */
    private AbstractBeanDefinition buildServiceBeanDefinition(Map<String, Object> serviceAnnotationAttributes,
                                                              String serviceInterface,
                                                              String refServiceBeanName) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(RpcServiceBean.class);
        AbstractBeanDefinition rootBeanDefinition = builder.getBeanDefinition();
        ConstructorArgumentValues constructorArgumentValues = rootBeanDefinition.getConstructorArgumentValues();

        MutablePropertyValues propertyValues = rootBeanDefinition.getPropertyValues();

        propertyValues.addPropertyValues(new AnnotationPropertyValuesAdapter(serviceAnnotationAttributes, environment));
        addPropertyReference(builder, "ref", refServiceBeanName);
        propertyValues.addPropertyValue("id", refServiceBeanName);
        // Set interface
        try {
            Class<?> aClass = ClassUtils.forName(serviceInterface, classLoader);
            propertyValues.addPropertyValue("interfaceName", serviceInterface);
            propertyValues.addPropertyValue("interfaceClass", aClass);
            constructorArgumentValues.addGenericArgumentValue(aClass);
            constructorArgumentValues.addGenericArgumentValue(serviceAnnotationAttributes.get("type"));
            constructorArgumentValues.addGenericArgumentValue(rpcProperties);
            constructorArgumentValues.addGenericArgumentValue(rpcServer);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        return rootBeanDefinition;

    }

    private String[] resolveStringArray(String[] strs) {
        if (strs == null) {
            return null;
        }
        for (int i = 0; i < strs.length; i++) {
            strs[i] = environment.resolvePlaceholders(strs[i]);
        }
        return strs;
    }

    private void addPropertyReference(BeanDefinitionBuilder builder, String propertyName, String beanName) {
        String resolvedBeanName = environment.resolvePlaceholders(beanName);
        builder.addPropertyReference(propertyName, resolvedBeanName);
    }

    private void addPropertyValue(BeanDefinitionBuilder builder, String propertyName, String value) {
        String resolvedBeanName = environment.resolvePlaceholders(value);
        builder.addPropertyValue(propertyName, resolvedBeanName);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

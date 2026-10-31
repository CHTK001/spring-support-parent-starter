package com.chua.starter.rpc.support.configuration;

import com.chua.common.support.network.rpc.RpcProtocolConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.network.rpc.RpcServer;
import com.chua.common.support.network.rpc.RpcService;
import com.chua.common.support.network.rpc.RpcType;
import com.chua.starter.common.support.utils.SpringCompatUtils;
import com.chua.starter.rpc.support.annotation.AnnotationPropertyValuesAdapter;
import com.chua.starter.rpc.support.attrbute.RpcAttribute;
import com.chua.starter.rpc.support.filter.ScanExcludeFilter;
import com.chua.starter.rpc.support.holder.ServicePackagesHolder;
import com.chua.starter.rpc.support.monitor.RpcServerHolder;
import com.chua.starter.rpc.support.properties.RpcProperties;
import com.chua.starter.rpc.support.service.RpcServiceBean;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.chua.starter.common.support.logger.ModuleLog.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.springframework.context.annotation.AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR;
import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;
import static org.springframework.util.ClassUtils.resolveClassName;

/**
 * rpc资源注释配置
 *
 * @since 2024/04/07
 * @author CH
 */
@ConditionalOnProperty(prefix = "plugin.rpc", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RpcProperties.class)
public class RpcServiceAnnotationBeanPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware,
        ResourceLoaderAware, BeanClassLoaderAware, ApplicationContextAware, InitializingBean  {

    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(RpcServiceAnnotationBeanPostProcessor.class);
    /**
     * Resourceloader
    */
    private ResourceLoader resourceLoader;
    /**
     * 环境
    */
    private Environment environment;
    /**
     * 包toscan
    */
    private Set<String> packagesToScan;
    /**
     * 注册表
    */
    private BeanDefinitionRegistry registry;
    /**
     * resolvedpackagestoscan
    */
    private Set<String> resolvedPackagesToScan;
    /**
     * Scanned
    */
    private boolean scanned;
    private final static List<Class<? extends Annotation>> SERVICE_ANNOTATION_TYPES = Lists.newArrayList(
            RpcService.class
    );
    /**
     * 服务packagesholder
    */
    private final ServicePackagesHolder servicePackagesHolder = ServicePackagesHolder.getInstance();
    /**
     * Application上下文
    */
    private ApplicationContext applicationContext;
    /**
     * Classloader
    */
    private ClassLoader classLoader;

    /**
     * RPC属性
    */
    private RpcProperties rpcProperties;
    /**
     * RPC服务器
    */
    private RpcServer rpcServer;

    /**
     * 当前上下文是否导出了 @RpcService 服务。
     * <p>纯消费端不导出任何服务，无需启动服务端（JSON 协议会真正监听端口）。</p>
     */
    private boolean hasServiceBean;


    @Override
    /**
     * 之后属性设置
     * @return 返回值
     * @throws Exception 异常
    */
    public void afterPropertiesSet() throws Exception {
        rpcProperties = Binder.get(this.environment).bindOrCreate(RpcProperties.PRE, RpcProperties.class);
        if(!rpcProperties.isEnable()) {
            return;
        }
        if (null == rpcProperties || rpcProperties.getScan() == null || rpcProperties.getScan().isEmpty()) {
            packagesToScan = Collections.singleton(applicationContext.getBeansWithAnnotation(SpringBootApplication.class).values().iterator().next().getClass().getPackage().getName());
        } else {
            packagesToScan = rpcProperties.getScan();
        }
        this.resolvedPackagesToScan = resolvePackagesToScan(packagesToScan);
    }

    @Override
    /**
     * Post处理Bean工厂
     * @param beanFactory 参数
     * @return 返回值
     * @throws BeansException 异常
    */
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if(!rpcProperties.isEnable()) {
            return;
        }
        if (this.registry == null) {
 // 入 spring 3.x, may be not call post处理Beandefinitionregistry()
            this.registry = (BeanDefinitionRegistry) beanFactory;
        }

 // 扫描 Bean definitions
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            Map<String, Object> annotationAttributes = getServiceAnnotationAttributes(beanDefinition);
            if (annotationAttributes != null) {
                Object bean = beanFactory.getBean(beanName);
                //
                /**
                 * 处理annotatedbeandefinition。
                 * @param beanName Bean名称
                 * @param (AnnotatedBeanDefinition (annotatedbeandefinition
                 */
                processAnnotatedBeanDefinition(beanName, (AnnotatedBeanDefinition) beanDefinition, annotationAttributes);
            }
        }

        if (!scanned) {
 // 入 spring 3.x, may be not call post处理Beandefinitionregistry(), so 扫描 服务 类 here
            /**
             * 扫描服务Bean。
             * @param resolvedPackagesToScan resolved包转为扫描
             * @param registry registry
             */
            scanServiceBeans(resolvedPackagesToScan, registry);
        }

 // 启动 RPC 服务端（Dubbo/SOFA 在 注册() 时已导出，此处为 JSON 等
 // 需要显式 之后属性设置() 才能启动监听服务的实现提供兜底）。
 // 纯消费端没有导出任何服务，不启动服务端，避免 JSON 等协议白白占用端口。
        if (rpcServer != null && hasServiceBean) {
            rpcServer.afterPropertiesSet();
        }
    }

    @Override
    /**
     * Post处理Beandefinitionregistry
     * @param registry 参数
     * @return 返回值
     * @throws BeansException 异常
    */
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if(!rpcProperties.isEnable()) {
            return;
        }
        this.registry = registry;
        /**
         * 扫描服务Bean。
         * @param resolvedPackagesToScan resolved包转为扫描
         * @param registry registry
         */
        scanServiceBeans(resolvedPackagesToScan, registry);
    }


    @Override
    /**
     * 设置resource加载
     * @param resourceLoader 参数
     * @return 返回值
    */
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    @Override
    /**
     * 设置环境
     * @param environment 参数
     * @return 返回值
    */
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.rpcProperties = Binder.get(environment).bindOrCreate(RpcProperties.PRE, RpcProperties.class);
        if (!rpcProperties.isEnable()) {
            log.info("[RPC] 服务状态: [{}]", disabled());
            return;
        }
        List<RpcRegistryConfig> registryConfigs = rpcProperties.getRegistry();
        List<RpcProtocolConfig> protocolConfigs = rpcProperties.getProtocols();
        RpcProtocolConfig protocolConfig = (protocolConfigs != null && !protocolConfigs.isEmpty()) ? protocolConfigs.getFirst() : null;
        String appName = environment.resolvePlaceholders(rpcProperties.getApplicationName());
        this.rpcServer = RpcServer.createService(rpcProperties.getType().name(), registryConfigs, protocolConfig, appName);
        RpcServerHolder.setRpcServer(this.rpcServer);
        /**
         * printstartup信息。
         */
        printStartupInfo();
    }
    
    /**
     * 打印启动配置信息
     */
    private void printStartupInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[RPC] ════════════════════════════════════════\n");
        sb.append("[RPC] 服务配置\n");
        sb.append("[RPC] ├─ 类型: ").append(highlight(rpcProperties.getType().name())).append("\n");
        sb.append("[RPC] ├─ 应用名: ").append(highlight(rpcProperties.getApplicationName())).append("\n");
        sb.append("[RPC] ├─ 状态: [").append(enabled()).append("]\n");

        // springbean / none 不产生网络通信：注册中心与远端协议配置均不生效，
        // 直接打印配置项会让人误以为仍在使用 dubbo 等远端协议。
        boolean localOnly = rpcProperties.getType() == RpcType.SPRINGBEAN
                || rpcProperties.getType() == RpcType.NONE;

        // 注册中心
        if (localOnly) {
            sb.append("[RPC] ├─ 注册中心: 无（本地 Bean 直调，无网络传输）\n");
        } else if (rpcProperties.getRegistry() != null && !rpcProperties.getRegistry().isEmpty()) {
            sb.append("[RPC] ├─ 注册中心:\n");
            for (var reg : rpcProperties.getRegistry()) {
                sb.append("[RPC] │  └─ ").append(address(reg.getAddress())).append("\n");
            }
        }

        // 协议
        if (localOnly) {
            sb.append("[RPC] └─ 协议: 本地 Bean 直调（无网络传输）\n");
        } else if (rpcProperties.getProtocols() != null && !rpcProperties.getProtocols().isEmpty()) {
            sb.append("[RPC] └─ 协议:\n");
            for (var protocol : rpcProperties.getProtocols()) {
                String protocolName = StringUtils.hasText(protocol.name())
                        ? protocol.name() : rpcProperties.getType().name().toLowerCase();
                Integer port = protocol.port();
                String endpoint;
                if (StringUtils.hasText(protocol.host())) {
                    endpoint = port != null ? protocol.host() + ":" + port : protocol.host();
                } else {
                    endpoint = port != null ? String.valueOf(port) : "default";
                }
                sb.append("[RPC]    └─ ").append(protocolName).append("://")
                        .append(address(endpoint)).append("\n");
            }
        }
        sb.append("[RPC] ════════════════════════════════════════");
        log.info(sb.toString());
    }


    @Override
    /**
     * 设置Bean类加载
     * @param classLoader 参数
     * @return 返回值
    */
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * 处理annotatedbeandefinition
     *
     * @param refServiceBeanName ref服务Bean名称
     * @param refServiceBeanDefinition ref服务Beandefinition
     * @param attributes attributes
     */
    private void processAnnotatedBeanDefinition(String refServiceBeanName, AnnotatedBeanDefinition refServiceBeanDefinition, Map<String, Object> attributes) {

        Map<String, Object> serviceAnnotationAttributes = new LinkedHashMap<>(attributes);

 // 获取 Bean 类 从 申报 类型
        String returnTypeName = SpringCompatUtils.getFactoryMethodReturnType(refServiceBeanDefinition);
        Class<?> beanClass = ClassUtils.resolveClassName(returnTypeName, classLoader);

        String serviceInterface = resolveServiceInterfaceName(serviceAnnotationAttributes, beanClass);

 // 服务Bean Bean 名称
        String serviceBeanName = generateServiceBeanName(serviceAnnotationAttributes, serviceInterface);

        AbstractBeanDefinition serviceBeanDefinition = buildServiceBeanDefinition(serviceAnnotationAttributes, serviceInterface, refServiceBeanName);

 // 设置 标识
        serviceBeanDefinition.getPropertyValues().add(RpcAttribute.ID, serviceBeanName);

        /**
         * 注册服务Beandefinition。
         * @param serviceBeanName 服务Bean名称
         * @param serviceBeanDefinition 服务Beandefinition
         * @param serviceInterface 服务接口
         */
        registerServiceBeanDefinition(serviceBeanName, serviceBeanDefinition, serviceInterface);
    }
    /**
     * 获取服务注解attributes
     *
     * @param beanDefinition Beandefinition
     * @return 获取服务注解attributes的结果
     */
    private Map<String, Object> getServiceAnnotationAttributes(BeanDefinition beanDefinition) {
        if (beanDefinition instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
            MethodMetadata factoryMethodMetadata = SpringCompatUtils.getFactoryMethodMetadata(annotatedBeanDefinition);
            if (factoryMethodMetadata != null) {
 // 尝试 全部 Dubbo 服务 注解 类型
                for (Class<? extends Annotation> annotationType : SERVICE_ANNOTATION_TYPES) {
                    if (factoryMethodMetadata.isAnnotated(annotationType.getName())) {
 // 自 Spring 5.2
 // 申报 工厂方法metadata.获取注解().获取(注解类型).过滤器默认值().as映射();
                        // Compatible with Spring 4.x
                        return factoryMethodMetadata.getAnnotationAttributes(annotationType.getName());
                    }
                }
            }
        }
        return null;
    }
    /**
     * 解析包转为扫描
     *
     * @param packagesToScan 包转为扫描
     * @return resolve包转为扫描的结果
     */
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

    /**
     * 扫描服务Bean
     *
     * @param packagesToScan 包转为扫描
     * @param registry registry
     */
    private void scanServiceBeans(Set<String> packagesToScan, BeanDefinitionRegistry registry) {

        scanned = true;
        if (packagesToScan == null || packagesToScan.isEmpty()) {
        if (log.isWarnEnabled()) {
                log.warn("[RPC] packagesToScan 为空, 跳过服务扫描");
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

 // 注册 @服务 Bean 第一个
            scanner.scan(packageToScan);

 // 查找 全部 Beandefinitionholders 的 @服务 whether @组件扫描 扫描 或 not.
            Set<BeanDefinitionHolder> beanDefinitionHolders =
                    /**
                     * 查找服务Beandefinitionholders。
                     * @param scanner scanner
                     * @param packageToScan 包转为扫描
                     * @param registry registry
                     * @param beanNameGenerator Bean名称生成器
                     */
                    findServiceBeanDefinitionHolders(scanner, packageToScan, registry, beanNameGenerator);

            if (beanDefinitionHolders != null && !beanDefinitionHolders.isEmpty()) {
                if (log.isInfoEnabled()) {
                    List<String> serviceClasses = new ArrayList<>(beanDefinitionHolders.size());
                    for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders) {
                        serviceClasses.add(beanDefinitionHolder.getBeanDefinition().getBeanClassName());
                    }
                    log.info("[RPC] 扫描包 [{}] 发现 {} 个 @RpcService: {}", packageToScan, highlight(beanDefinitionHolders.size()), serviceClasses);
                }

                for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders) {
                    /**
                     * 处理scannedbeandefinition。
                     * @param beanDefinitionHolder Beandefinitionholder
                     */
                    processScannedBeanDefinition(beanDefinitionHolder);
                    servicePackagesHolder.addScannedClass(beanDefinitionHolder.getBeanDefinition().getBeanClassName());
                }
            } else {
                if (log.isWarnEnabled()) {
                    log.warn("[RPC] 扫描包 [{}] 未发现 @RpcService", packageToScan);
                }
            }

            servicePackagesHolder.addScannedPackage(packageToScan);
        }
    }
    /**
     * 建议使用Bean名称生成器实例，因为它可能引用了 Bean 名称生成，这可能成为一个潜在的问题。
     *
     * @param registry {@link BeanDefinitionRegistry}， Bean定义注册表，用于注册Bean定义。
     * @return {@link Bean名称生成器} 实例，这是一个用于生成Bean名称的实例。
     * @see SingletonBeanRegistry 单例Bean注册表。
     * @since 2.5.8 从此版本开始可用。
     */
    private BeanNameGenerator resolveBeanNameGenerator(BeanDefinitionRegistry registry) {

        BeanNameGenerator beanNameGenerator = null;

        if (registry instanceof SingletonBeanRegistry singletonBeanRegistry) {
            beanNameGenerator = (BeanNameGenerator) singletonBeanRegistry.getSingleton(CONFIGURATION_BEAN_NAME_GENERATOR);
        }

        if (beanNameGenerator == null) {
            log.debug("[RPC] 使用默认 BeanNameGenerator: {}", AnnotationBeanNameGenerator.class.getSimpleName());
            beanNameGenerator = new AnnotationBeanNameGenerator();
        }

        return beanNameGenerator;

    }

    /**
     * 查找类型上注解了指定注解的{@link BeanDefinitionHolder BeanDefinitionHolders}集合。
     *
     * @param scanner       用于扫描类路径中Bean定义的{@link ClassPathBeanDefinitionScanner}
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

    /**
     * 处理scannedbeandefinition
     *
     * @param beanDefinitionHolder Beandefinitionholder
     */
    private void processScannedBeanDefinition(BeanDefinitionHolder beanDefinitionHolder) {

        Class<?> beanClass = resolveClass(beanDefinitionHolder);

        Annotation service = findServiceAnnotation(beanClass);

 // The attributes 的 @服务 注解
        Map<String, Object> serviceAnnotationAttributes = AnnotationUtils.getAnnotationAttributes(service, true);

        String serviceInterface = resolveServiceInterfaceName(serviceAnnotationAttributes, beanClass);

        String annotatedServiceBeanName = beanDefinitionHolder.getBeanName();

 // 服务Bean Bean 名称
        String beanName = generateServiceBeanName(serviceAnnotationAttributes, serviceInterface);

        AbstractBeanDefinition serviceBeanDefinition =
                /**
                 * 构建服务Beandefinition。
                 * @param serviceAnnotationAttributes 服务注解attributes
                 * @param serviceInterface 服务接口
                 * @param annotatedServiceBeanName annotated服务Bean名称
                 */
                buildServiceBeanDefinition(serviceAnnotationAttributes, serviceInterface, annotatedServiceBeanName);

        /**
         * 注册服务Beandefinition。
         * @param beanName Bean名称
         * @param serviceBeanDefinition 服务Beandefinition
         * @param serviceInterface 服务接口
         */
        registerServiceBeanDefinition(beanName, serviceBeanDefinition, serviceInterface);

    }
    /**
     * 注册服务Beandefinition
     *
     * @param serviceBeanName 服务Bean名称
     * @param serviceBeanDefinition 服务Beandefinition
     * @param serviceInterface 服务接口
     */
    private void registerServiceBeanDefinition(String serviceBeanName, AbstractBeanDefinition serviceBeanDefinition, String serviceInterface) {
 // 本上下文负责导出服务，需要启动 RPC 服务端
        this.hasServiceBean = true;
 // 检查 服务 Bean
        if (registry.containsBeanDefinition(serviceBeanName)) {
            BeanDefinition existingDefinition = registry.getBeanDefinition(serviceBeanName);
            if (existingDefinition.equals(serviceBeanDefinition)) {
 // exist equipment Bean definition
                return;
            }

            String msg = "[RPC] 发现重复的服务定义: " + serviceInterface + " -> " + serviceBeanName;
            log.error(msg);
            /**
             * Beandefinition存储异常。
             * @param serviceBeanDefinition.getResourceDescription( 服务Beandefinition.getresourcedescription(
             * @return Beandefinition存储异常的结果
             */
            throw new BeanDefinitionStoreException(serviceBeanDefinition.getResourceDescription(), serviceBeanName, msg);
        }

        registry.registerBeanDefinition(serviceBeanName, serviceBeanDefinition);
        if (log.isInfoEnabled()) {
            log.info("[RPC] 注册服务: {} -> {}", highlight(serviceInterface), serviceBeanName);
        }
    }
    /**
     * 查找 the {@link Annotation annotation} 的 @服务
     *
     * @param beanClass the {@link Class class} 的 Bean
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


    /**
     * generate服务Bean名称
     *
     * @param serviceAnnotationAttributes 服务注解attributes
     * @param serviceInterface 服务接口
     * @return generate服务Bean名称的结果
     */
    private String generateServiceBeanName(Map<String, Object> serviceAnnotationAttributes, String serviceInterface) {
        String sb = "RpcServiceBean" + serviceInterface +
                serviceAnnotationAttributes.get("version") +
                serviceAnnotationAttributes.get("group");
        return environment.resolvePlaceholders(sb);
    }

    /**
     * resolve类。
     * @param beanDefinitionHolder Beandefinitionholder
     * @return resolve类的结果
     */
    private Class<?> resolveClass(BeanDefinitionHolder beanDefinitionHolder) {

        BeanDefinition beanDefinition = beanDefinitionHolder.getBeanDefinition();

        return resolveClass(beanDefinition);

    }

    /**
     * resolve类。
     * @param beanDefinition Beandefinition
     * @return resolve类的结果
     */
    private Class<?> resolveClass(BeanDefinition beanDefinition) {

        String beanClassName = beanDefinition.getBeanClassName();

        return resolveClassName(Objects.requireNonNull(beanClassName), classLoader);

    }

    /**
     * 构建 the {@link AbstractBeanDefinition Bean Definition}
     *
     * @param serviceAnnotationAttributes 服务注释属性
     * @param serviceInterface            服务接口
     * @param refServiceBeanName          ref服务Bean名称
     * @return {@link 抽象Beandefinition}
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
        /**
         * 添加财产引用。
         * @param builder 构建器
         * @param "ref" "ref"
         * @param refServiceBeanName ref服务Bean名称
         */
        addPropertyReference(builder, "ref", refServiceBeanName);
        propertyValues.addPropertyValue("id", refServiceBeanName);
 // 设置 接口
        try {
            Class<?> aClass = ClassUtils.forName(serviceInterface, classLoader);
            propertyValues.addPropertyValue("interfaceName", serviceInterface);
            propertyValues.addPropertyValue("interfaceClass", aClass);
            constructorArgumentValues.addGenericArgumentValue(aClass);
            constructorArgumentValues.addGenericArgumentValue(serviceAnnotationAttributes.get("type"));
            constructorArgumentValues.addGenericArgumentValue(rpcProperties);
            constructorArgumentValues.addGenericArgumentValue(rpcServer);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("RPC 服务类加载失败: " + e.getMessage(), e);
        }

        return rootBeanDefinition;

    }

    /**
     * 解析字符串array
     *
     * @param strs strs
     * @return resolve字符串array的结果
     */
    private String[] resolveStringArray(String[] strs) {
        if (strs == null) {
            return null;
        }
        for (int i = 0; i < strs.length; i++) {
            strs[i] = environment.resolvePlaceholders(strs[i]);
        }
        return strs;
    }

    /**
     * 添加财产引用
     *
     * @param builder 构建器
     * @param propertyName 财产名称
     * @param beanName Bean名称
     */
    private void addPropertyReference(BeanDefinitionBuilder builder, String propertyName, String beanName) {
        String resolvedBeanName = environment.resolvePlaceholders(beanName);
        builder.addPropertyReference(propertyName, resolvedBeanName);
    }

    /**
     * 添加财产值
     *
     * @param builder 构建器
     * @param propertyName 财产名称
     * @param value 值
     */
    private void addPropertyValue(BeanDefinitionBuilder builder, String propertyName, String value) {
        String resolvedBeanName = environment.resolvePlaceholders(value);
        builder.addPropertyValue(propertyName, resolvedBeanName);
    }

    @Override
    /**
     * 设置application上下文
     * @param applicationContext 参数
     * @return 返回值
     * @throws BeansException 异常
    */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 从服务注解属性与默认接口类解析出实际服务接口的全限定名。
     *
     * @param attributes              服务注解属性映射
     * @param defaultInterfaceClass   默认接口类（Bean 实现类）
     * @return 解析得到的接口全限定名
     */
    private static String resolveServiceInterfaceName(Map<String, Object> attributes, Class<?> defaultInterfaceClass) {
        if (attributes != null) {
            String interfaceClassName = (String) attributes.get("interfaceName");
            if (interfaceClassName != null && !interfaceClassName.isEmpty()) {
                if ("org.apache.dubbo.rpc.service.GenericService".equals(interfaceClassName)
                        || "com.alibaba.dubbo.rpc.service.GenericService".equals(interfaceClassName)) {
                    /**
                     * illegal状态异常。
                     * @param interfaceName( 接口名称(
                     * @return illegal状态异常的结果
                     */
                    throw new IllegalStateException("@RpcService interfaceName() cannot be GenericService: " + interfaceClassName);
                }
                return interfaceClassName;
            }

            Object interfaceClassObj = attributes.get("interfaceClass");
            Class<?> interfaceClass = null;
            if (interfaceClassObj instanceof Class) {
                interfaceClass = (Class<?>) interfaceClassObj;
                if (void.class.equals(interfaceClass)) {
                    interfaceClass = null;
                }
            }
            if (interfaceClass != null) {
                return interfaceClass.getName();
            }
            if (interfaceClassObj instanceof String) {
                String clsName = (String) interfaceClassObj;
                if (clsName != null && !clsName.isEmpty() && !"void".equals(clsName)) {
                    return clsName;
                }
            }
        }

        if (defaultInterfaceClass != null) {
            Class<?>[] allInterfaces = ClassUtils.getAllInterfacesForClass(defaultInterfaceClass);
            if (allInterfaces.length > 0) {
                return allInterfaces[0].getName();
            }
        }

        /**
         * illegal状态异常。
         * @param interfaceClass( 接口类(
         * @return illegal状态异常的结果
         */
        throw new IllegalStateException("@RpcService interfaceClass() or interfaceName() or interface class must be present!");
    }
}

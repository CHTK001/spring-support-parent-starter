package com.chua.starter.datasource.configuration;

import com.chua.common.support.lang.datasource.schema.SchemaDriver;
import com.chua.common.support.lang.datasource.schema.SchemaDriverContext;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.datasource.properties.DataSourceScriptProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 数据源脚本 Bean 后处理器。
 *
 * <p>在 {@link DataSource} 初始化完成后立即执行数据库脚本，确保脚本先于
 * 依赖数据库的 Bean（实体管理器工厂、SqlSessionFactory 等）就绪。</p>
 *
 * <p><b>为什么单独成类</b>：{@link DataSourceScriptConfiguration} 是
 * {@code @AutoConfiguration} 自动配置类，若它自己同时实现 {@link BeanPostProcessor}，
 * 容器会把「自动配置类实例」与「{@code @Bean} 暴露的实例」注册成两个 bean，
 * 脚本可能执行两次。拆开后职责单一：配置类负责装配，处理器负责触发。</p>
 *
 * <p><b>为什么实现 {@link PriorityOrdered} 且取最高优先级</b>：BeanPostProcessor 注册得越晚，
 * 容器里已有的 bean 就越多，Spring 的 BeanPostProcessorChecker 会为每一个未被本处理器
 * 处理的 bean 打一条「is not eligible for getting processed」告警。取最高优先级让本处理器
 * 在容器极早期注册，既消除了这批噪音，也确保数据源脚本抢在所有依赖数据库的
 * Bean 之前执行 —— 这与本类最初的职责诉求一致。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class DataSourceScriptBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

    /**
     * 数据源脚本配置属性提供者。
     *
     * <p>用 {@link ObjectProvider} 延迟解析，而不是在构造器里直接注入
     * {@link DataSourceScriptProperties}：BeanPostProcessor 会在容器极早期被创建，
     * 直接注入 {@code @ConfigurationProperties} bean 会迫使它提前实例化，
     * 从而触发一串「Bean ... is not eligible for getting processed by all
     * BeanPostProcessors」告警。延迟到真正处理数据源时再取，容器启动顺序恢复正常。</p>
     */
    private final ObjectProvider<DataSourceScriptProperties> propertiesProvider;

    /**
     * 数据源脚本配置属性（首次使用时解析并缓存）
     */
    private volatile DataSourceScriptProperties dataSourceScriptProperties;

    /**
     * 已处理的数据源 Bean 名称集合，避免重复执行
     */
    private final Set<String> processedDataSources = Collections.synchronizedSet(new HashSet<>());

    /**
     * 已提示过禁用状态的数据源 Bean 名称集合，避免重复打印日志
     */
    private final Set<String> skippedDataSources = Collections.synchronizedSet(new HashSet<>());

    /**
     * 创建数据源脚本处理器
     *
     * @param propertiesProvider 数据源脚本配置属性提供者
     */
    public DataSourceScriptBeanPostProcessor(ObjectProvider<DataSourceScriptProperties> propertiesProvider) {
        this.propertiesProvider = propertiesProvider;
    }

    /**
     * 获取数据源脚本配置属性（首次调用时解析）。
     *
     * @return 数据源脚本配置属性
     */
    private DataSourceScriptProperties properties() {
        DataSourceScriptProperties current = dataSourceScriptProperties;
        if (current == null) {
            synchronized (this) {
                current = dataSourceScriptProperties;
                if (current == null) {
                    current = propertiesProvider.getObject();
                    dataSourceScriptProperties = current;
                }
            }
        }
        return current;
    }

    @Override
    /**
     * 处理器排序：取最高优先级，让本处理器在容器极早期注册。
     *
     * @return 最高优先级
     */
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    /**
     * Post处理之后initialization
     * @param bean 对象
     * @param beanName 字符串
     * @return 返回值
     * @throws BeansException 异常
    */
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 只有数据源才需要脚本配置：必须把 properties() 放在类型判断之内。
        // 否则容器里每个 bean 初始化时都会去解析 DataSourceScriptProperties，
        // 而它自身初始化时又会回调到这里 → 「bean is currently in creation」
        // 循环依赖，直接启动失败（2026-10-23 实测）。
        if (!(bean instanceof DataSource)) {
            return bean;
        }

        DataSourceScriptProperties properties = properties();
        if (!properties.isEnable()) {
            if (skippedDataSources.add(beanName)) {
                log.info("检测到 DataSource [{}] 初始化完成，但 [{}]=false，已跳过数据库脚本执行",
                        beanName, DataSourceScriptProperties.PRE + ".enable");
            }
            return bean;
        }

        DataSource ds = (DataSource) bean;
        // 避免重复处理同一个数据源
        if (processedDataSources.add(beanName)) {
            try {
                if (properties.getMode() == DataSourceScriptProperties.Mode.ENTITY) {
                    // 实体驱动模式：经 SchemaDriver SPI 按实体类初始化表结构，不走 SQL 脚本链路
                    log.info("检测到 DataSource [{}] 初始化完成，实体驱动模式，开始按实体初始化表结构...", beanName);
                    migrateWithEntityDriver(ds, properties);
                    log.info("DataSource [{}] 实体驱动表结构初始化完成", beanName);
                } else {
                    log.info("检测到 DataSource [{}] 初始化完成，开始执行数据库脚本...", beanName);
                    // 优先通过 SPI 获取 Flyway 高优先级实现（utils-support-flyway-starter 的 数据源Flyway）；
                    // 获取成功则委托其迁移，否则回退到内置 flywaylikepopulator。
                    DataSourceScriptConfiguration delegate = new DataSourceScriptConfiguration(properties);
                    if (delegate.tryMigrateWithSpiFlyway(ds, beanName)) {
                        log.info("DataSource [{}] 数据库脚本执行完成（SPI Flyway）", beanName);
                    } else {
                        DataSourceScriptConfiguration.FlywayLikePopulator populator =
                                new DataSourceScriptConfiguration.FlywayLikePopulator(ds, properties);
                        populator.populate();
                        log.info("DataSource [{}] 数据库脚本执行完成", beanName);
                    }
                }
            } catch (Exception e) {
                log.warn("执行数据库脚本失败: {}", beanName, e);
                if (!properties.isContinueOnError()) {
                    /**
                     * illegal状态异常。
                     *
                     * @param beanName Bean 名称
                     * @param e 异常
                     * @return illegal状态异常的结果
                     */
                    throw new IllegalStateException("执行数据库脚本失败: " + beanName, e);
                }
            }
        }
        return bean;
    }

    /**
     * 实体驱动模式执行入口：经 {@link SchemaDriver} SPI 解析 {@code entity} 扩展并执行结构初始化。
     *
     * <p>协议名复用 {@link DataSourceScriptConfiguration#resolveProtocol(DataSource)} 的
     * 产品名映射（同一份实现，避免两套协议判定）；实体类来自
     * {@code plugin.datasource.script.entity-packages} 配置的包扫描结果。</p>
     *
     * @param ds         目标数据源
     * @param properties 数据源脚本配置属性
     */
    private void migrateWithEntityDriver(DataSource ds, DataSourceScriptProperties properties) {
        SchemaDriver driver = resolveEntitySchemaDriver();
        List<Class<?>> entityClasses = scanEntityClasses(properties);
        SchemaDriverContext context = new SchemaDriverContext()
                .setDataSource(ds)
                .setProtocol(DataSourceScriptConfiguration.resolveProtocol(ds))
                .setEntityClasses(entityClasses)
                .setVerbose(properties.isVerbose());
        int created = driver.migrate(context);
        log.info("实体驱动处理 {} 个实体，新建 {} 张表", entityClasses.size(), created);
    }

    /**
     * 从 SPI 扩展中解析实体驱动实现（{@code entity} 扩展）。
     *
     * @return 实体驱动实例
     * @throws IllegalStateException classpath 上不存在 entity 扩展时抛出
     *         （通常为未引入 utils-support-datasource-starter）
     */
    private SchemaDriver resolveEntitySchemaDriver() {
        List<SchemaDriver> extensions
                = ServiceProvider.of(SchemaDriver.class).getNewExtensions(SchemaDriver.SPI_NAME);
        if (extensions != null) {
            for (SchemaDriver candidate : extensions) {
                if (candidate != null && SchemaDriver.ENTITY.equalsIgnoreCase(candidate.name())) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException(
                "未找到 SchemaDriver SPI 的 entity 实现，请确认 classpath 已引入 utils-support-datasource-starter");
    }

    /**
     * 扫描实体驱动模式下配置的实体包，收集参与建表的实体类。
     *
     * <p>仅收集具体类：接口、注解、枚举、抽象类、非静态内部类均不参与建表。
     * 未配置 {@code entity-packages} 时返回空列表并告警（由驱动侧跳过建表）。</p>
     *
     * @param properties 数据源脚本配置属性
     * @return 实体类列表
     */
    private List<Class<?>> scanEntityClasses(DataSourceScriptProperties properties) {
        List<String> packages = properties.getEntityPackages();
        if (CollectionUtils.isEmpty(packages)) {
            log.warn("实体驱动模式已启用，但未配置 [{}]，跳过实体扫描",
                    DataSourceScriptProperties.PRE + ".entity-packages");
            return Collections.emptyList();
        }
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        ClassLoader classLoader = DataSourceScriptBeanPostProcessor.class.getClassLoader();
        List<Class<?>> entityClasses = new ArrayList<>();
        for (String pkg : packages) {
            if (pkg == null || pkg.isBlank()) {
                continue;
            }
            for (BeanDefinition beanDefinition : scanner.findCandidateComponents(pkg.trim())) {
                String className = beanDefinition.getBeanClassName();
                if (className == null) {
                    continue;
                }
                try {
                    Class<?> clazz = ClassUtils.forName(className, classLoader);
                    if (isEntityCandidate(clazz)) {
                        entityClasses.add(clazz);
                    }
                } catch (ClassNotFoundException | LinkageError e) {
                    log.warn("实体类 {} 加载失败，已跳过: {}", className, e.getMessage());
                }
            }
        }
        return entityClasses;
    }

    /**
     * 判断类是否为可建表的实体候选。
     *
     * @param clazz 待判定类
     * @return true 表示可参与建表
     */
    private boolean isEntityCandidate(Class<?> clazz) {
        return !clazz.isInterface() && !clazz.isAnnotation() && !clazz.isEnum()
                && !clazz.isSynthetic() && !Modifier.isAbstract(clazz.getModifiers());
    }
}

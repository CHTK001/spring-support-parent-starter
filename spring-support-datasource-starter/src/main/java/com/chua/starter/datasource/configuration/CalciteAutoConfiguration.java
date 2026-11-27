package com.chua.starter.datasource.configuration;

import com.chua.calcite.support.conversion.CalciteDataSourceConversion;
import com.chua.datasource.support.engine.DataSourceConversion;
import com.chua.datasource.support.engine.DataSourceEnvironment;
import com.chua.starter.datasource.properties.CalciteDataSourceProperties;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.chua.common.support.utils.CollectionUtils;

/**
 * Calcite 聚合数据源自动配置。
 * <p>
 * 当 {@code plugin.datasource.calcite.enabled=true} 时，
 * 自动将所有已注册的动态数据源聚合为统一的 Calcite 数据源，
 * 支持跨库联邦查询。
 * </p>
 *
 * <p>
 * 用户可自行注册 {@link CalciteDataSourceConversion} 或
 * {@link DataSourceConversion} 类型的 Bean 来覆盖默认行为；
 * {@code plugin.datasource.calcite.bean-name} 可为聚合数据源追加注入别名，
 * {@code plugin.datasource.calcite.data-sources} 可限定参与聚合的数据源名单，
 * {@code plugin.datasource.calcite.register-mode} 控制注册语义：
 * {@code replace}（默认）把聚合数据源提升为主数据源（primary），
 * {@code derive} 保持历史行为，作为额外 Bean 派生注册（必须提供 bean-name）。
 * </p>
 *
 * @since 2026/07/14
 * @author CH
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(CalciteDataSourceConversion.class)
@ConditionalOnProperty(prefix = "plugin.datasource.calcite", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CalciteDataSourceProperties.class)
public class CalciteAutoConfiguration {

    /**
     * 聚合数据源的规范 Bean 名。运行期联邦管理器（CalciteFederationManager.BEAN_NAME）、
     * 热替换注册表与 {@code @ConditionalOnMissingBean} 覆盖语义都依赖该名称，不可更改；
     * 自定义 {@code bean-name} 只能以别名形式注册。
     */
    private static final String CANONICAL_BEAN_NAME = "calciteDataSource";

    /**
     * 注册 Calcite 数据源转换器 Bean
     * @return calcite数据源转换的结果
     */
    @Bean
    @ConditionalOnMissingBean(name = "calciteDataSourceConversion")
    public DataSourceConversion calciteDataSourceConversion() {
        /**
         * calcite数据源转换。
         * @return calcite数据源转换的结果
         */
        return new CalciteDataSourceConversion();
    }

    /**
     * 处理 {@code plugin.datasource.calcite.register-mode} 与 {@code bean-name}。
     *
     * <p>静态 {@code @Bean} + {@link BeanFactoryPostProcessor}：处理发生在
     * Bean 定义加载之后、实例化之前，不会提前实例化本配置类。这里刻意从
     * {@link Environment} 读取配置而不是注入 {@code CalciteDataSourceProperties}：
     * BFPP 阶段早于 {@code ConfigurationPropertiesBindingPostProcessor} 注册，
     * 过早实例化 properties Bean 会拿到未绑定 yaml 的默认值。</p>
     *
     * <ul>
     *   <li>{@code replace}（默认）：把其它被标记 primary 的 {@code DataSource} Bean
     *       定义降级，并将规范 Bean {@code calciteDataSource} 标记为 primary，宿主按
     *       类型注入 {@code DataSource} 时命中聚合数据源；原动态路由数据源仍可按
     *       名称或 {@code @Qualifier} 注入。</li>
     *   <li>{@code derive}：保持历史行为，聚合数据源作为额外 Bean 派生注册，
     *       不改动任何 primary 标记；此模式必须提供 {@code bean-name}。</li>
     * </ul>
     * 两种模式下配置的 {@code bean-name} 都会注册为规范 Bean 的别名。
     *
     * @param environment 环境配置
     * @return 注册模式处理器
     */
    @Bean
    public static BeanFactoryPostProcessor calciteRegisterModePostProcessor(Environment environment) {
        return beanFactory -> {
            if (!(beanFactory instanceof ConfigurableListableBeanFactory registry)) {
                return;
            }
            String mode = resolveRegisterMode(environment.getProperty("plugin.datasource.calcite.register-mode"));
            String customName = environment.getProperty("plugin.datasource.calcite.bean-name");
            boolean hasCustomName = customName != null && !customName.isBlank();
            if ("derive".equals(mode) && !hasCustomName) {
                throw new IllegalStateException(
                        "[Calcite] plugin.datasource.calcite.register-mode=derive 时必须提供 "
                                + "plugin.datasource.calcite.bean-name（派生模式以自定义名注册额外 Bean）");
            }
            if (hasCustomName) {
                registerAliasQuietly(registry, customName.trim());
            }
            if ("replace".equals(mode) && registry.containsBeanDefinition(CANONICAL_BEAN_NAME)) {
                demoteOtherPrimaryDataSources(registry);
                registry.getBeanDefinition(CANONICAL_BEAN_NAME).setPrimary(true);
                log.info("[Calcite] register-mode=replace: {} 已标记为主数据源（primary）", CANONICAL_BEAN_NAME);
            }
        };
    }

    /**
     * 解析 register-mode 原始配置。
     *
     * @param raw 原始值（null/空白按默认 replace 处理）
     * @return "replace" 或 "derive"（小写规范形式）
     */
    private static String resolveRegisterMode(String raw) {
        if (raw == null || raw.isBlank() || "replace".equalsIgnoreCase(raw.trim())) {
            return "replace";
        }
        if ("derive".equalsIgnoreCase(raw.trim())) {
            return "derive";
        }
        throw new IllegalStateException(
                "[Calcite] 非法的 plugin.datasource.calcite.register-mode: " + raw + "（仅支持 replace / derive）");
    }

    /**
     * 注册自定义 Bean 名为规范 Bean 的别名（规范名本身、同名 Bean 定义或已被其它 Bean 占用的别名均跳过）。
     *
     * @param registry Bean 工厂
     * @param alias    自定义别名
     */
    private static void registerAliasQuietly(ConfigurableListableBeanFactory registry, String alias) {
        if (CANONICAL_BEAN_NAME.equals(alias) || registry.containsBeanDefinition(alias)
                || (registry instanceof org.springframework.core.AliasRegistry aliasRegistry && aliasRegistry.isAlias(alias))) {
            return;
        }
        try {
            registry.registerAlias(CANONICAL_BEAN_NAME, alias);
            log.info("[Calcite] 聚合数据源 Bean 别名注册成功: {} -> {}", alias, CANONICAL_BEAN_NAME);
        } catch (IllegalStateException e) {
            log.warn("[Calcite] 聚合数据源 Bean 别名 {} 注册失败: {}", alias, e.getMessage());
        }
    }

    /**
     * 把其它被标记 primary 的 {@code DataSource} Bean 定义降级（replace 模式让位给聚合数据源）。
     * 非 DataSource 类型的 primary Bean 不受影响。
     *
     * @param registry Bean 工厂
     */
    private static void demoteOtherPrimaryDataSources(ConfigurableListableBeanFactory registry) {
        for (String name : registry.getBeanDefinitionNames()) {
            if (CANONICAL_BEAN_NAME.equals(name)) {
                continue;
            }
            try {
                BeanDefinition definition = registry.getBeanDefinition(name);
                if (!definition.isPrimary() || !isDataSourceType(registry, name)) {
                    continue;
                }
                definition.setPrimary(false);
                log.info("[Calcite] register-mode=replace: 数据源 Bean {} 的 primary 标记已让位给聚合数据源", name);
            } catch (Exception e) {
                log.debug("[Calcite] 评估 Bean {} 的 primary/类型失败，跳过: {}", name, e.getMessage());
            }
        }
    }

    /**
     * 判断 Bean 定义解析出的类型是否为 {@link DataSource}。
     *
     * @param registry Bean 工厂
     * @param name     Bean 名
     * @return true = DataSource 类型
     */
    private static boolean isDataSourceType(ConfigurableListableBeanFactory registry, String name) {
        try {
            Class<?> type = registry.getType(name);
            return type != null && DataSource.class.isAssignableFrom(type);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 创建 Calcite 聚合数据源 Bean。
     *
     * <p>{@code replace} 模式下无可聚合数据源或创建失败直接抛异常（fail-fast，
     * 宿主启动失败而非静默缺主数据源）；{@code derive} 模式保持历史行为，
     * 告警并返回 {@code null}。</p>
     *
     * @param calciteDataSourceConversion Calcite 数据源转换器
     * @param properties                  Calcite 数据源配置
     * @return 聚合数据源；derive 模式下未注册数据源或创建失败返回 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean(name = "calciteDataSource")
    public DataSource calciteDataSource(
            DataSourceConversion calciteDataSourceConversion,
            CalciteDataSourceProperties properties) {

        boolean replaceMode = isReplaceMode(properties);
        List<DataSource> dataSources = collectDataSources(properties);
        if (dataSources.isEmpty()) {
            if (replaceMode) {
                throw new IllegalStateException(
                        "[Calcite] register-mode=replace 时没有可聚合的数据源，无法创建主数据源");
            }
            log.warn("[Calcite] 没有找到可聚合的数据源，跳过聚合");
            return null;
        }

        DataSourceEnvironment environment = new DataSourceEnvironment();
        environment.setDatabaseType("CALCITE");

        try {
            DataSource calciteDataSource = calciteDataSourceConversion.convert(dataSources, environment);
            log.info("[Calcite] 聚合数据源创建成功，已聚合 {} 个数据源", dataSources.size());
            return calciteDataSource;
        } catch (Exception e) {
            if (replaceMode) {
                throw new IllegalStateException("[Calcite] register-mode=replace 时创建聚合数据源失败", e);
            }
            log.error("[Calcite] 创建聚合数据源失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 判断聚合数据源注册模式是否为 replace（替换主数据源）。
     *
     * <p>非法取值由 {@link #resolveRegisterMode(String)} 统一抛异常；
     * BFPP 阶段先于本方法执行，正常启动流程下配置已被校验。</p>
     *
     * @param properties Calcite 数据源配置
     * @return true = replace 模式
     */
    private static boolean isReplaceMode(CalciteDataSourceProperties properties) {
        return "replace".equals(resolveRegisterMode(properties.getRegisterMode()));
    }

    /**
     * 收集参与聚合的数据源。
     *
     * <p>未配置 {@code data-sources} 时聚合全部已注册数据源（过滤动态路由数据源与
     * 聚合数据源自身）；配置后只聚合名单内的数据源，名单顺序不改变 schema 编号
     * （schema 顺序仍按注册表迭代序），名单中未注册的名称告警跳过。</p>
     *
     * @param properties Calcite 数据源配置
     * @return collect数据源的结果
     */
    private List<DataSource> collectDataSources(CalciteDataSourceProperties properties) {
        Map<String, DataSource> allDataSources = DataSourceContextSupport.getAllDataSources();
        List<DataSource> result = new ArrayList<>();
        String customBeanName = properties.getBeanName() == null ? null : properties.getBeanName().trim();

        List<String> includeNames = properties.getDataSources();
        if (CollectionUtils.isEmpty(includeNames)) {
            for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
                String name = entry.getKey();
                DataSource ds = entry.getValue();

                // 跳过聚合数据源自身（规范名与配置的自定义名）
                if (CANONICAL_BEAN_NAME.equals(name) || name.equals(customBeanName)) {
                    continue;
                }

                // 跳过动态路由数据源
                if (ds instanceof AbstractRoutingDataSource) {
                    continue;
                }

                result.add(ds);
            }
            return result;
        }

        Set<String> wanted = new LinkedHashSet<>();
        for (String item : includeNames) {
            if (item != null && !item.isBlank()) {
                wanted.add(item.trim());
            }
        }
        if (wanted.isEmpty()) {
            log.warn("[Calcite] data-sources 配置为空名单，跳过聚合");
            return result;
        }
        for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
            String name = entry.getKey();
            DataSource ds = entry.getValue();
            if (!wanted.contains(name)) {
                continue;
            }
            if (CANONICAL_BEAN_NAME.equals(name) || name.equals(customBeanName)) {
                log.warn("[Calcite] data-sources 包含聚合数据源自身: {}，已跳过", name);
                continue;
            }
            if (ds instanceof AbstractRoutingDataSource) {
                log.warn("[Calcite] data-sources 包含动态路由数据源: {}，已跳过", name);
                continue;
            }
            result.add(ds);
        }
        for (String name : wanted) {
            if (!allDataSources.containsKey(name)) {
                log.warn("[Calcite] data-sources 中的数据源未注册: {}", name);
            }
        }
        if (result.isEmpty()) {
            log.warn("[Calcite] data-sources 名单均未命中已注册数据源，跳过聚合");
        }
        return result;
    }
}

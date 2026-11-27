package com.chua.starter.datasource.configuration;

import com.chua.datasource.support.engine.DataSourceConversion;
import com.chua.datasource.support.engine.DataSourceEnvironment;
import com.chua.shardingv5.support.conversion.ShardingV5Conversion;
import com.chua.starter.datasource.properties.ShardingDataSourceProperties;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 分库分表数据源注册器。
 *
 * <p>以 {@link BeanFactoryPostProcessor} 形式在 Bean 定义加载阶段，把
 * {@code plugin.datasource.sharding} 列表中的每个分片配置注册为独立的
 * {@link DataSource} Bean：{@code name} 指定 Bean 名（缺省
 * {@code shardingDataSource}），与已有 Bean 同名时告警跳过、保留已有 Bean，
 * {@code enabled=false} 的配置项不注册；兼容旧的单对象写法（等价单元素列表）。</p>
 *
 * <p>之所以用 BFPP 而不是 {@code @Bean}：列表长度与 Bean 名要等启动期绑定配置
 * 才能确定，无法用固定 {@code @Bean} 方法声明；且 {@code @Bean} 声明的 BFPP
 * 运行在所有 {@code BeanDefinitionRegistryPostProcessor} 之后，业务数据源此时
 * 已登记进 {@link DataSourceContextSupport}，{@code containsBeanDefinition}
 * 的冲突检查也能覆盖全部已注册 Bean 定义。</p>
 *
 * <p>配置读取刻意走 {@link Binder} + {@link Environment}，而不是注入
 * {@code ShardingDataSourceProperties}：BFPP 阶段早于
 * {@code ConfigurationPropertiesBindingPostProcessor}，过早实例化 properties
 * Bean 会拿到未绑定 yaml 的默认值。</p>
 *
 * @since 2026/11/25
 * @author CH
 */
@Slf4j
public class ShardingDataSourceRegistrar implements BeanFactoryPostProcessor {

    /**
     * 分片数据源缺省 Bean 名（与运行期 ShardingRuleManager.BEAN_NAME 同名同义）
     */
    public static final String DEFAULT_SHARDING_BEAN_NAME = "shardingDataSource";

    /**
     * 分片配置前缀
     */
    private static final String CONFIG_PREFIX = "plugin.datasource.sharding";

    /**
     * 环境配置（BFPP 阶段直接读取/绑定 yaml 配置）
     */
    private final Environment environment;

    /**
     * 构造分片数据源注册器
     *
     * @param environment 环境配置
     */
    public ShardingDataSourceRegistrar(Environment environment) {
        this.environment = environment;
    }

    /**
     * 把 {@code plugin.datasource.sharding} 列表逐项注册为分片数据源 Bean 定义。
     *
     * @param beanFactory Bean 工厂
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            log.warn("[Sharding] Bean 工厂不支持 BeanDefinitionRegistry，跳过分片数据源注册");
            return;
        }
        List<ShardingDataSourceProperties> items = bindShardingItems();
        for (ShardingDataSourceProperties properties : items) {
            String beanName = resolveBeanName(properties);
            if (!properties.isEnabled()) {
                log.info("[Sharding] 分片配置 [{}] 未启用（enabled=false），跳过注册", beanName);
                continue;
            }
            if (registry.containsBeanDefinition(beanName)) {
                log.warn("[Sharding] 数据源 Bean [{}] 已存在，跳过分片数据源注册（同名冲突，保留已有 Bean）", beanName);
                continue;
            }
            registry.registerBeanDefinition(beanName, shardingBeanDefinition(beanFactory, beanName, properties));
            DataSourceContextSupport.registerShardingBeanName(beanName);
            log.info("[Sharding] 注册分片数据源 Bean: {}", beanName);
        }
    }

    /**
     * 绑定分片配置：优先按列表读取（{@code plugin.datasource.sharding[n]}），
     * 未写成列表时回退旧的单对象写法并包装为单元素列表。
     *
     * @return 分片配置列表（未配置时为空列表）
     */
    private List<ShardingDataSourceProperties> bindShardingItems() {
        List<ShardingDataSourceProperties> list = null;
        try {
            list = Binder.get(environment)
                    .bind(CONFIG_PREFIX, Bindable.listOf(ShardingDataSourceProperties.class))
                    .orElse(null);
        } catch (Exception e) {
            log.debug("[Sharding] 按列表绑定 {} 失败，尝试旧单对象写法: {}", CONFIG_PREFIX, e.getMessage());
        }
        if (CollectionUtils.isNotEmpty(list)) {
            return list;
        }
        try {
            ShardingDataSourceProperties legacy = Binder.get(environment)
                    .bind(CONFIG_PREFIX, Bindable.of(ShardingDataSourceProperties.class))
                    .orElse(null);
            if (legacy != null) {
                return Collections.singletonList(legacy);
            }
        } catch (Exception e) {
            log.error("[Sharding] 绑定 {} 配置失败: {}", CONFIG_PREFIX, e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    /**
     * 解析分片数据源 Bean 名（空白回退缺省名）
     *
     * @param properties 分片配置
     * @return Bean 名
     */
    private String resolveBeanName(ShardingDataSourceProperties properties) {
        String name = properties.getName();
        if (name == null || name.isBlank()) {
            return DEFAULT_SHARDING_BEAN_NAME;
        }
        return name.trim();
    }

    /**
     * 构建分片数据源 Bean 定义（实例供给器延迟到实例化阶段创建真实数据源）。
     *
     * @param beanFactory Bean 工厂
     * @param beanName    Bean 名
     * @param properties  分片配置
     * @return Bean 定义
     */
    private BeanDefinition shardingBeanDefinition(ConfigurableListableBeanFactory beanFactory,
                                                  String beanName,
                                                  ShardingDataSourceProperties properties) {
        return new RootBeanDefinition(DataSource.class,
                () -> createShardingDataSource(beanFactory, beanName, properties));
    }

    /**
     * 创建分片数据源（实例供给器回调，语义与原 {@code @Bean} 方法一致）。
     *
     * @param beanFactory Bean 工厂
     * @param beanName    Bean 名
     * @param properties  分片配置
     * @return 分片数据源；未注册数据源或创建失败返回 {@code null}（Bean 记为 NullBean）
     */
    private DataSource createShardingDataSource(ConfigurableListableBeanFactory beanFactory,
                                                String beanName,
                                                ShardingDataSourceProperties properties) {
        List<DataSource> dataSources = ShardingAutoConfiguration.collectDataSources();
        if (dataSources.isEmpty()) {
            log.warn("[Sharding] [{}] 没有找到已注册的数据源，跳过分片配置", beanName);
            return null;
        }
        try {
            DataSourceConversion conversion = resolveConversion(beanFactory);
            ShardingV5Conversion configured = ShardingAutoConfiguration.configureSharding(conversion, properties);
            DataSourceEnvironment environment = new DataSourceEnvironment();
            environment.setDatabaseType("ShardingSphere");
            DataSource shardingDataSource = configured.convert(dataSources, environment);
            log.info("[Sharding] [{}] 分片数据源创建成功，已配置 {} 个数据源", beanName, dataSources.size());
            return shardingDataSource;
        } catch (Exception e) {
            log.error("[Sharding] [{}] 创建分片数据源失败: {}", beanName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析分库分表转换器：优先取容器中的 {@code shardingV5Conversion} Bean，
     * 失败时兜底新建默认转换器。
     *
     * @param beanFactory Bean 工厂
     * @return 分库分表转换器
     */
    private DataSourceConversion resolveConversion(ConfigurableListableBeanFactory beanFactory) {
        try {
            return beanFactory.getBean("shardingV5Conversion", DataSourceConversion.class);
        } catch (Exception e) {
            log.warn("[Sharding] 获取 shardingV5Conversion Bean 失败，使用默认转换器: {}", e.getMessage());
            return new ShardingV5Conversion();
        }
    }
}

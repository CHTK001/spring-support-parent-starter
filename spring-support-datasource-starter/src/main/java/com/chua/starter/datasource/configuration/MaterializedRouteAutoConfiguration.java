package com.chua.starter.datasource.configuration;

import com.chua.common.support.data.materialized.MaterializedSqlDataSourceRouter;
import com.chua.datasource.support.materialized.CalciteMaterializedSqlRouter;
import com.chua.starter.datasource.properties.MaterializedRouteProperties;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import com.chua.starter.datasource.support.MaterializedRefreshAdvisor;
import com.chua.starter.datasource.support.MaterializedRouteAdvisor;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.context.annotation.Conditional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * SQL 物理化自动配置。
 *
 * @since 2026/4/2
 * @author CH
 */
@Conditional(MaterializedRouteAutoConfiguration.MaterializedRouteEnabledCondition.class)
public class MaterializedRouteAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    /**
     * 物化route属性
     *
     * @param environment 环境
     * @return 物化route属性的结果
     */
    public MaterializedRouteProperties materializedRouteProperties(Environment environment) {
        return MaterializedRouteProperties.bind(environment);
    }

    @Bean
    @ConditionalOnMissingBean(name = "defaultAdvisorAutoProxyCreator")
    /**
     * 默认advisorauto代理creator
     *
     * @return 默认advisorauto代理creator的结果
     */
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true);
        return creator;
    }

    @Bean
    @ConditionalOnMissingBean
    /**
     * 物化routeadvisor
     *
     * @param properties 属性
     * @return 物化routeadvisor的结果
     */
    public MaterializedRouteAdvisor materializedRouteAdvisor(MaterializedRouteProperties properties) {
        /**
         * 物化routeadvisor。
         * @param properties 属性
         * @return 物化routeadvisor的结果
         */
        return new MaterializedRouteAdvisor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    /**
     * 物化refreshadvisor
     *
     * @param properties 属性
     * @return 物化refreshadvisor的结果
     */
    public MaterializedRefreshAdvisor materializedRefreshAdvisor(MaterializedRouteProperties properties) {
        /**
         * 物化refreshadvisor。
         * @param properties 属性
         * @return 物化refreshadvisor的结果
         */
        return new MaterializedRefreshAdvisor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    /**
     * 物化sqlrouter
     *
     * @param properties 属性
     * @param dataSource 数据源
     * @return 物化sqlrouter的结果
     */
    public MaterializedSqlDataSourceRouter materializedSqlRouter(MaterializedRouteProperties properties, DataSource dataSource) {
        return new CalciteMaterializedSqlRouter(
                properties.toOptions(),
                dataSource,
                name -> {
                    DataSource target = DataSourceContextSupport.getDatasource(name);
                    return target != null ? target : dataSource;
                });
    }

    static final class MaterializedRouteEnabledCondition implements Condition {
        @Override
        /**
         * Matches
         * @param context 参数
         * @param metadata 参数
         * @return 返回值
        */
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return MaterializedRouteProperties.bind(context.getEnvironment()).isEnabled();
        }
    }
}

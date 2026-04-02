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
 * @author CH
 * @since 2026/4/2
 */
@Conditional(MaterializedRouteAutoConfiguration.MaterializedRouteEnabledCondition.class)
public class MaterializedRouteAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MaterializedRouteProperties materializedRouteProperties(Environment environment) {
        return MaterializedRouteProperties.bind(environment);
    }

    @Bean
    @ConditionalOnMissingBean(name = "defaultAdvisorAutoProxyCreator")
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true);
        return creator;
    }

    @Bean
    @ConditionalOnMissingBean
    public MaterializedRouteAdvisor materializedRouteAdvisor(MaterializedRouteProperties properties) {
        return new MaterializedRouteAdvisor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MaterializedRefreshAdvisor materializedRefreshAdvisor(MaterializedRouteProperties properties) {
        return new MaterializedRefreshAdvisor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
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
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return MaterializedRouteProperties.bind(context.getEnvironment()).isEnabled();
        }
    }
}

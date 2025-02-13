package com.chua.starter.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.mybatis.endpoint.MybatisEndpoint;
import com.chua.starter.mybatis.interceptor.MybatisPlusPermissionHandler;
import com.chua.starter.mybatis.interceptor.MybatisPlusPermissionInterceptor;
import com.chua.starter.mybatis.method.SupportInjector;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import com.chua.starter.mybatis.properties.MybatisPlusProperties;
import com.chua.starter.mybatis.reloader.MapperReload;
import com.chua.starter.mybatis.reloader.Reload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 处理注解
 *
 * @author CH
 * @version 1.0.0
 * @since 2021/4/12
 */
@Slf4j
@AutoConfigureAfter(SqlSessionFactory.class)
@EnableConfigurationProperties({MybatisPlusProperties.class, MybatisPlusDataScopeProperties.class})
//@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class MybatisPlusConfiguration {

    final  MybatisPlusProperties mybatisProperties;

    /**
     * 分页
     *
     * @return OptimisticLockerInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public PaginationInnerInterceptor paginationInnerInterceptor() {
        return new PaginationInnerInterceptor();
    }



    /**
     * 数据权限
     *
     * @return OptimisticLockerInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusPermissionInterceptor dataPermissionInterceptor(
            @Autowired(required = false) DataPermissionHandler dataPermissionHandler,
            @Autowired(required = false) AuthService authService,
            MybatisPlusDataScopeProperties methodSecurityInterceptor
            ) {
        if(null == dataPermissionHandler) {
            dataPermissionHandler = new MybatisPlusPermissionHandler(authService, methodSecurityInterceptor);
        }
        return new MybatisPlusPermissionInterceptor(dataPermissionHandler, methodSecurityInterceptor);
    }


    /**
     * 乐观锁
     *
     * @return OptimisticLockerInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor() {
        return new OptimisticLockerInnerInterceptor();
    }
    /**
     * MybatisPlusInterceptor
     *
     * @return MybatisPlusInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Autowired(required = false) TenantLineInnerInterceptor tenantLineInnerInterceptor,
            OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor,
            PaginationInnerInterceptor paginationInnerInterceptor,
            MybatisPlusPermissionInterceptor dataPermissionInterceptor
            ) {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(optimisticLockerInnerInterceptor);
        mybatisPlusInterceptor.addInnerInterceptor(paginationInnerInterceptor);
        mybatisPlusInterceptor.addInnerInterceptor(dataPermissionInterceptor);
        if(null != tenantLineInnerInterceptor) {
            mybatisPlusInterceptor.addInnerInterceptor(tenantLineInnerInterceptor);
        }
        return mybatisPlusInterceptor;
    }


    /**
     * SqlInterceptor
     *
     * @return SqlInterceptor
     */
    @Bean("mapper-reload")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = MybatisPlusProperties.PRE, name = "open-xml-reload", havingValue = "true", matchIfMissing = true)
    public Reload xmlReload(List<SqlSessionFactory> sqlSessionFactory, MybatisPlusProperties mybatisProperties) {
        return new MapperReload(sqlSessionFactory, mybatisProperties);
    }
    /**
     * SupportInjector
     *
     * @return SupportInjector
     */
    @Bean
    @ConditionalOnMissingBean(name = {"supportInjector"})
    public SupportInjector supportInjector(MybatisPlusProperties mybatisProperties) {
        return new SupportInjector(mybatisProperties);
    }
    /**
     * MybatisEndpoint
     *
     * @return MybatisEndpoint MybatisEndpoint
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = {"mapper-reload", "supportInjector"})
    public MybatisEndpoint mybatisEndpoint(Reload reload, SqlSessionFactory sqlSessionFactory, @Autowired(required = false) SupportInjector supportInjector ) {
        return new MybatisEndpoint(reload, sqlSessionFactory.getConfiguration(), supportInjector);
    }


}

package com.chua.starter.mybatis;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.mybatis.endpoint.MybatisEndpoint;
import com.chua.starter.mybatis.interceptor.*;
import com.chua.starter.mybatis.method.SupportInjector;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import com.chua.starter.mybatis.properties.MybatisPlusProperties;
import com.chua.starter.mybatis.reloader.MapperReload;
import com.chua.starter.mybatis.reloader.Reload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
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
@ConditionalOnProperty(prefix = "plugin.mybatis", name = "enable", havingValue = "true", matchIfMissing = false)
//@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE + 10)
public class MybatisPlusConfiguration {

    /**
     * 逻辑删除字段名
     */
    private static final String LOGIC_DELETE_FIELD = "sys_deleted";

    /**
     * 逻辑删除值（已删除）
     */
    private static final String LOGIC_DELETE_VALUE = "1";

    /**
     * 逻辑未删除值（未删除）
     */
    private static final String LOGIC_NOT_DELETE_VALUE = "0";

    private final MybatisPlusProperties mybatisProperties;
    private final MybatisPlusDataScopeProperties methodSecurityInterceptor;

    /**
     * 构造函数
     *
     * @param mybatisProperties MyBatis Plus配置属性
     * @param methodSecurityInterceptor 数据权限配置属性
     */
    public MybatisPlusConfiguration(MybatisPlusProperties mybatisProperties, MybatisPlusDataScopeProperties methodSecurityInterceptor) {
        this.mybatisProperties = mybatisProperties;
        this.methodSecurityInterceptor = methodSecurityInterceptor;
    }

    static {
        JsqlParserGlobal.setParserMultiFunc((sql) -> {
            String formatSql = CCJSqlParserUtil.sanitizeSingleSql(sql);
            return CCJSqlParserUtil.parseStatements(formatSql, JsqlParserGlobal.getExecutorService(), null);
        });
        JsqlParserGlobal.setParserSingleFunc((sql) -> {
            String formatSql = CCJSqlParserUtil.sanitizeSingleSql(sql);
            return CCJSqlParserUtil.parse(formatSql, JsqlParserGlobal.getExecutorService(), null);
        });
    }
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
     * 配置MyBatis Plus全局配置
     *
     * @return GlobalConfig 全局配置对象
     */
    @Bean
    public GlobalConfig globalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        // 设置逻辑删除的全局值
        dbConfig.setLogicDeleteField(LOGIC_DELETE_FIELD);
        dbConfig.setLogicDeleteValue(LOGIC_DELETE_VALUE);
        dbConfig.setLogicNotDeleteValue(LOGIC_NOT_DELETE_VALUE);
        globalConfig.setDbConfig(dbConfig);
        return globalConfig;
    }


    /**
     * 删除权限
     *
     * @return OptimisticLockerInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public DeleteOnlySelfInterceptor deleteOnlySelfInterceptor(AuthService authService) {
        return new DeleteOnlySelfInterceptor(authService);
    }
    /**
     * 数据权限
     *
     * @return OptimisticLockerInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public DataPermissionInterceptor dataPermissionInterceptor(
            @Autowired(required = false) MybatisPlusDataPermissionHandler dataPermissionHandler,
            MybatisPlusDataScopeProperties methodSecurityInterceptor
            ) {
        if(null == dataPermissionHandler) {
            dataPermissionHandler = new MybatisPlusDataPermissionHandler(methodSecurityInterceptor);
        }
        return new MybatisPlusDataPermissionInterceptor(dataPermissionHandler, methodSecurityInterceptor);
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
     * 加密
     *
     * @return OptimisticLockerInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public CryptoInterceptor cryptoInterceptor() {
        return new CryptoInterceptor();
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
            DataPermissionInterceptor dataPermissionInterceptor,
            PaginationInnerInterceptor paginationInnerInterceptor
            ) {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(optimisticLockerInnerInterceptor);
        mybatisPlusInterceptor.addInnerInterceptor(dataPermissionInterceptor);
        mybatisPlusInterceptor.addInnerInterceptor(paginationInnerInterceptor);

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
    @ConditionalOnProperty(prefix = MybatisPlusProperties.PRE, name = "open-xml-reload", havingValue = "true", matchIfMissing = false)
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

    @Bean
    @ConditionalOnMissingBean
    public ConfigurationCustomizer mybatisConfigurationCustomizer(MybatisPlusProperties mybatisProperties) {
        return configuration -> {
            // 只读拦截器优先级最高，需要第一个添加
            if (mybatisProperties.isReadOnly()) {
                log.warn("数据库只读模式已开启，所有写操作将被拒绝");
                configuration.addInterceptor(new ReadOnlyInterceptor(mybatisProperties));
            }
            // 添加追踪拦截器
            configuration.addInterceptor(new MapperTracingInterceptor());
        };
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

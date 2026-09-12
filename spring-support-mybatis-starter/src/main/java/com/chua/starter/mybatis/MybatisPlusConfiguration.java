package com.chua.starter.mybatis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import com.chua.common.support.data.materialized.MaterializedSqlDataSourceRouter;
import com.chua.starter.datasource.properties.MultiDataSourceSettingProperties;
import com.chua.starter.datasource.properties.MaterializedRouteProperties;
import com.chua.starter.mybatis.endpoint.MybatisEndpoint;
import com.chua.starter.mybatis.interceptor.*;
import com.chua.starter.mybatis.method.SupportInjector;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import com.chua.starter.mybatis.properties.MybatisPlusProperties;
import com.chua.starter.mybatis.properties.MybatisSlowSqlProperties;
import com.chua.starter.mybatis.reloader.MapperReload;
import com.chua.starter.mybatis.reloader.Reload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
* 处理注解
*
* @版本 1.0.0
* @since 2021/4/12
* @author CH
 */
@EnableConfigurationProperties({MybatisPlusProperties.class, MybatisPlusDataScopeProperties.class, MybatisSlowSqlProperties.class})
public class MybatisPlusConfiguration {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(MybatisPlusConfiguration.class);
    /** Encryptor_密码_财产 */
    private static final String ENCRYPTOR_PASSWORD_PROPERTY = "mybatis-plus.encryptor.password";
    /** Encryptor_mode_财产 */
    private static final String ENCRYPTOR_MODE_PROPERTY = "mybatis-plus.encryptor.mode";

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

    /** MyBatis属性 */
    private final MybatisPlusProperties mybatisProperties;
    /** Methodsecurityinterceptor */
    private final MybatisPlusDataScopeProperties methodSecurityInterceptor;
    /** 环境 */
    private final Environment environment;

    /**
    * 构造函数
    *
    * @param mybatisProperties MyBatis Plus配置属性
    * @param methodSecurityInterceptor 数据权限配置属性
     */
    public MybatisPlusConfiguration(MybatisPlusProperties mybatisProperties,
                                    MybatisPlusDataScopeProperties methodSecurityInterceptor,
                                    Environment environment) {
        this.mybatisProperties = mybatisProperties;
        this.methodSecurityInterceptor = methodSecurityInterceptor;
        this.environment = environment;
        /**
        * 同步encryptor属性。
         */
        syncEncryptorProperties();
    }

    static {
 // 不使用 sanitize单个SQL，避免 jsqlparser 将 AS 群体 等关键字别名解析失败
        JsqlParserGlobal.setParserMultiFunc((sql) -> {
            return CCJSqlParserUtil.parseStatements(sql, JsqlParserGlobal.getExecutorService(), null);
        });
        JsqlParserGlobal.setParserSingleFunc((sql) -> {
            return CCJSqlParserUtil.parse(sql, JsqlParserGlobal.getExecutorService(), null);
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
        /**
        * pagination内部拦截器。
        * @return pagination内部拦截器的结果
         */
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
    * @param authService 认证服务
     */
    @Bean
    @ConditionalOnMissingBean
    public DeleteOnlySelfInterceptor deleteOnlySelfInterceptor(AuthService authService) {
        /**
        * 删除onlyself拦截器。
        * @param authService 认证服务
        * @return 删除onlyself拦截器的结果
         */
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
            MybatisPlusDataScopeProperties methodSecurityInterceptor,
            @Qualifier("authService") ObjectProvider<AuthService> authServiceProvider,
            @Qualifier("defaultAuthService") ObjectProvider<AuthService> defaultAuthServiceProvider
            ) {
        Supplier<AuthService> authServiceSupplier = () -> {
            AuthService authService = authServiceProvider.getIfAvailable();
            return null != authService ? authService : defaultAuthServiceProvider.getIfAvailable();
        };
        if(null == dataPermissionHandler) {
            dataPermissionHandler = new MybatisPlusDataPermissionHandler(methodSecurityInterceptor, authServiceSupplier);
        }
        /**
        * mybatisplus数据权限拦截器。
        * @param dataPermissionHandler 数据权限处理器
        * @param methodSecurityInterceptor 方法安全性拦截器
        * @param authServiceSupplier 认证服务供应商
        * @return mybatisplus数据权限拦截器的结果
         */
        return new MybatisPlusDataPermissionInterceptor(dataPermissionHandler, methodSecurityInterceptor, authServiceSupplier);
    }


    /**
    * 乐观锁
    *
    * @return OptimisticLockerInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor() {
        /**
        * optimistic锁内部拦截器。
        * @return optimistic锁内部拦截器的结果
         */
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
        /**
        * 加密货币拦截器。
        * @return 加密货币拦截器的结果
         */
        return new CryptoInterceptor();
    }

    /**
    * mybatisplus拦截器
    *
    * @return MybatisPlusInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            ObjectProvider<List<InnerInterceptor>> innerInterceptorProvider) {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        List<InnerInterceptor> innerInterceptors = new ArrayList<>(innerInterceptorProvider.getIfAvailable(List::of));
        innerInterceptors.sort((left, right) -> {
            boolean leftPagination = left instanceof PaginationInnerInterceptor;
            boolean rightPagination = right instanceof PaginationInnerInterceptor;
            if (leftPagination == rightPagination) {
                return 0;
            }
            return leftPagination ? 1 : -1;
        });
        for (InnerInterceptor innerInterceptor : innerInterceptors) {
            mybatisPlusInterceptor.addInnerInterceptor(innerInterceptor);
        }
        return mybatisPlusInterceptor;
    }


    /**
    * SQL拦截器
    *
    * @return SqlInterceptor
    * @param sqlSessionFactory SQL会话工厂
    * @param mybatisProperties MyBatis属性
     */
    @Bean("mapper-reload")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = MybatisPlusProperties.PRE, name = "open-xml-reload", havingValue = "true", matchIfMissing = false)
    public Reload xmlReload(List<SqlSessionFactory> sqlSessionFactory, MybatisPlusProperties mybatisProperties) {
        /**
        * 映射器reload。
        * @param sqlSessionFactory SQL会话工厂
        * @param mybatisProperties MyBatis属性
        * @return 映射器reload的结果
         */
        return new MapperReload(sqlSessionFactory, mybatisProperties);
    }
    /**
    * 支持injector
    *
    * @return SupportInjector
    * @param mybatisProperties MyBatis属性
     */
    @Bean
    @ConditionalOnMissingBean(name = {"supportInjector"})
    public SupportInjector supportInjector(MybatisPlusProperties mybatisProperties) {
        /**
        * 支持injector。
        * @param mybatisProperties MyBatis属性
        * @return 支持injector的结果
         */
        return new SupportInjector(mybatisProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    /**
    * MyBatis配置customizer
    * @param mybatisProperties MyBatis属性
    * @param materializedSqlRouter 物化sqlrouter
    * @param materializedRouteProperties 物化route属性
    * @param multiDataSourceSettingProperties multi数据源setting属性
    * @param applicationContext application上下文
    * @param slowSqlDetectInterceptorProvider slowsqldetect拦截器提供者
     */
    public ConfigurationCustomizer mybatisConfigurationCustomizer(MybatisPlusProperties mybatisProperties,
                                                                 @Autowired(required = false) MaterializedSqlDataSourceRouter materializedSqlRouter,
                                                                 @Autowired(required = false) MaterializedRouteProperties materializedRouteProperties,
                                                                 MultiDataSourceSettingProperties multiDataSourceSettingProperties,
                                                                 ApplicationContext applicationContext,
                                                                 ObjectProvider<SlowSqlDetectInterceptor> slowSqlDetectInterceptorProvider) {
        return configuration -> {
            // 只读拦截器优先级最高，需要第一个添加
            if (mybatisProperties.isReadOnly()) {
                configuration.addInterceptor(new ReadOnlyInterceptor(mybatisProperties));
            }
            if (materializedSqlRouter != null && materializedRouteProperties != null && materializedRouteProperties.isEnabled()) {
                configuration.addInterceptor(new MaterializedSqlInterceptor(materializedSqlRouter, materializedRouteProperties));
            }
            configuration.addInterceptor(new MapperDataSourceInterceptor(applicationContext, multiDataSourceSettingProperties));
            // 添加追踪拦截器
            configuration.addInterceptor(new MapperTracingInterceptor());
            SlowSqlDetectInterceptor slowSqlDetectInterceptor = slowSqlDetectInterceptorProvider.getIfAvailable();
            if (slowSqlDetectInterceptor != null) {
                configuration.addInterceptor(slowSqlDetectInterceptor);
            }
        };
    }

    /**
    * 慢 SQL 检测拦截器（可选）。
    *
    * @param eventPublisher Spring 事件发布器
    * @param properties     慢 SQL 配置
    * @return 慢 SQL 检测拦截器
     */
    @Bean
    @ConditionalOnProperty(prefix = MybatisPlusProperties.PRE + ".slow-sql", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SlowSqlDetectInterceptor slowSqlDetectInterceptor(org.springframework.context.ApplicationEventPublisher eventPublisher,
                                                            MybatisSlowSqlProperties properties) {
        /**
        * slowsqldetect拦截器。
        * @param eventPublisher 事件发布
        * @param properties 属性
        * @return slowsqldetect拦截器的结果
         */
        return new SlowSqlDetectInterceptor(eventPublisher, properties);
    }
    /**
    * MyBatis端点
    *
    * @return MybatisEndpoint MyBatis端点
    * @param reload reload
    * @param sqlSessionFactory SQL会话工厂
    * @param false false
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = {"mapper-reload", "supportInjector"})
    public MybatisEndpoint mybatisEndpoint(Reload reload, SqlSessionFactory sqlSessionFactory, @Autowired(required = false) SupportInjector supportInjector ) {
        /**
        * MyBatis端点。
        * @param reload reload
        * @param sqlSessionFactory.getConfiguration( SQL会话工厂.getconfiguration(
        * @return mybatis端点的结果
         */
        return new MybatisEndpoint(reload, sqlSessionFactory.getConfiguration(), supportInjector);
    }


    /**
    * 创建 {@link SqlSessionFactory}。
    * <p>基于动态数据源和 MyBatis Plus 全局配置、拦截器进行构建。</p>
    *
    * @param dataSource               主数据源
    * @param mybatisPlusInterceptor   MyBatis Plus 拦截器
    * @param globalConfig             MyBatis Plus 全局配置
    * @return SqlSessionFactory 实例
    * @throws Exception 创建失败时抛出异常
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                               MybatisPlusInterceptor mybatisPlusInterceptor,
                                               GlobalConfig globalConfig,
                                               ObjectProvider<MetaObjectHandler> metaObjectHandlerProvider) throws Exception {
        var factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPlugins(mybatisPlusInterceptor);
 // 关联容器中的 meta对象处理器，实现公共字段自动填充（如 sysbase 的 创建_时间/更新_时间）
        MetaObjectHandler metaObjectHandler = metaObjectHandlerProvider.getIfAvailable();
        if (null != metaObjectHandler) {
            globalConfig.setMetaObjectHandler(metaObjectHandler);
        }
        factoryBean.setGlobalConfig(globalConfig);
        var sqlSessionFactory = factoryBean.getObject();
        log.info("[OAuth][MyBatis]SqlSessionFactory 已创建");
        return sqlSessionFactory;
    }

    /**
    * 创建 {@link SqlSessionTemplate}。
    * <p>用于给 Mapper 提供实际使用的 SqlSession。</p>
    *
    * @param sqlSessionFactory SQL会话工厂 实例
    * @return SqlSessionTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        /**
        * SQL会话template。
        * @param sqlSessionFactory SQL会话工厂
        * @return sql会话template的结果
         */
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /** 同步encryptor属性 */
    private void syncEncryptorProperties() {
        /**
        * 同步系统财产。
        * @param ENCRYPTOR_PASSWORD_PROPERTY encryptor密码财产
         */
        syncSystemProperty(ENCRYPTOR_PASSWORD_PROPERTY);
        /**
        * 同步系统财产。
        * @param ENCRYPTOR_MODE_PROPERTY encryptormode财产
         */
        syncSystemProperty(ENCRYPTOR_MODE_PROPERTY);
    }

    /**
    * 同步系统财产
    *
    * @param propertyName 财产名称
     */
    private void syncSystemProperty(String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return;
        }
        System.setProperty(propertyName, value);
    }

    @Bean
    /**
    * mybatisplus服务信息contributor
    *
    * @return mybatisplus服务信息contributor的结果
     */
    public ServiceInfoContributor mybatisPlusServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("mybatis", "MyBatis", true, "MyBatis Plus模块", null);
            registry.registerFlag("read-only", "MyBatis",
                    mybatisProperties.isReadOnly(),
                    "数据库只读模式 (拒绝写操作)", null);
            registry.registerFlag("data-scope", "MyBatis",
                    methodSecurityInterceptor.isEnable(),
                    "数据权限拦截", null);
        };
    }
}

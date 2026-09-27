package com.chua.starter.datasource.configuration;

import com.chua.starter.datasource.controller.DataSourceDocumentController;
import com.chua.starter.datasource.controller.DataSourceDocumentHandler;
import com.chua.starter.datasource.properties.DocumentProperties;
import com.chua.starter.datasource.support.DataSourceDocumentService;
import com.chua.starter.datasource.support.graph.DatabaseGraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;

/**
 * 数据源文档预览自动配置。
 * <p>
 * 所有 Bean 都是无条件注册（依赖检测在构造函数内做），
 * 避免 Spring Boot 的 {@code @ConditionalOnBean} 在同类配置中求值失败的问题。
 * 顶级 {@code @ConditionalOnClass} 保证核心类存在；{@code @ConditionalOnWebApplication}
 * 保证仅 Web 上下文生效。
 * </p>
 *
 * @since 2026/8/5
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = {
        "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping",
        "com.chua.common.support.lang.document.DocumentParser"
})
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = DocumentProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DocumentProperties.class)
/**
 * 文档auto配置类。
 *
 * @author CH
 * @since 4.0.0
 */
public class DocumentAutoConfiguration {

    /**
     * Spring MVC 的 RequestMappingHandlerMapping bean 名。
     *
     * <p>按类型查找会与 Actuator 的 controllerEndpointHandlerMapping 冲突，故按名取。</p>
     */
    private static final String HANDLER_MAPPING_BEAN = "requestMappingHandlerMapping";

    /**
     * 文档服务（解析 + 缓存）。
     * <p>若 classpath 缺 DocumentParser SPI 或 DataSource 不可用，返回 null</p>
     */
    @Bean
    public DataSourceDocumentService dataSourceDocumentService(
            ObjectProvider<DataSource> dataSourceProvider, DocumentProperties properties) {
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            log.debug("DataSource 不可用，跳过 DataSourceDocumentService");
            return null;
        }
        /**
         * 数据源文档服务。
         * @param ds ds
         * @param properties 属性
         * @return 数据源文档服务的结果
         */
        return new DataSourceDocumentService(ds, properties);
    }

    /**
     * 数据库关系图谱服务。
     */
    @Bean
    public DatabaseGraphService databaseGraphService(
            ObjectProvider<DataSource> dataSourceProvider, DocumentProperties properties) {
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            log.debug("DataSource 不可用，跳过 DatabaseGraphService");
            return null;
        }
        /**
         * database图计算服务。
         * @param ds ds
         * @param properties 属性
         * @return database图计算服务的结果
         */
        return new DatabaseGraphService(ds, properties);
    }

    /**
     * 文档控制器（图谱服务可选）。
     */
    @Bean
    public DataSourceDocumentController dataSourceDocumentController(
            ObjectProvider<DataSourceDocumentService> serviceProvider,
            DocumentProperties properties,
            ObjectProvider<DatabaseGraphService> graphServiceProvider) {
        DataSourceDocumentService service = serviceProvider.getIfAvailable();
        if (service == null) {
            log.debug("DataSourceDocumentService 不可用，跳过 Controller");
            return null;
        }
        DatabaseGraphService graphService = graphServiceProvider.getIfAvailable();
        /**
         * 数据源文档控制器。
         * @param service 服务
         * @param properties 属性
         * @param graphService 图计算服务
         * @return 数据源文档控制器的结果
         */
        return new DataSourceDocumentController(service, properties, graphService);
    }

    /**
     * 注册器（把 控制器 方法挂到 Spring MVC）。
     */
    @Bean
    public DataSourceDocumentHandler dataSourceDocumentHandler(
            ObjectProvider<DataSourceDocumentController> controllerProvider, DocumentProperties properties) {
        DataSourceDocumentController controller = controllerProvider.getIfAvailable();
        if (controller == null) {
            log.debug("DataSourceDocumentController 不可用，跳过 Handler");
            return null;
        }
        /**
         * 数据源文档处理器。
         * @param controller 控制器
         * @param properties.getBasePath( 属性.获取基础路径(
         * @return 数据源文档处理器的结果
         */
        return new DataSourceDocumentHandler(controller, properties.getBasePath());
    }

    /**
     * * 触发路径注册
     * @param context 上下文
     * @return 文档路径registrar的结果
     */
@Bean
public SmartInitializingSingleton documentPathRegistrar(ApplicationContext context) {
    return () -> {
        DataSourceDocumentHandler handler = context.getBeanProvider(DataSourceDocumentHandler.class).getIfAvailable();
        if (handler == null) {
            return;
        }
        try {
            // 必须按 bean 名取：上下文里同时存在 requestMappingHandlerMapping 与
            // Actuator 的 controllerEndpointHandlerMapping 两个同类型 Bean，
            // getBean(RequestMappingHandlerMapping.class) 会抛 NoUniqueBeanDefinitionException，
            // 导致数据库文档路径注册静默失败。
            if (!context.containsBean(HANDLER_MAPPING_BEAN)) {
                log.warn("未找到 {} Bean，跳过数据库文档路径注册", HANDLER_MAPPING_BEAN);
                return;
            }
            RequestMappingHandlerMapping mapping =
                    context.getBean(HANDLER_MAPPING_BEAN, RequestMappingHandlerMapping.class);
            handler.register(mapping);
        } catch (Exception e) {
            log.warn("注册数据库文档路径失败：{}", e.getMessage());
        }
    };
}
}

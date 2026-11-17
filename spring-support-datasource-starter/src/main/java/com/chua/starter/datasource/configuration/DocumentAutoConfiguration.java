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
     *
     * @param dataSourceProvider 数据源提供者（可空）
     * @param properties         文档属性
     * @return 数据源文档服务；DataSource 不可用时返回 null
     */
    @Bean
    public DataSourceDocumentService dataSourceDocumentService(
            ObjectProvider<DataSource> dataSourceProvider, DocumentProperties properties) {
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            log.debug("DataSource 不可用，跳过 DataSourceDocumentService");
            return null;
        }
        return new DataSourceDocumentService(ds, properties);
    }

    /**
     * 数据库关系图谱服务。
     *
     * @param dataSourceProvider 数据源提供者（可空）
     * @param properties         文档属性
     * @return 数据库关系图谱服务；DataSource 不可用时返回 null
     */
    @Bean
    public DatabaseGraphService databaseGraphService(
            ObjectProvider<DataSource> dataSourceProvider, DocumentProperties properties) {
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            log.debug("DataSource 不可用，跳过 DatabaseGraphService");
            return null;
        }
        return new DatabaseGraphService(ds, properties);
    }

    /**
     * 文档控制器（图谱服务可选）。
     *
     * @param serviceProvider      文档服务提供者（可空）
     * @param properties           文档属性
     * @param graphServiceProvider 图谱服务提供者（可空）
     * @return 文档控制器；服务不可用时返回 null
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
        return new DataSourceDocumentController(service, properties, graphService);
    }

    /**
     * 注册器（把 控制器 方法挂到 Spring MVC）。
     *
     * @param controllerProvider 文档控制器提供者（可空）
     * @param properties         文档属性
     * @return 注册器；控制器不可用时返回 null
     */
    @Bean
    public DataSourceDocumentHandler dataSourceDocumentHandler(
            ObjectProvider<DataSourceDocumentController> controllerProvider, DocumentProperties properties) {
        DataSourceDocumentController controller = controllerProvider.getIfAvailable();
        if (controller == null) {
            log.debug("DataSourceDocumentController 不可用，跳过 Handler");
            return null;
        }
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

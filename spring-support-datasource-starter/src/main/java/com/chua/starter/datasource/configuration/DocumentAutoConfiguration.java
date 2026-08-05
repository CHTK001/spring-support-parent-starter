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
 * @author CH
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
public class DocumentAutoConfiguration {

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
        return new DataSourceDocumentController(service, properties, graphService);
    }

    /**
     * 注册器（把 Controller 方法挂到 Spring MVC）。
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
 */
@Bean
public SmartInitializingSingleton documentPathRegistrar(ApplicationContext context) {
    return () -> {
        DataSourceDocumentHandler handler = context.getBeanProvider(DataSourceDocumentHandler.class).getIfAvailable();
        if (handler == null) {
            return;
        }
        try {
            RequestMappingHandlerMapping mapping = context.getBean(RequestMappingHandlerMapping.class);
            handler.register(mapping);
        } catch (Exception e) {
            log.warn("注册数据库文档路径失败：{}", e.getMessage());
        }
    };
}
}
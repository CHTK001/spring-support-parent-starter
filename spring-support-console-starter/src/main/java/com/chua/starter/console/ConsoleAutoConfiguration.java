package com.chua.starter.console;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.console.controller.ConsolePageController;
import com.chua.starter.console.scan.AnnotationSwitchScanner;
import com.chua.starter.console.store.ConsoleOverrideProvider;
import com.chua.starter.console.store.ConsoleSqliteStore;
import com.chua.starter.console.store.ConsoleStartupResetter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
* 管理控制台自动配置。
*
* <p>装配：SQLite 持久层（自包含 sqlite-jdbc）→ 注解扫描与指纹 → {@code ApiSwitchOverrideProvider}
* 实现（接入 通用 拦截器判定）→ 启动指纹比对重置。FTL 管理页面控制器见
* {@code console.controller} 包（随本配置一并注册）。</p>
*
* @author CH
* @since 2026/09/04
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = ConsoleProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ConsoleProperties.class)
@Import(ConsolePageController.class)
public class ConsoleAutoConfiguration {

    @Bean
    public AnnotationSwitchScanner annotationSwitchScanner(
            @org.springframework.context.annotation.Lazy RequestMappingHandlerMapping requestMappingHandlerMapping) {
        /**
        * 注解switchscanner。
        * @param requestMappingHandlerMapping 请求mapping处理器mapping
        * @return 注解switchscanner的结果
         */
        return new AnnotationSwitchScanner(requestMappingHandlerMapping);
    }

    @Bean(destroyMethod = "close")
    /**
    * 控制台sqlite存储。
    * @param properties 属性
    * @return 控制台sqlite存储的结果
     */
    public ConsoleSqliteStore consoleSqliteStore(ConsoleProperties properties) {
        /**
        * 控制台sqlite存储。
        * @param properties 属性
        * @return 控制台sqlite存储的结果
         */
        return new ConsoleSqliteStore(properties);
    }

    @Bean
    /**
    * 控制台override提供者。
    * @param consoleSqliteStore 控制台sqlite存储
    * @return 控制台override提供者的结果
     */
    public ConsoleOverrideProvider consoleOverrideProvider(ConsoleSqliteStore consoleSqliteStore) {
        /**
        * 控制台override提供者。
        * @param consoleSqliteStore 控制台sqlite存储
        * @return 控制台override提供者的结果
         */
        return new ConsoleOverrideProvider(consoleSqliteStore);
    }

    @Bean
    public ConsoleStartupResetter consoleStartupResetter(AnnotationSwitchScanner annotationSwitchScanner,
                                                         ConsoleSqliteStore consoleSqliteStore) {
        /**
        * 控制台startupresetter。
        * @param annotationSwitchScanner 注解switchscanner
        * @param consoleSqliteStore 控制台sqlite存储
        * @return 控制台startupresetter的结果
         */
        return new ConsoleStartupResetter(annotationSwitchScanner, consoleSqliteStore);
    }

    /**
    * 管理控制台的 服务信息 贡献者。
    *
    * @param properties 控制台配置
    * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor consoleServiceInfoContributor(ConsoleProperties properties) {
        return registry -> registry.registerFlag("console", "管理控制台", properties.isEnable(),
                "管理控制台 (运行时开关调整)", null);
    }
}

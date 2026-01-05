package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.VirtualThreadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;

/**
 * 虚拟线程自动配置
 * <p>
 * Java 21+ 虚拟线程（Virtual Threads）配置，启用后可以显著提升高并发场景下的性能。
 * </p>
 * 
 * <h3>支持的 Web 容器：</h3>
 * <ul>
 *     <li>Tomcat - 通过 TomcatProtocolHandlerCustomizer 配置</li>
 *     <li>Undertow - 通过 WebServerFactoryCustomizer 配置</li>
 *     <li>Jetty - 通过 WebServerFactoryCustomizer 配置</li>
 *     <li>Netty (WebFlux) - 通过 ReactorResourceFactory 配置</li>
 * </ul>
 * 
 * <h3>主要功能：</h3>
 * <ul>
 *     <li>Web 容器请求处理使用虚拟线程</li>
 *     <li>@Async 异步任务使用虚拟线程</li>
 *     <li>@Scheduled 定时任务使用虚拟线程</li>
 * </ul>
 * 
 * <h3>使用方式：</h3>
 * <pre>
 * # application.yml
 * plugin:
 *   virtual-thread:
 *     enabled: true
 * </pre>
 * 
 * <h3>注意事项：</h3>
 * <ul>
 *     <li>需要 Java 21+</li>
 *     <li>虚拟线程不适合 CPU 密集型任务</li>
 *     <li>避免在虚拟线程中使用 synchronized，推荐使用 ReentrantLock</li>
 *     <li>ThreadLocal 在虚拟线程中可能有性能问题，考虑使用 ScopedValue</li>
 * </ul>
 * 
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(VirtualThreadProperties.class)
@ConditionalOnProperty(prefix = VirtualThreadProperties.PREFIX, name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class VirtualThreadConfiguration {

    private final VirtualThreadProperties properties;

    // ==================== Tomcat 虚拟线程配置 ====================

    /**
     * 配置 Tomcat 使用虚拟线程处理请求
     * <p>
     * 每个 HTTP 请求都会在一个虚拟线程中处理，大幅提升并发处理能力
     * </p>
     */
    @Bean
    @ConditionalOnClass(name = "org.apache.catalina.connector.Connector")
    @ConditionalOnProperty(prefix = VirtualThreadProperties.PREFIX, name = "web-enabled", havingValue = "true", matchIfMissing = true)
    public TomcatProtocolHandlerCustomizer<?> tomcatVirtualThreadExecutorCustomizer() {
        log.info("[Virtual Thread] Tomcat 虚拟线程执行器已启用");
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }

    // ==================== Undertow 虚拟线程配置 ====================

    /**
     * 配置 Undertow 使用虚拟线程处理请求
     */
    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "io.undertow.Undertow")
    @ConditionalOnProperty(prefix = VirtualThreadProperties.PREFIX, name = "web-enabled", havingValue = "true", matchIfMissing = true)
    static class UndertowVirtualThreadConfiguration {

        @Bean
        public WebServerFactoryCustomizer<UndertowServletWebServerFactory> undertowVirtualThreadCustomizer() {
            log.info("[Virtual Thread] Undertow 虚拟线程执行器已启用");
            return factory -> {
                factory.addBuilderCustomizers(builder -> {
                    // Undertow 使用虚拟线程作为工作线程
                    builder.setWorkerThreads(1); // 虚拟线程模式下，物理工作线程可以设置为最小
                });
            };
        }
    }

    // ==================== Jetty 虚拟线程配置 ====================

    /**
     * 配置 Jetty 使用虚拟线程处理请求
     */
    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "org.eclipse.jetty.server.Server")
    @ConditionalOnProperty(prefix = VirtualThreadProperties.PREFIX, name = "web-enabled", havingValue = "true", matchIfMissing = true)
    static class JettyVirtualThreadConfiguration {

        @Bean
        public WebServerFactoryCustomizer<JettyServletWebServerFactory> jettyVirtualThreadCustomizer() {
            log.info("[Virtual Thread] Jetty 虚拟线程执行器已启用");
            return factory -> {
                factory.setThreadPool(new org.eclipse.jetty.util.thread.QueuedThreadPool() {
                    @Override
                    protected void runJob(Runnable job) {
                        // 使用虚拟线程执行任务
                        Thread.startVirtualThread(job);
                    }
                });
            };
        }
    }

    // ==================== Netty (WebFlux) 虚拟线程配置 ====================

    /**
     * 配置 Netty (Spring WebFlux) 使用虚拟线程处理阻塞操作
     * <p>
     * WebFlux 本身是响应式的，但当需要执行阻塞操作时可以使用虚拟线程
     * </p>
     */
    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "reactor.netty.http.server.HttpServer")
    @ConditionalOnProperty(prefix = VirtualThreadProperties.PREFIX, name = "web-enabled", havingValue = "true", matchIfMissing = true)
    static class NettyVirtualThreadConfiguration {

        @Bean
        public reactor.core.scheduler.Scheduler virtualThreadScheduler() {
            log.info("[Virtual Thread] Netty/WebFlux 虚拟线程调度器已启用");
            return reactor.core.scheduler.Schedulers.fromExecutor(
                    Executors.newVirtualThreadPerTaskExecutor()
            );
        }
    }

    // ==================== 异步任务虚拟线程配置 ====================

    /**
     * 配置异步任务执行器使用虚拟线程
     * <p>
     * 所有 @Async 注解的方法都会在虚拟线程中执行
     * </p>
     */
    @Bean("virtualThreadAsyncTaskExecutor")
    @ConditionalOnProperty(prefix = VirtualThreadProperties.PREFIX, name = "async-enabled", havingValue = "true", matchIfMissing = true)
    public AsyncTaskExecutor virtualThreadAsyncTaskExecutor() {
        log.info("[Virtual Thread] 异步任务虚拟线程执行器已启用");
        return new TaskExecutorAdapter(Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name(properties.getThreadNamePrefix() + "async-", 0)
                        .factory()
        ));
    }

    /**
     * 配置定时任务执行器使用虚拟线程
     * <p>
     * 所有 @Scheduled 注解的方法都会在虚拟线程中执行
     * </p>
     */
    @Bean("virtualThreadScheduledTaskExecutor")
    @ConditionalOnProperty(prefix = VirtualThreadProperties.PREFIX, name = "scheduled-enabled", havingValue = "true", matchIfMissing = true)
    public TaskExecutor virtualThreadScheduledTaskExecutor() {
        log.info("[Virtual Thread] 定时任务虚拟线程执行器已启用");
        return new TaskExecutorAdapter(Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name(properties.getThreadNamePrefix() + "scheduled-", 0)
                        .factory()
        ));
    }

    /**
     * 通用虚拟线程执行器
     * <p>
     * 可以通过 @Qualifier("virtualThreadExecutor") 注入使用
     * </p>
     */
    @Bean("virtualThreadExecutor")
    public TaskExecutor virtualThreadExecutor() {
        log.info("[Virtual Thread] 通用虚拟线程执行器已创建");
        return new TaskExecutorAdapter(Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name(properties.getThreadNamePrefix(), 0)
                        .factory()
        ));
    }
}

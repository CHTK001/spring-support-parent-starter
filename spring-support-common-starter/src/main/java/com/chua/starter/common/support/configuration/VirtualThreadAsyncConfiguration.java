package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.VirtualThreadProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 虚拟线程异步配置
 * <p>
 * 当启用虚拟线程时，所有 @Async 注解的方法默认使用虚拟线程执行
 * </p>
 * 
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(VirtualThreadProperties.class)
@ConditionalOnProperty(prefix = VirtualThreadProperties.PREFIX, name = "enabled", havingValue = "true")
public class VirtualThreadAsyncConfiguration implements AsyncConfigurer {

    private final VirtualThreadProperties properties;

    public VirtualThreadAsyncConfiguration(VirtualThreadProperties properties) {
        this.properties = properties;
        log.info("[Virtual Thread] 异步任务配置已启用，@Async 将使用虚拟线程执行");
    }

    @Override
    public Executor getAsyncExecutor() {
        if (!properties.isAsyncEnabled()) {
            return null; // 使用默认执行器
        }
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name(properties.getThreadNamePrefix() + "async-", 0)
                        .factory()
        );
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}

package com.chua.starter.ai.support.configuration.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 异步任务配置——注册 {@code taskExecutor} 线程池。
 *
 * <p>{@code @Async("taskExecutor")}（用量统计、AppKey 使用次数更新）依赖该 Bean，
 * 当未引入系统 starter 时由本模块兜底提供。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Configuration
public class AiAsyncConfiguration {

    /**
     * 核心线程数
     */
    private static final int CORE_POOL_SIZE = 4;

    /**
     * 最大线程数
     */
    private static final int MAX_POOL_SIZE = 16;

    /**
     * 队列容量
     */
    private static final int QUEUE_CAPACITY = 500;

    /**
     * 异步任务线程池
     *
     * @return 线程池执行器
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("ai-async-task-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("[AiAsyncConfiguration] AI 异步任务线程池初始化完成: corePoolSize={}, maxPoolSize={}",
                CORE_POOL_SIZE, MAX_POOL_SIZE);
        return executor;
    }
}

package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.application.ModuleEnvironmentRegistration;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.common.support.log.MdcTaskDecorator;
import com.chua.starter.common.support.properties.AsyncThreadPoolProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务
 *
 * @author CH
 */
@EnableAsync
@EnableConfigurationProperties(AsyncThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "plugin.async", name = "enable", havingValue = "true", matchIfMissing = true)
public class AsyncThreadPoolConfiguration implements AsyncConfigurer {
    /**
     * 构造函数
     *
     * @param asyncProperties AsyncThreadPoolProperties
     */
    public AsyncThreadPoolConfiguration(AsyncThreadPoolProperties asyncProperties) {
        this.asyncProperties = asyncProperties;
    }


    final AsyncThreadPoolProperties asyncProperties;

    /**
     * 注册异步线程池配置到全局环境
     */
    @PostConstruct
    public void registerEnvironment() {
        new ModuleEnvironmentRegistration(AsyncThreadPoolProperties.PRE, asyncProperties, asyncProperties.isEnable());
    }

    /**
     * 创建共享线程池（DEFAULT_EXECUTOR2 和 DEFAULT_TASK_EXECUTOR 均指向同一实例，保持向后兼容）
     */
    @Bean({Constant.DEFAULT_EXECUTOR2, Constant.DEFAULT_TASK_EXECUTOR})
    public ThreadPoolTaskExecutor sharedThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncProperties.getCorePoolSize());
        executor.setMaxPoolSize(asyncProperties.getMaxPoolSize());
        executor.setQueueCapacity(asyncProperties.getQueueCapacity());
        executor.setKeepAliveSeconds(asyncProperties.getKeepAliveSeconds());
        executor.setThreadNamePrefix("shared-thread-pool-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(asyncProperties.getAwaitTerminationSeconds());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean(Constant.DEFAULT_EXECUTOR)
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：线程池创建的时候初始化的线程数
        executor.setCorePoolSize(asyncProperties.getCorePoolSize());
        // 最大线程数：线程池最大的线程数，只有缓冲队列满了之后才会申请超过核心线程数的线程
        executor.setMaxPoolSize(asyncProperties.getMaxPoolSize());
        // 缓冲队列：用来缓冲执行任务的队列
        executor.setQueueCapacity(asyncProperties.getQueueCapacity());
        // 线程池关闭：等待所有任务都完成再关闭
        executor.setWaitForTasksToCompleteOnShutdown(asyncProperties.isWaitForTasksToCompleteOnShutdown());
        // 等待时间：等待秒后强制停止
        executor.setAwaitTerminationSeconds(asyncProperties.getAwaitTerminationSeconds());
        // 允许空闲时间：超过核心线程之外的线程到达60秒后会被销毁
        executor.setKeepAliveSeconds(asyncProperties.getKeepAliveSeconds());
        // 线程名称前缀
        executor.setThreadNamePrefix(asyncProperties.getThreadNamePrefix());
        executor.setTaskDecorator(new MdcTaskDecorator());
        // 初始化线程池
        executor.initialize();
        return executor;
    }
    @Bean
    public TaskDecorator taskDecorator() {
       return new MdcTaskDecorator();
    }

    @Override
    public Executor getAsyncExecutor() {
        return asyncExecutor();
    }
}


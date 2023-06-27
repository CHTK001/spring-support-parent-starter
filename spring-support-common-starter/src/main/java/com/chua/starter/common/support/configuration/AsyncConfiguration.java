package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.common.support.properties.AsyncProperties;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.Resource;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务
 *
 * @author CH
 */
@EnableAsync
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncConfiguration extends AsyncConfigurerSupport {

    @Resource
    AsyncProperties asyncProperties;

    @Bean(Constant.DEFAULT_EXECUTOR2)
    public ThreadPoolTaskExecutor FebsShiroThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //配置核心线程数
        executor.setCorePoolSize(20);
        //配置最大线程数
        executor.setMaxPoolSize(200);
        //配置队列大小
        executor.setQueueCapacity(100000);
        //线程池维护线程所允许的空闲时间
        executor.setKeepAliveSeconds(10);
        //配置线程池中的线程的名称前缀
        executor.setThreadNamePrefix("async-thread-pool-");
        //设置线程池关闭的时候等待所有任务都完成再继续销毁其他的Bean
        executor.setWaitForTasksToCompleteOnShutdown(true);
        //设置线程池中任务的等待时间，如果超过这个时候还没有销毁就强制销毁，以确保应用最后能够被关闭，而不是阻塞住
        executor.setAwaitTerminationSeconds(60);
        // rejection-policy：当pool已经达到max size的时候，如何处理新任务
        // CALLER_RUNS：不在新线程中执行任务，而是由调用者所在的线程来执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        //执行初始化
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
        // 等待时间：等待5秒后强制停止
        executor.setAwaitTerminationSeconds(asyncProperties.getAwaitTerminationSeconds());
        // 允许空闲时间：超过核心线程之外的线程到达60秒后会被销毁
        executor.setKeepAliveSeconds(asyncProperties.getKeepAliveSeconds());
        // 线程名称前缀
        executor.setThreadNamePrefix(asyncProperties.getThreadNamePrefix());
        // 初始化线程
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return asyncExecutor();
    }
}

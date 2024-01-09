package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.common.support.properties.AsyncThreadPoolProperties.PRE;

/**
 * 线程池配置
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class AsyncThreadPoolProperties {

    public static final String PRE = "plugin.thread";
    /**
     * 核心线程数：线程池创建的时候初始化的线程数
     */
    private int corePoolSize = 20;
    /**
     * 最大线程数：线程池最大的线程数，只有缓冲队列满了之后才会申请超过核心线程数的线程
     */
    private int maxPoolSize = 200;
    /**
     * 缓冲队列：用来缓冲执行任务的队列
     */
    private int queueCapacity = 100000;
    /**
     * 线程池关闭：等待所有任务都完成再关闭
     */
    private boolean waitForTasksToCompleteOnShutdown;
    /**
     * 等待时间：等待5秒后强制停止
     */
    private int awaitTerminationSeconds = Integer.MAX_VALUE;
    /**
     * 允许空闲时间：超过核心线程之外的线程到达x秒后会被销毁
     */
    private int keepAliveSeconds = 10;
    /**
     * 线程名称前缀
     */
    private String threadNamePrefix = "async-thread-pool-%d";
}

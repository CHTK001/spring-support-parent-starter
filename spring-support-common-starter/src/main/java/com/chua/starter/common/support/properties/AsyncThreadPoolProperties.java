package com.chua.starter.common.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.common.support.properties.AsyncThreadPoolProperties.PRE;

/**
 * 线程池配置
 * @author CH
 */
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class AsyncThreadPoolProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


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
     * 等待时间：等待秒后强制停止
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
    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 设置 enable
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 获取 corePoolSize
     *
     * @return corePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * 设置 corePoolSize
     *
     * @param corePoolSize corePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    /**
     * 获取 maxPoolSize
     *
     * @return maxPoolSize
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * 设置 maxPoolSize
     *
     * @param maxPoolSize maxPoolSize
     */
    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    /**
     * 获取 queueCapacity
     *
     * @return queueCapacity
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * 设置 queueCapacity
     *
     * @param queueCapacity queueCapacity
     */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * 获取 waitForTasksToCompleteOnShutdown
     *
     * @return waitForTasksToCompleteOnShutdown
     */
    public boolean getWaitForTasksToCompleteOnShutdown() {
        return waitForTasksToCompleteOnShutdown;
    }

    /**
     * 设置 waitForTasksToCompleteOnShutdown
     *
     * @param waitForTasksToCompleteOnShutdown waitForTasksToCompleteOnShutdown
     */
    public void setWaitForTasksToCompleteOnShutdown(boolean waitForTasksToCompleteOnShutdown) {
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
    }

    /**
     * 获取 awaitTerminationSeconds
     *
     * @return awaitTerminationSeconds
     */
    public int getAwaitTerminationSeconds() {
        return awaitTerminationSeconds;
    }

    /**
     * 设置 awaitTerminationSeconds
     *
     * @param awaitTerminationSeconds awaitTerminationSeconds
     */
    public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }

    /**
     * 获取 keepAliveSeconds
     *
     * @return keepAliveSeconds
     */
    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    /**
     * 设置 keepAliveSeconds
     *
     * @param keepAliveSeconds keepAliveSeconds
     */
    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    /**
     * 获取 threadNamePrefix
     *
     * @return threadNamePrefix
     */
    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    /**
     * 设置 threadNamePrefix
     *
     * @param threadNamePrefix threadNamePrefix
     */
    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }




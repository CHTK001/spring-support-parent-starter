package com.chua.starter.common.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.common.support.properties.AsyncThreadPoolProperties.PRE;

/**
 * 线程池配置
 * @author CH
 * @since 4.0.0
 */
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class AsyncThreadPoolProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /**
     * PRE
    */
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
     * 等待时间：等待秒后强制停止（默认60秒，避免应用关闭时无限等待）
     */
    private int awaitTerminationSeconds = 60;
    /**
     * 允许空闲时间：超过核心线程之外的线程到达x秒后会被销毁
     */
    private int keepAliveSeconds = 10;
    /**
     * 线程名称前缀
     */
    private String threadNamePrefix = "async-thread-pool-%d";

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 判断是否启用
     *
     * @return enable
     */
    public boolean isEnable() {
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
     * 获取 核心游泳池大小
     *
     * @return corePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * 设置 核心游泳池大小
     *
     * @param corePoolSize 核心游泳池大小
     */
    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    /**
     * 获取 最大游泳池大小
     *
     * @return maxPoolSize
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * 设置 最大游泳池大小
     *
     * @param maxPoolSize 最大游泳池大小
     */
    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    /**
     * 获取 队列容量
     *
     * @return queueCapacity
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * 设置 队列容量
     *
     * @param queueCapacity 队列容量
     */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * 获取 waitfor任务转为完成on关闭
     *
     * @return waitForTasksToCompleteOnShutdown
     */
    public boolean getWaitForTasksToCompleteOnShutdown() {
        return waitForTasksToCompleteOnShutdown;
    }

    /**
     * 判断是否等待任务完成再关闭
     *
     * @return waitForTasksToCompleteOnShutdown
     */
    public boolean isWaitForTasksToCompleteOnShutdown() {
        return waitForTasksToCompleteOnShutdown;
    }

    /**
     * 设置 waitfor任务转为完成on关闭
     *
     * @param waitForTasksToCompleteOnShutdown waitfor任务转为完成on关闭
     */
    public void setWaitForTasksToCompleteOnShutdown(boolean waitForTasksToCompleteOnShutdown) {
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
    }

    /**
     * 获取 await终止seconds
     *
     * @return awaitTerminationSeconds
     */
    public int getAwaitTerminationSeconds() {
        return awaitTerminationSeconds;
    }

    /**
     * 设置 await终止seconds
     *
     * @param awaitTerminationSeconds await终止seconds
     */
    public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }

    /**
     * 获取 keepaliveseconds
     *
     * @return keepAliveSeconds
     */
    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    /**
     * 设置 keepaliveseconds
     *
     * @param keepAliveSeconds keepaliveseconds
     */
    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    /**
     * 获取 thread名称前缀
     *
     * @return threadNamePrefix
     */
    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    /**
     * 设置 thread名称前缀
     *
     * @param threadNamePrefix thread名称前缀
     */
    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }
}

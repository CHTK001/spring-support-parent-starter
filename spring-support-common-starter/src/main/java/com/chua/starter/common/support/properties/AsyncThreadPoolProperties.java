package com.chua.starter.common.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.common.support.properties.AsyncThreadPoolProperties.PRE;

/**
 * 异步线程池配置属性。
 *
 * <p>前缀 {@code plugin.thread}。注意：本配置类的 {@code enable} 仅影响 /service-info
 * 展示与环境登记；线程池 Bean 的装配开关是 {@code plugin.async.enable}（默认 true）。</p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   thread:
 *     core-pool-size: 20          # 核心线程数（默认 20）
 *     max-pool-size: 200          # 最大线程数（默认 200）
 *     queue-capacity: 100000      # 缓冲队列容量（默认 100000）
 *     keep-alive-seconds: 10      # 非核心线程空闲存活秒数（默认 10）
 *     wait-for-tasks-to-complete-on-shutdown: false  # 关闭时等待任务完成（默认 false）
 *     await-termination-seconds: 60  # 关闭等待秒数，超时强制停止（默认 60）
 *     thread-name-prefix: async-thread-pool-%d  # 线程名前缀（默认 async-thread-pool-%d）
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class AsyncThreadPoolProperties {
    /**
     * 是否启用。
     * <p>YAML：{@code plugin.thread.enable}，默认 {@code false}。注意：该开关仅影响
     * /service-info 展示与环境登记，线程池装配开关是 {@code plugin.async.enable}。</p>
     */
    private boolean enable = false;


    /**
     * PRE
    */
    public static final String PRE = "plugin.thread";
    /**
     * 核心线程数：线程池创建的时候初始化的线程数。
     * <p>YAML：{@code plugin.thread.core-pool-size}，默认 {@code 20}。</p>
     */
    // [P3C A20 豁免]
    private int corePoolSize = 20;
    /**
     * 最大线程数：线程池最大的线程数，只有缓冲队列满了之后才会申请超过核心线程数的线程。
     * <p>YAML：{@code plugin.thread.max-pool-size}，默认 {@code 200}。</p>
     */
    // [P3C A20 豁免]
    private int maxPoolSize = 200;
    /**
     * 缓冲队列：用来缓冲执行任务的队列。
     * <p>YAML：{@code plugin.thread.queue-capacity}，默认 {@code 100000}；队列过大需注意任务积压的内存压力。</p>
     */
    // [P3C A20 豁免]
    private int queueCapacity = 100000;
    /**
     * 线程池关闭：等待所有任务都完成再关闭。
     * <p>YAML：{@code plugin.thread.wait-for-tasks-to-complete-on-shutdown}，默认 {@code false}。</p>
     */
    private boolean waitForTasksToCompleteOnShutdown;
    /**
     * 等待时间：等待秒后强制停止（默认60秒，避免应用关闭时无限等待）。
     * <p>YAML：{@code plugin.thread.await-termination-seconds}，默认 {@code 60}。</p>
     */
    // [P3C A20 豁免]
    private int awaitTerminationSeconds = 60;
    /**
     * 允许空闲时间：超过核心线程之外的线程到达x秒后会被销毁。
     * <p>YAML：{@code plugin.thread.keep-alive-seconds}，默认 {@code 10}。</p>
     */
    // [P3C A20 豁免]
    private int keepAliveSeconds = 10;
    /**
     * 线程名称前缀。
     * <p>YAML：{@code plugin.thread.thread-name-prefix}，默认 {@code async-thread-pool-%d}。</p>
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

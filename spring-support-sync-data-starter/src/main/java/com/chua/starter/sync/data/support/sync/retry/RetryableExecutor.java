package com.chua.starter.sync.data.support.sync.retry;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * 可重试执行器
 * 支持指数退避策略
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
public class RetryableExecutor {
    
    private final RetryConfig config;
    
    public RetryableExecutor(RetryConfig config) {
        this.config = config;
    }
    
    /**
     * 执行可重试任务
     *
     * @param task 任务
     * @param <T> 返回类型
     * @return 执行结果
     * @throws Exception 执行失败异常
     */
    public <T> T execute(Callable<T> task) throws Exception {
        return execute(task, null);
    }
    
    /**
     * 执行可重试任务（带重试条件）
     *
     * @param task 任务
     * @param retryCondition 重试条件，返回true表示需要重试
     * @param <T> 返回类型
     * @return 执行结果
     * @throws Exception 执行失败异常
     */
    public <T> T execute(Callable<T> task, Predicate<Exception> retryCondition) throws Exception {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt <= config.getMaxRetries()) {
            try {
                if (attempt > 0) {
                    log.info("重试执行任务, 第{}次尝试", attempt);
                }
                
                T result = task.call();
                
                if (attempt > 0) {
                    log.info("任务重试成功, 尝试次数: {}", attempt);
                }
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                attempt++;
                
                // 检查是否需要重试
                if (attempt > config.getMaxRetries()) {
                    log.error("任务执行失败，已达到最大重试次数: {}", config.getMaxRetries(), e);
                    break;
                }
                
                // 检查重试条件
                if (retryCondition != null && !retryCondition.test(e)) {
                    log.warn("任务执行失败，不满足重试条件: {}", e.getMessage());
                    throw e;
                }
                
                // 计算退避时间
                long backoffTime = calculateBackoff(attempt);
                log.warn("任务执行失败, 第{}次尝试, {}ms后重试: {}", 
                        attempt, backoffTime, e.getMessage());
                
                // 等待退避时间
                try {
                    Thread.sleep(backoffTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试等待被中断", ie);
                }
            }
        }
        
        throw lastException;
    }
    
    /**
     * 计算退避时间（指数退避）
     *
     * @param attempt 尝试次数
     * @return 退避时间（毫秒）
     */
    private long calculateBackoff(int attempt) {
        if (!config.isExponentialBackoff()) {
            return config.getRetryInterval();
        }
        
        // 指数退避：baseInterval * (2 ^ (attempt - 1))
        long backoff = (long) (config.getRetryInterval() * Math.pow(2, attempt - 1));
        
        // 限制最大退避时间
        return Math.min(backoff, config.getMaxBackoffTime());
    }
    
    /**
     * 重试配置
     */
    @Data
    public static class RetryConfig {
        /**
         * 最大重试次数
         */
        private int maxRetries = 3;
        
        /**
         * 重试间隔（毫秒）
         */
        private long retryInterval = 1000;
        
        /**
         * 是否使用指数退避
         */
        private boolean exponentialBackoff = true;
        
        /**
         * 最大退避时间（毫秒）
         */
        private long maxBackoffTime = 30000;
        
        public static RetryConfig defaultConfig() {
            return new RetryConfig();
        }
        
        public static RetryConfig of(int maxRetries, long retryInterval) {
            RetryConfig config = new RetryConfig();
            config.setMaxRetries(maxRetries);
            config.setRetryInterval(retryInterval);
            return config;
        }
    }
}

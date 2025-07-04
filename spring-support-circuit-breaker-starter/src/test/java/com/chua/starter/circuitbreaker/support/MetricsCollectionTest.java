package com.chua.starter.circuitbreaker.support;

import com.chua.starter.circuitbreaker.support.annotation.RateLimiter;
import com.chua.starter.circuitbreaker.support.metrics.RateLimiterMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 指标收集功能测试
 * 
 * 测试RateLimiterMetrics的各种指标收集功能
 * 
 * @author CH
 * @since 2024/12/20
 */
public class MetricsCollectionTest {

    private RateLimiterMetrics rateLimiterMetrics;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        rateLimiterMetrics = new RateLimiterMetrics(meterRegistry);
    }

    @Test
    public void testRecordRequest() {
        String limiterName = "test-limiter";
        RateLimiter.Dimension dimension = RateLimiter.Dimension.GLOBAL;
        
        // 记录成功请求
        rateLimiterMetrics.recordRequest(limiterName, dimension, "success");
        rateLimiterMetrics.recordRequest(limiterName, dimension, "success");
        
        // 记录拒绝请求
        rateLimiterMetrics.recordRequest(limiterName, dimension, "rejected");
        
        // 验证计数器
        Counter successCounter = meterRegistry.find("rate_limiter_qps_total")
            .tag("limiter_name", limiterName)
            .tag("dimension", "global")
            .tag("status", "success")
            .counter();
        
        assertNotNull(successCounter, "成功请求计数器应该存在");
        assertEquals(2.0, successCounter.count(), "成功请求计数应该为2");
        
        Counter rejectedCounter = meterRegistry.find("rate_limiter_qps_total")
            .tag("limiter_name", limiterName)
            .tag("dimension", "global")
            .tag("status", "rejected")
            .counter();
        
        assertNotNull(rejectedCounter, "拒绝请求计数器应该存在");
        assertEquals(1.0, rejectedCounter.count(), "拒绝请求计数应该为1");
    }

    @Test
    public void testRecordExecutionTime() {
        String limiterName = "execution-test";
        RateLimiter.Dimension dimension = RateLimiter.Dimension.IP;
        Duration duration = Duration.ofMillis(100);
        
        // 记录执行时间
        rateLimiterMetrics.recordExecutionTime(limiterName, dimension, duration, "success");
        
        // 验证计时器
        Timer timer = meterRegistry.find("rate_limiter_execution_time")
            .tag("limiter_name", limiterName)
            .tag("dimension", "ip")
            .tag("status", "success")
            .timer();
        
        assertNotNull(timer, "执行时间计时器应该存在");
        assertEquals(1, timer.count(), "计时器记录次数应该为1");
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) >= 100, "总执行时间应该大于等于100ms");
    }

    @Test
    public void testRecordWaitTime() {
        String limiterName = "wait-test";
        RateLimiter.Dimension dimension = RateLimiter.Dimension.USER;
        Duration waitTime = Duration.ofMillis(50);
        
        // 记录等待时间
        rateLimiterMetrics.recordWaitTime(limiterName, dimension, waitTime);
        
        // 验证计时器
        Timer timer = meterRegistry.find("rate_limiter_wait_time")
            .tag("limiter_name", limiterName)
            .tag("dimension", "user")
            .timer();
        
        assertNotNull(timer, "等待时间计时器应该存在");
        assertEquals(1, timer.count(), "计时器记录次数应该为1");
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) >= 50, "总等待时间应该大于等于50ms");
    }

    @Test
    public void testRecordCurrentState() {
        String limiterName = "state-test";
        RateLimiter.Dimension dimension = RateLimiter.Dimension.API;
        int availablePermissions = 5;
        int waitingThreads = 2;
        
        // 记录当前状态
        rateLimiterMetrics.recordCurrentState(limiterName, dimension, availablePermissions, waitingThreads);
        
        // 验证仪表盘指标
        Double availableGauge = meterRegistry.find("rate_limiter_available_permissions")
            .tag("limiter_name", limiterName)
            .tag("dimension", "api")
            .gauge()
            .value();
        
        assertNotNull(availableGauge, "可用许可数仪表盘应该存在");
        assertEquals(5.0, availableGauge, "可用许可数应该为5");
        
        Double waitingGauge = meterRegistry.find("rate_limiter_waiting_threads")
            .tag("limiter_name", limiterName)
            .tag("dimension", "api")
            .gauge()
            .value();
        
        assertNotNull(waitingGauge, "等待线程数仪表盘应该存在");
        assertEquals(2.0, waitingGauge, "等待线程数应该为2");
    }

    @Test
    public void testDimensionSpecificMetrics() {
        String limiterName = "dimension-test";
        
        // 测试不同维度的指标
        rateLimiterMetrics.recordRequest(limiterName, RateLimiter.Dimension.GLOBAL, "success");
        rateLimiterMetrics.recordRequest(limiterName, RateLimiter.Dimension.IP, "success");
        rateLimiterMetrics.recordRequest(limiterName, RateLimiter.Dimension.USER, "rejected");
        rateLimiterMetrics.recordRequest(limiterName, RateLimiter.Dimension.API, "success");
        
        // 验证按维度的QPS指标
        Counter globalCounter = meterRegistry.find("rate_limiter_qps_global")
            .tag("limiter_name", limiterName)
            .tag("status", "success")
            .counter();
        assertNotNull(globalCounter, "全局QPS计数器应该存在");
        assertEquals(1.0, globalCounter.count());
        
        Counter ipCounter = meterRegistry.find("rate_limiter_qps_ip")
            .tag("limiter_name", limiterName)
            .tag("status", "success")
            .counter();
        assertNotNull(ipCounter, "IP QPS计数器应该存在");
        assertEquals(1.0, ipCounter.count());
        
        Counter userCounter = meterRegistry.find("rate_limiter_qps_user")
            .tag("limiter_name", limiterName)
            .tag("status", "rejected")
            .counter();
        assertNotNull(userCounter, "用户QPS计数器应该存在");
        assertEquals(1.0, userCounter.count());
        
        Counter apiCounter = meterRegistry.find("rate_limiter_qps_api")
            .tag("limiter_name", limiterName)
            .tag("status", "success")
            .counter();
        assertNotNull(apiCounter, "API QPS计数器应该存在");
        assertEquals(1.0, apiCounter.count());
    }

    @Test
    public void testGetStatistics() {
        String limiterName = "stats-test";
        RateLimiter.Dimension dimension = RateLimiter.Dimension.GLOBAL;
        
        // 记录一些指标数据
        rateLimiterMetrics.recordRequest(limiterName, dimension, "success");
        rateLimiterMetrics.recordRequest(limiterName, dimension, "success");
        rateLimiterMetrics.recordRequest(limiterName, dimension, "rejected");
        
        // 获取统计信息
        RateLimiterMetrics.MetricsStatistics statistics = rateLimiterMetrics.getStatistics();
        
        assertNotNull(statistics, "统计信息不应为空");
        assertTrue(statistics.getTotalCounters() > 0, "应该有计数器");
        assertEquals(3, statistics.getTotalRequests(), "总请求数应该为3");
        assertEquals(2, statistics.getSuccessRequests(), "成功请求数应该为2");
        assertEquals(1, statistics.getRejectedRequests(), "拒绝请求数应该为1");
        assertEquals(2.0/3.0, statistics.getSuccessRate(), 0.001, "成功率应该为2/3");
    }

    @Test
    public void testClearMetrics() {
        String limiterName = "clear-test";
        RateLimiter.Dimension dimension = RateLimiter.Dimension.GLOBAL;
        
        // 记录一些指标
        rateLimiterMetrics.recordRequest(limiterName, dimension, "success");
        rateLimiterMetrics.recordExecutionTime(limiterName, dimension, Duration.ofMillis(100), "success");
        
        // 验证指标存在
        RateLimiterMetrics.MetricsStatistics beforeClear = rateLimiterMetrics.getStatistics();
        assertTrue(beforeClear.getTotalCounters() > 0, "清理前应该有计数器");
        
        // 清理指标
        rateLimiterMetrics.clearMetrics(limiterName);
        
        // 注意：SimpleMeterRegistry不支持移除指标，所以这里主要测试方法不抛异常
        assertDoesNotThrow(() -> rateLimiterMetrics.clearMetrics(limiterName), 
                          "清理指标方法不应该抛出异常");
    }

    @Test
    public void testConcurrentMetricsCollection() throws InterruptedException {
        String limiterName = "concurrent-test";
        RateLimiter.Dimension dimension = RateLimiter.Dimension.GLOBAL;
        
        int threadCount = 10;
        int requestsPerThread = 100;
        
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    rateLimiterMetrics.recordRequest(limiterName, dimension, "success");
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证计数器的正确性
        Counter counter = meterRegistry.find("rate_limiter_qps_total")
            .tag("limiter_name", limiterName)
            .tag("dimension", "global")
            .tag("status", "success")
            .counter();
        
        assertNotNull(counter, "计数器应该存在");
        assertEquals(threadCount * requestsPerThread, counter.count(), 
                    "计数器应该记录所有并发请求");
    }

    @Test
    public void testMetricsWithSpecialCharacters() {
        // 测试包含特殊字符的限流器名称
        String limiterName = "test-limiter.with:special@chars";
        RateLimiter.Dimension dimension = RateLimiter.Dimension.GLOBAL;
        
        // 记录指标（应该不抛异常）
        assertDoesNotThrow(() -> {
            rateLimiterMetrics.recordRequest(limiterName, dimension, "success");
            rateLimiterMetrics.recordExecutionTime(limiterName, dimension, Duration.ofMillis(50), "success");
            rateLimiterMetrics.recordCurrentState(limiterName, dimension, 10, 0);
        }, "包含特殊字符的限流器名称应该能正常记录指标");
    }
}

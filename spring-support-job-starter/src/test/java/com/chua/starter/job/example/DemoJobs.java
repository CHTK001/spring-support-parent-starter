package com.chua.starter.job.example;

import com.chua.starter.job.support.annotation.Job;
import com.chua.starter.job.support.log.JobLogHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 启动服务测接口用的示例任务集。
 *
 * <p>涵盖本模块主要业务形态, 便于通过运行日志直观核对:</p>
 * <ul>
 *   <li>{@code @Job("example.hello")} 每秒成功执行并上行 SUCCESS;</li>
 *   <li>{@code @Job("example.fail")} 每秒执行并故意抛异常, 上行 FAILURE;</li>
 *   <li>{@code @Job("example.log")} 通过 {@link JobLogHelper} 收集日志并随事件上行;</li>
 *   <li>{@code @Job("example.fixedRate")} 以 fixedRate=2000 固定频率重复触发;</li>
 *   <li>{@code @Job("example.fixedDelay")} 以 fixedDelay=2000 固定延迟触发;</li>
 *   <li>{@code @Job("example.lifecycle")} 含 init/destroy 生命周期钩子;</li>
 *   <li>Spring 原生 {@code @Scheduled} 每秒触发一次。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class DemoJobs {

    /**
     * 并发压测任务统一触发周期: 每秒同峰触发一次
     */
    private static final String CC_CRON = "0/1 * * * * ?";

    /**
     * hello 任务触发计数
     */
    private final AtomicInteger helloCount = new AtomicInteger(0);

    /**
     * 失败 任务触发计数
     */
    private final AtomicInteger failCount = new AtomicInteger(0);

    /**
     * 原生定时触发计数
     */
    private final AtomicInteger scheduledCount = new AtomicInteger(0);

    /**
     * 并发测试: 当前处于执行中的任务数
     */
    private final AtomicInteger concurrentActive = new AtomicInteger(0);

    /**
     * 并发测试: 历史观测到的最大并发执行数
     */
    private final AtomicInteger concurrentMax = new AtomicInteger(0);

    /**
     * 每秒成功执行示例任务。
     */
    @Job(value = "example.hello", desc = "示例成功任务", cron = "0/1 * * * * ?")
    public void hello() {
        int n = helloCount.incrementAndGet();
        log.info("[example] hello 执行成功 count={}", n);
    }

    /**
     * 每秒故意失败示例任务。
     */
    @Job(value = "example.fail", desc = "示例失败任务", cron = "0/1 * * * * ?")
    public void fail() {
        failCount.incrementAndGet();
        /**
         * illegal状态异常。
         * @param 故意失败" 故意失败"
         * @return illegal状态异常的结果
         */
        throw new IllegalStateException("example 故意失败");
    }

    /**
     * 每 2 秒执行, 通过 {@link JobLogHelper} 收集线程内日志, 验证其随事件上行。
     */
    @Job(value = "example.log", desc = "示例 JobLogHelper 收集任务", cron = "0/2 * * * * ?")
    public void logCollect() {
        int n = helloCount.incrementAndGet();
        JobLogHelper.log("example.log 第 " + n + " 次执行, 收集本条日志");
        JobLogHelper.log("第二行收集日志, 验证多行 Base64 编码上行");
        log.info("[example] logCollect 执行 count={}", n);
    }

    /**
     * 固定频率触发 (fixedrate=2000ms), 验证调度引擎 FIXED 支持。
     */
    @Job(value = "example.fixedRate", desc = "示例 fixedRate 任务", fixedRate = 2000L)
    public void fixedRateTick() {
        int n = failCount.incrementAndGet();
        log.info("[example] fixedRate 触发 count={}", n);
    }

    /**
     * 固定延迟触发 (fixed延迟=2000ms), 验证调度引擎 延迟 支持。
     */
    @Job(value = "example.fixedDelay", desc = "示例 fixedDelay 任务", fixedDelay = 2000L)
    public void fixedDelayTick() {
        int n = scheduledCount.incrementAndGet();
        log.info("[example] fixedDelay 触发 count={}", n);
    }

    /**
     * 生命周期示例: 初始化 在首次执行前回调, 销毁 在关闭时回调。
     */
    @Job(value = "example.lifecycle", desc = "示例生命周期任务",
            cron = "0/3 * * * * ?", init = "lifecycleInit", destroy = "lifecycleDestroy")
    /**
     * lifecycletick
    */
    public void lifecycleTick() {
        log.info("[example] lifecycle 执行");
    }

    /**
     * 生命周期 初始化 钩子 (无参)。
     */
    public void lifecycleInit() {
        log.info("[example] lifecycle init 钩子被调用");
    }

    /**
     * 生命周期 销毁 钩子 (无参)。
     */
    public void lifecycleDestroy() {
        log.info("[example] lifecycle destroy 钩子被调用");
    }

    /**
     * 并发压测任务组: 共 12 个任务, 皆为每秒同峰触发, 每个执行耗时约 1.5s,
     * 使得下一波任务到达时上一波仍未结束, 从而观测线程池真实并发上限。
     *
     * <p>通过 {@link #concurrentTick(int)} 共享进入/退出计数,
     * 可依据 {@code concurrentMax} 判断实际并行执行的任务数。</p>
     */
    @Job(value = "example.cc.01", desc = "并发压测任务 01", cron = CC_CRON)
    public void cc01() {
        /**
         * 并发tick。
         * @param 1 1
         */
        concurrentTick(1);
    }

    /**
     * 并发压测任务 02。
     */
    @Job(value = "example.cc.02", desc = "并发压测任务 02", cron = CC_CRON)
    public void cc02() {
        /**
         * 并发tick。
         * @param 2 2
         */
        concurrentTick(2);
    }

    /**
     * 并发压测任务 03。
     */
    @Job(value = "example.cc.03", desc = "并发压测任务 03", cron = CC_CRON)
    public void cc03() {
        /**
         * 并发tick。
         * @param 3 3
         */
        concurrentTick(3);
    }

    /**
     * 并发压测任务 04。
     */
    @Job(value = "example.cc.04", desc = "并发压测任务 04", cron = CC_CRON)
    public void cc04() {
        /**
         * 并发tick。
         * @param 4 4
         */
        concurrentTick(4);
    }

    /**
     * 并发压测任务 05。
     */
    @Job(value = "example.cc.05", desc = "并发压测任务 05", cron = CC_CRON)
    public void cc05() {
        /**
         * 并发tick。
         * @param 5 5
         */
        concurrentTick(5);
    }

    /**
     * 并发压测任务 06。
     */
    @Job(value = "example.cc.06", desc = "并发压测任务 06", cron = CC_CRON)
    public void cc06() {
        /**
         * 并发tick。
         * @param 6 6
         */
        concurrentTick(6);
    }

    /**
     * 并发压测任务 07。
     */
    @Job(value = "example.cc.07", desc = "并发压测任务 07", cron = CC_CRON)
    public void cc07() {
        /**
         * 并发tick。
         * @param 7 7
         */
        concurrentTick(7);
    }

    /**
     * 并发压测任务 08。
     */
    @Job(value = "example.cc.08", desc = "并发压测任务 08", cron = CC_CRON)
    public void cc08() {
        /**
         * 并发tick。
         * @param 8 8
         */
        concurrentTick(8);
    }

    /**
     * 并发压测任务 09。
     */
    @Job(value = "example.cc.09", desc = "并发压测任务 09", cron = CC_CRON)
    public void cc09() {
        /**
         * 并发tick。
         * @param 9 9
         */
        concurrentTick(9);
    }

    /**
     * 并发压测任务 10。
     */
    @Job(value = "example.cc.10", desc = "并发压测任务 10", cron = CC_CRON)
    public void cc10() {
        /**
         * 并发tick。
         * @param 10 10
         */
        concurrentTick(10);
    }

    /**
     * 并发压测任务 11。
     */
    @Job(value = "example.cc.11", desc = "并发压测任务 11", cron = CC_CRON)
    public void cc11() {
        /**
         * 并发tick。
         * @param 11 11
         */
        concurrentTick(11);
    }

    /**
     * 并发压测任务 12。
     */
    @Job(value = "example.cc.12", desc = "并发压测任务 12", cron = CC_CRON)
    public void cc12() {
        /**
         * 并发tick。
         * @param 12 12
         */
        concurrentTick(12);
    }

    /**
     * 并发压测通用执行体: 记录进入/退出计数与历史峰值。
     *
     * @param index 任务序号
     */
    private void concurrentTick(int index) {
        int active = concurrentActive.incrementAndGet();
        int max = concurrentMax.accumulateAndGet(active, Math::max);
        log.info("[example][cc] enter idx={} active={} max={} thread={}", index, active, max,
                Thread.currentThread().getName());
        try {
            Thread.sleep(1500L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        int left = concurrentActive.decrementAndGet();
        log.info("[example][cc] leave idx={} active={} max={}", index, left, max);
    }

    /**
     * Spring 原生定时任务示例 (由本模块 {@code @Scheduled} 扫描器注册到 任务调度器)。
     */
    @Scheduled(cron = "0/1 * * * * ?")
    public void scheduledTick() {
        int n = scheduledCount.incrementAndGet();
        log.info("[example] @Scheduled 触发 count={}", n);
    }
}
package com.chua.starter.job.tpload;

import com.chua.starter.job.support.annotation.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 吞吐量压测专用任务集 (独立于 {@link DemoJobs}, 避免并发压测任务互相挤占)。
 *
 * <p>共 24 个任务, 皆为每秒同峰触发, 请求率约 24 次/秒; 每个任务模拟耗时
 * {@link #TP_WORK_MS}=500ms。当线程池核心线程数低于处理能力所需 (约 2 个任务/线程/秒)
 * 时, 观测到的吞吐受线程数约束, 借此验证扩容与吞吐的线性关系。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class TpJobs {

    /**
     * 吞吐量压测任务统一触发周期: 每秒同峰触发一次
     */
    private static final String TP_CRON = "0/1 * * * * ?";

    /**
     * 吞吐量压测: 单个任务模拟耗时 (毫秒)
     */
    private static final long TP_WORK_MS = 500L;

    /**
     * 吞吐量测试: 当前处于执行中的任务数
     */
    private final AtomicInteger tpActive = new AtomicInteger(0);

    /**
     * 吞吐量测试: 已完成任务总数
     */
    private final AtomicLong tpDone = new AtomicLong(0);

    /**
     * 吞吐量压测任务 01。
     */
    @Job(value = "example.tp.01", desc = "吞吐量压测任务 01", cron = TP_CRON)
    public void tp01() {
        /**
         * 吞吐量tick。
         * @param 1 1
         */
        throughputTick(1);
    }

    /**
     * 吞吐量压测任务 02。
     */
    @Job(value = "example.tp.02", desc = "吞吐量压测任务 02", cron = TP_CRON)
    public void tp02() {
        /**
         * 吞吐量tick。
         * @param 2 2
         */
        throughputTick(2);
    }

    /**
     * 吞吐量压测任务 03。
     */
    @Job(value = "example.tp.03", desc = "吞吐量压测任务 03", cron = TP_CRON)
    public void tp03() {
        /**
         * 吞吐量tick。
         * @param 3 3
         */
        throughputTick(3);
    }

    /**
     * 吞吐量压测任务 04。
     */
    @Job(value = "example.tp.04", desc = "吞吐量压测任务 04", cron = TP_CRON)
    public void tp04() {
        /**
         * 吞吐量tick。
         * @param 4 4
         */
        throughputTick(4);
    }

    /**
     * 吞吐量压测任务 05。
     */
    @Job(value = "example.tp.05", desc = "吞吐量压测任务 05", cron = TP_CRON)
    public void tp05() {
        /**
         * 吞吐量tick。
         * @param 5 5
         */
        throughputTick(5);
    }

    /**
     * 吞吐量压测任务 06。
     */
    @Job(value = "example.tp.06", desc = "吞吐量压测任务 06", cron = TP_CRON)
    public void tp06() {
        /**
         * 吞吐量tick。
         * @param 6 6
         */
        throughputTick(6);
    }

    /**
     * 吞吐量压测任务 07。
     */
    @Job(value = "example.tp.07", desc = "吞吐量压测任务 07", cron = TP_CRON)
    public void tp07() {
        /**
         * 吞吐量tick。
         * @param 7 7
         */
        throughputTick(7);
    }

    /**
     * 吞吐量压测任务 08。
     */
    @Job(value = "example.tp.08", desc = "吞吐量压测任务 08", cron = TP_CRON)
    public void tp08() {
        /**
         * 吞吐量tick。
         * @param 8 8
         */
        throughputTick(8);
    }

    /**
     * 吞吐量压测任务 09。
     */
    @Job(value = "example.tp.09", desc = "吞吐量压测任务 09", cron = TP_CRON)
    public void tp09() {
        /**
         * 吞吐量tick。
         * @param 9 9
         */
        throughputTick(9);
    }

    /**
     * 吞吐量压测任务 10。
     */
    @Job(value = "example.tp.10", desc = "吞吐量压测任务 10", cron = TP_CRON)
    public void tp10() {
        /**
         * 吞吐量tick。
         * @param 10 10
         */
        throughputTick(10);
    }

    /**
     * 吞吐量压测任务 11。
     */
    @Job(value = "example.tp.11", desc = "吞吐量压测任务 11", cron = TP_CRON)
    public void tp11() {
        /**
         * 吞吐量tick。
         * @param 11 11
         */
        throughputTick(11);
    }

    /**
     * 吞吐量压测任务 12。
     */
    @Job(value = "example.tp.12", desc = "吞吐量压测任务 12", cron = TP_CRON)
    public void tp12() {
        /**
         * 吞吐量tick。
         * @param 12 12
         */
        throughputTick(12);
    }

    /**
     * 吞吐量压测任务 13。
     */
    @Job(value = "example.tp.13", desc = "吞吐量压测任务 13", cron = TP_CRON)
    public void tp13() {
        /**
         * 吞吐量tick。
         * @param 13 13
         */
        throughputTick(13);
    }

    /**
     * 吞吐量压测任务 14。
     */
    @Job(value = "example.tp.14", desc = "吞吐量压测任务 14", cron = TP_CRON)
    public void tp14() {
        /**
         * 吞吐量tick。
         * @param 14 14
         */
        throughputTick(14);
    }

    /**
     * 吞吐量压测任务 15。
     */
    @Job(value = "example.tp.15", desc = "吞吐量压测任务 15", cron = TP_CRON)
    public void tp15() {
        /**
         * 吞吐量tick。
         * @param 15 15
         */
        throughputTick(15);
    }

    /**
     * 吞吐量压测任务 16。
     */
    @Job(value = "example.tp.16", desc = "吞吐量压测任务 16", cron = TP_CRON)
    public void tp16() {
        /**
         * 吞吐量tick。
         * @param 16 16
         */
        throughputTick(16);
    }

    /**
     * 吞吐量压测任务 17。
     */
    @Job(value = "example.tp.17", desc = "吞吐量压测任务 17", cron = TP_CRON)
    public void tp17() {
        /**
         * 吞吐量tick。
         * @param 17 17
         */
        throughputTick(17);
    }

    /**
     * 吞吐量压测任务 18。
     */
    @Job(value = "example.tp.18", desc = "吞吐量压测任务 18", cron = TP_CRON)
    public void tp18() {
        /**
         * 吞吐量tick。
         * @param 18 18
         */
        throughputTick(18);
    }

    /**
     * 吞吐量压测任务 19。
     */
    @Job(value = "example.tp.19", desc = "吞吐量压测任务 19", cron = TP_CRON)
    public void tp19() {
        /**
         * 吞吐量tick。
         * @param 19 19
         */
        throughputTick(19);
    }

    /**
     * 吞吐量压测任务 20。
     */
    @Job(value = "example.tp.20", desc = "吞吐量压测任务 20", cron = TP_CRON)
    public void tp20() {
        /**
         * 吞吐量tick。
         * @param 20 20
         */
        throughputTick(20);
    }

    /**
     * 吞吐量压测任务 21。
     */
    @Job(value = "example.tp.21", desc = "吞吐量压测任务 21", cron = TP_CRON)
    public void tp21() {
        /**
         * 吞吐量tick。
         * @param 21 21
         */
        throughputTick(21);
    }

    /**
     * 吞吐量压测任务 22。
     */
    @Job(value = "example.tp.22", desc = "吞吐量压测任务 22", cron = TP_CRON)
    public void tp22() {
        /**
         * 吞吐量tick。
         * @param 22 22
         */
        throughputTick(22);
    }

    /**
     * 吞吐量压测任务 23。
     */
    @Job(value = "example.tp.23", desc = "吞吐量压测任务 23", cron = TP_CRON)
    public void tp23() {
        /**
         * 吞吐量tick。
         * @param 23 23
         */
        throughputTick(23);
    }

    /**
     * 吞吐量压测任务 24。
     */
    @Job(value = "example.tp.24", desc = "吞吐量压测任务 24", cron = TP_CRON)
    public void tp24() {
        /**
         * 吞吐量tick。
         * @param 24 24
         */
        throughputTick(24);
    }

    /**
     * 吞吐量压测通用执行体: 记录进入/退出计数与已完成任务总数。
     *
     * @param index 任务序号
     */
    private void throughputTick(int index) {
        int active = tpActive.incrementAndGet();
        long done = tpDone.incrementAndGet();
        try {
            Thread.sleep(TP_WORK_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        int left = tpActive.decrementAndGet();
        log.info("[example][tp] done idx={} active={} left={} total={} thread={}", index, active, left, done,
                Thread.currentThread().getName());
    }
}

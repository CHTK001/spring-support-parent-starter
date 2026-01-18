package com.chua.starter.job.support.scheduler;

import com.chua.starter.job.support.JobProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 时间环调度处理器
 * <p>
 * 基于时间环的秒级任务触发器，实现精确的秒级任务调度。
 * 时间环是一个60索引的环形数据结构，每个索引对应一秒。
 * </p>
 *
 * <h3>工作原理</h3>
 * <ol>
 *     <li>{@link CoreTriggerHandler}将任务推送到时间环的指定秒数位置</li>
 *     <li>时间环线程每秒精确触发一次</li>
 *     <li>取出当前秒数和前一秒的任务（避免跨秒丢失）</li>
 *     <li>通过{@link JobTriggerPoolHelper}触发任务执行</li>
 * </ol>
 *
 * <h3>数据结构</h3>
 * <pre>
 * ringData: Map&lt;Integer, List&lt;Integer&gt;&gt;
 *   key: 0-59的秒数
 *   value: 该秒需要执行的任务ID列表
 * </pre>
 *
 * <h3>特性</h3>
 * <ul>
 *     <li>毫秒级精度：通过sleep对齐到每秒的开始</li>
 *     <li>容错机制：向前检查一个刻度，避免处理耗时导致丢失</li>
 *     <li>优雅停止：关闭时等待环上任务全部执行完毕</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see CoreTriggerHandler
 * @see JobTriggerPoolHelper
 */
@Slf4j
@RequiredArgsConstructor
public class RingTriggerHandler implements TriggerHandler, Runnable {
    private static final Logger log = LoggerFactory.getLogger(RingTriggerHandler.class);

    private volatile boolean ringThreadToStop = false;
    private static final Map<Integer, List<Integer>> ringData = new ConcurrentHashMap<>();
    private Thread ringThread;

    private final JobProperties jobProperties;

    @Override
    public void start() {
        ringThread = new Thread(this);
        ringThread.setDaemon(true);
        ringThread.setName("job, ringThread");
        ringThread.start();
    }

    @Override
    public void stop() {
        ringThreadToStop = true;
        boolean hasRingData = false;
        if (!ringData.isEmpty()) {
            for (int second : ringData.keySet()) {
                List<Integer> tmpData = ringData.get(second);
                if (tmpData != null && !tmpData.isEmpty()) {
                    hasRingData = true;
                    break;
                }
            }
        }
        if (hasRingData) {
            try {
                TimeUnit.SECONDS.sleep(8);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }

        ringThreadToStop = true;
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        if (ringThread.getState() != Thread.State.TERMINATED) {
            ringThread.interrupt();
            try {
                ringThread.join();
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * 推送任务到时间环
     *
     * @param ringSecond 秒数
     * @param jobId      任务ID
     */
    public static void pushTimeRing(int ringSecond, int jobId) {
        List<Integer> ringItemData = ringData.get(ringSecond);
        if (ringItemData == null) {
            ringItemData = new ArrayList<>();
            ringData.put(ringSecond, ringItemData);
        }
        ringItemData.add(jobId);

        log.debug(">>>>>>>>>>> 推送到时间环: 秒数={}, 任务ID={}", ringSecond, ringItemData);
    }

    @Override
    public void run() {

        while (!ringThreadToStop) {

            // 对齐秒
            try {
                TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
            } catch (InterruptedException e) {
                if (!ringThreadToStop) {
                    log.error(e.getMessage(), e);
                }
            }

            try {
                // 当前秒数据
                List<Integer> ringItemData = new ArrayList<>();
                // 避免处理耗时太长，跨过刻度，向前校验一个刻度
                int nowSecond = Calendar.getInstance().get(Calendar.SECOND);
                for (int i = 0; i < 2; i++) {
                    List<Integer> tmpData = ringData.remove((nowSecond + 60 - i) % 60);
                    if (tmpData != null) {
                        ringItemData.addAll(tmpData);
                    }
                }

                // 时间环触发
                log.debug(">>>>>>>>>>> 时间环触发: 当前秒={}, 任务数={}", nowSecond, ringItemData.size());
                if (!ringItemData.isEmpty()) {
                    // 执行触发
                    for (int jobId : ringItemData) {
                        JobTriggerPoolHelper.trigger(jobId, TriggerTypeEnum.CRON, -1, null, null);
                    }
                    ringItemData.clear();
                }
            } catch (Exception e) {
                if (!ringThreadToStop) {
                    log.error(">>>>>>>>>>> 时间环线程异常: {}", e.getMessage(), e);
                }
            }
        }
        log.info(">>>>>>>>>>> 时间环线程已停止");
    }
}

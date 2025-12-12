package com.chua.starter.job.support.scheduler;

import com.chua.starter.job.support.JobProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 环形调度处理器
 * <p>
 * 基于时间环的秒级任务触发器
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Slf4j
@RequiredArgsConstructor
public class RingTriggerHandler implements TriggerHandler, Runnable {

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

        log.debug(">>>>>>>>>>> job, schedule push time-ring : " + ringSecond + " = " + List.of(ringItemData));
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
                log.debug(">>>>>>>>>>> job, time-ring beat : " + nowSecond + " = " + Collections.singletonList(ringItemData));
                if (!ringItemData.isEmpty()) {
                    // 执行触发
                    for (int jobId : ringItemData) {
                        JobTriggerPoolHelper.trigger(jobId, TriggerTypeEnum.CRON, -1, null, null);
                    }
                    ringItemData.clear();
                }
            } catch (Exception e) {
                if (!ringThreadToStop) {
                    log.error(">>>>>>>>>>> job, ringThread error:{}", e.getMessage(), e);
                }
            }
        }
        log.info(">>>>>>>>>>> job, ringThread stop");
    }
}

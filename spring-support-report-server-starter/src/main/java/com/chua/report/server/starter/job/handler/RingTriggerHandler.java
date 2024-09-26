package com.chua.report.server.starter.job.handler;

import com.chua.report.server.starter.job.TriggerTypeEnum;
import com.chua.report.server.starter.job.trigger.JobTriggerPoolHelper;
import com.chua.report.server.starter.properties.ReportJobProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 环形处理程序
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Slf4j
@RequiredArgsConstructor
public class RingTriggerHandler implements TriggerHandler, Runnable{
    private volatile boolean ringThreadToStop = false;
    private static final Map<Integer, List<Integer>> ringData = new ConcurrentHashMap<>();
    private Thread ringThread;

    private final ReportJobProperties reportJobProperties;

    @Override
    public void start() {
        // ring thread
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
                if (tmpData!=null && tmpData.size()>0) {
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

        // stop ring (wait job-in-memory stop)
        ringThreadToStop = true;
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        if (ringThread.getState() != Thread.State.TERMINATED){
            // interrupt and wait
            ringThread.interrupt();
            try {
                ringThread.join();
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
    public static void pushTimeRing(int ringSecond, int jobId){
        // push async ring
        List<Integer> ringItemData = ringData.get(ringSecond);
        if (ringItemData == null) {
            ringItemData = new ArrayList<Integer>();
            ringData.put(ringSecond, ringItemData);
        }
        ringItemData.add(jobId);

        log.debug(">>>>>>>>>>> job, schedule push time-ring : " + ringSecond + " = " + List.of(ringItemData));
    }

    @Override
    public void run() {

        while (!ringThreadToStop) {

            // align second
            try {
                TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
            } catch (InterruptedException e) {
                if (!ringThreadToStop) {
                    log.error(e.getMessage(), e);
                }
            }

            try {
                // second data
                List<Integer> ringItemData = new ArrayList<>();
                // 避免处理耗时太长，跨过刻度，向前校验一个刻度；
                int nowSecond = Calendar.getInstance().get(Calendar.SECOND);
                for (int i = 0; i < 2; i++) {
                    List<Integer> tmpData = ringData.remove( (nowSecond+60-i)%60 );
                    if (tmpData != null) {
                        ringItemData.addAll(tmpData);
                    }
                }

                // ring trigger
                log.debug(">>>>>>>>>>> job, time-ring beat : " + nowSecond + " = " + Collections.singletonList(ringItemData));
                if (ringItemData.size() > 0) {
                    // do trigger
                    for (int jobId: ringItemData) {
                        // do trigger
                        JobTriggerPoolHelper.trigger(jobId, TriggerTypeEnum.CRON, -1, null, null);
                    }
                    // clear
                    ringItemData.clear();
                }
            } catch (Exception e) {
                if (!ringThreadToStop) {
                    log.error(">>>>>>>>>>> job, ringThread error:{}", e);
                }
            }
        }
        log.info(">>>>>>>>>>> job, ringThread stop");
    }
}

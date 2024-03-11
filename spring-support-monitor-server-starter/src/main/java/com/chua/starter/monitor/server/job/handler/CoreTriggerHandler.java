package com.chua.starter.monitor.server.job.handler;

import com.chua.common.support.express.CronExpression;
import com.chua.starter.monitor.server.entity.MonitorJob;
import com.chua.starter.monitor.server.job.JobConfig;
import com.chua.starter.monitor.server.job.SchedulerTypeEnum;
import com.chua.starter.monitor.server.job.TriggerTypeEnum;
import com.chua.starter.monitor.server.job.lock.JobLock;
import com.chua.starter.monitor.server.job.scheduler.MisfireStrategyEnum;
import com.chua.starter.monitor.server.job.trigger.JobTriggerPoolHelper;
import com.chua.starter.monitor.server.properties.JobProperties;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.monitor.server.job.JobHelper.PRE_READ_MS;
import static com.chua.starter.monitor.server.job.handler.RingTriggerHandler.pushTimeRing;

/**
 * 堆芯处理机
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Slf4j
public class CoreTriggerHandler implements TriggerHandler, Runnable {
    private volatile boolean scheduleThreadToStop = false;


    private Thread scheduleThread;

    @Override
    public void start() {

        // schedule thread
        scheduleThread = new Thread(this);
        scheduleThread.setDaemon(true);
        scheduleThread.setName("job, coreThread");
        scheduleThread.start();
    }

    @Override
    public void stop() {
        scheduleThreadToStop = true;
        if (scheduleThread.getState() != Thread.State.TERMINATED) {
            // interrupt and wait
            scheduleThread.interrupt();
            try {
                scheduleThread.join();
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }

    }

    @Resource
    private JobProperties jobProperties;

    @Override
    public void run() {
        try {
            TimeUnit.MILLISECONDS.sleep(5000 - System.currentTimeMillis() % 1000);
        } catch (InterruptedException e) {
            if (!scheduleThreadToStop) {
                log.error(e.getMessage(), e);
            }
        }
        log.info(">>>>>>>>> init job admin scheduler success.");

        // pre-read count: treadpool-size * trigger-qps (each trigger cost 50ms, qps = 1000/50 = 20)
        int preReadCount = (JobConfig.getInstance().getTriggerPoolFastMax() + JobConfig.getInstance().getTriggerPoolSlowMax()) * 20;

        while (!scheduleThreadToStop) {

            // Scan Job
            long start = System.currentTimeMillis();

            boolean preReadSuc = true;
            JobLock jobLock = JobConfig.getInstance().lock("job");
            jobLock.lock();
            try {

                // tx start

                // 1、pre read
                long nowTime = System.currentTimeMillis();
                List<MonitorJob> scheduleList = JobConfig.getInstance().scheduleJobQuery(nowTime + PRE_READ_MS, preReadCount);
                if (scheduleList != null && scheduleList.size() > 0) {
                    // 2、push time-ring
                    for (MonitorJob jobInfo : scheduleList) {

                        // time-ring jump
                        if (nowTime > jobInfo.getJobTriggerNextTime() + PRE_READ_MS) {
                            // 2.1、trigger-expire > 5s：pass && make next-trigger-time
                            log.warn(">>>>>>>>>>> job, schedule misfire, jobId = " + jobInfo.getJobId());

                            // 1、misfire match
                            MisfireStrategyEnum misfireStrategyEnum = MisfireStrategyEnum.match(jobInfo.getJobExecuteMisfireStrategy(), MisfireStrategyEnum.DO_NOTHING);
                            if (MisfireStrategyEnum.FIRE_ONCE_NOW == misfireStrategyEnum) {
                                // FIRE_ONCE_NOW 》 trigger
                                JobTriggerPoolHelper.trigger(jobInfo.getJobId(), TriggerTypeEnum.MISFIRE, -1, null, null);
                                log.debug(">>>>>>>>>>> job, schedule push trigger : jobId = " + jobInfo.getJobId());
                            }

                            // 2、fresh next
                            refreshNextValidTime(jobInfo, new Date());

                        } else if (nowTime > jobInfo.getJobTriggerNextTime()) {
                            // 2.2、trigger-expire < 5s：direct-trigger && make next-trigger-time

                            // 1、trigger
                            JobTriggerPoolHelper.trigger(jobInfo.getJobId(), TriggerTypeEnum.CRON, -1, null, null);
                            log.debug(">>>>>>>>>>> job, schedule push trigger : jobId = " + jobInfo.getJobId());

                            // 2、fresh next
                            refreshNextValidTime(jobInfo, new Date());

                            // next-trigger-time in 5s, pre-read again
                            if (jobInfo.getJobStatus() == 1 && nowTime + PRE_READ_MS > jobInfo.getJobTriggerNextTime()) {

                                // 1、make ring second
                                int ringSecond = (int) ((jobInfo.getJobTriggerNextTime() / 1000) % 60);

                                // 2、push time ring
                                pushTimeRing(ringSecond, jobInfo.getJobId());

                                // 3、fresh next
                                refreshNextValidTime(jobInfo, new Date(jobInfo.getJobTriggerNextTime()));

                            }

                        } else {
                            // 2.3、trigger-pre-read：time-ring trigger && make next-trigger-time

                            // 1、make ring second
                            int ringSecond = (int) ((jobInfo.getJobTriggerNextTime() / 1000) % 60);

                            // 2、push time ring
                            pushTimeRing(ringSecond, jobInfo.getJobId());

                            // 3、fresh next
                            refreshNextValidTime(jobInfo, new Date(jobInfo.getJobTriggerNextTime()));

                        }

                    }

                    // 3、update trigger info
                    for (MonitorJob jobInfo : scheduleList) {
                        JobConfig.getInstance().scheduleUpdate(jobInfo);
                    }

                } else {
                    preReadSuc = false;
                }

                // tx stop


            } catch (Exception e) {
                if (!scheduleThreadToStop) {
                    log.error(">>>>>>>>>>> job, coreThread error:{}", e);
                }
            } finally {
                jobLock.unlock();
            }
            long cost = System.currentTimeMillis() - start;


            // Wait seconds, align second
            if (cost < 1000) {  // scan-overtime, not wait
                try {
                    // pre-read period: success > scan each second; fail > skip this period;
                    TimeUnit.MILLISECONDS.sleep((preReadSuc ? 1000 : PRE_READ_MS) - System.currentTimeMillis() % 1000);
                } catch (InterruptedException e) {
                    if (!scheduleThreadToStop) {
                        log.error(e.getMessage(), e);
                    }
                }
            }

        }

        log.info(">>>>>>>>>>> job, coreThread stop");
    }
    private void refreshNextValidTime(MonitorJob jobInfo, Date fromTime) throws Exception {
        Date nextValidTime = generateNextValidTime(jobInfo, fromTime);
        if (nextValidTime != null) {
            jobInfo.setJobTriggerLastTime(jobInfo.getJobTriggerNextTime());
            jobInfo.setJobTriggerNextTime(nextValidTime.getTime());
        } else {
            jobInfo.setJobStatus(0);
            jobInfo.setJobTriggerLastTime(0L);
            jobInfo.setJobTriggerNextTime(0L);
            log.warn(">>>>>>>>>>> job, refreshNextValidTime fail for job: jobId={}, scheduleType={}, scheduleConf={}",
                    jobInfo.getJobId(), jobInfo.getJobType(), jobInfo.getJobConf());
        }
    }

    // ---------------------- tools ----------------------
    public static Date generateNextValidTime(MonitorJob jobInfo, Date fromTime) throws Exception {
        SchedulerTypeEnum scheduleTypeEnum = SchedulerTypeEnum.match(jobInfo.getJobType(), null);
        if (SchedulerTypeEnum.CRON == scheduleTypeEnum) {
            Date nextValidTime = new CronExpression(jobInfo.getJobConf()).getNextValidTimeAfter(fromTime);
            return nextValidTime;
        } else if (SchedulerTypeEnum.FIXED == scheduleTypeEnum /*|| ScheduleTypeEnum.FIX_DELAY == scheduleTypeEnum*/) {
            return new Date(fromTime.getTime() + Integer.valueOf(jobInfo.getJobConf())*1000 );
        }
        return null;
    }
}

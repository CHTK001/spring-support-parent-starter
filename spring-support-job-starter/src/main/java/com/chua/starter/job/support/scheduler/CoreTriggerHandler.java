package com.chua.starter.job.support.scheduler;

import com.chua.advanced.support.express.CronExpression;
import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.entity.MonitorJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.job.support.scheduler.JobHelper.PRE_READ_MS;
import static com.chua.starter.job.support.scheduler.RingTriggerHandler.pushTimeRing;

/**
 * 核心调度处理器
 * <p>
 * 负责周期性扫描数据库中待执行的任务，根据任务的调度配置计算执行时间，
 * 并将任务推送到{@link RingTriggerHandler}时间环中等待触发。
 * </p>
 *
 * <h3>调度流程</h3>
 * <ol>
 *     <li>每秒扫描数据库，预读未来5秒内需要执行的任务</li>
 *     <li>判断任务的触发时间与当前时间的关系</li>
 *     <li>超时超过5秒：根据失效策略处理</li>
 *     <li>超时不超5秒：立即触发执行</li>
 *     <li>未超时：推送到时间环等待执行</li>
 * </ol>
 *
 * <h3>失效策略</h3>
 * <ul>
 *     <li>{@link MisfireStrategyEnum#DO_NOTHING} - 跳过本次执行，更新下次触发时间</li>
 *     <li>{@link MisfireStrategyEnum#FIRE_ONCE_NOW} - 立即触发一次，然后更新下次触发时间</li>
 * </ul>
 *
 * <h3>配置参数</h3>
 * <ul>
 *     <li>预读时间窗口：5秒 ({@link JobHelper#PRE_READ_MS})</li>
 *     <li>预读数量：(快速线程池大小 + 慢速线程池大小) * 20</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see RingTriggerHandler
 * @see MisfireStrategyEnum
 * @see SchedulerTrigger
 */
@Slf4j
@RequiredArgsConstructor
public class CoreTriggerHandler implements TriggerHandler, Runnable {

    private final JobProperties jobProperties;
    private volatile boolean scheduleThreadToStop = false;
    private Thread scheduleThread;

    @Override
    public void start() {
        scheduleThread = new Thread(this);
        scheduleThread.setDaemon(true);
        scheduleThread.setName("job, coreThread");
        scheduleThread.start();
    }

    @Override
    public void stop() {
        scheduleThreadToStop = true;
        if (scheduleThread.getState() != Thread.State.TERMINATED) {
            scheduleThread.interrupt();
            try {
                scheduleThread.join();
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void run() {
        try {
            TimeUnit.MILLISECONDS.sleep(5000 - System.currentTimeMillis() % 1000);
        } catch (InterruptedException e) {
            if (!scheduleThreadToStop) {
                log.error(e.getMessage(), e);
            }
        }
        log.info(">>>>>>>>> 任务调度器初始化成功");

        // 预读数量: 线程池大小 * 触发qps (每个触发耗时50ms, qps = 1000/50 = 20)
        int preReadCount = (JobConfig.getInstance().getTriggerPoolFastMax() + JobConfig.getInstance().getTriggerPoolSlowMax()) * 20;

        while (!scheduleThreadToStop) {

            long start = System.currentTimeMillis();
            boolean preReadSuc = true;
            JobConfig.JobLock jobLock = JobConfig.getInstance().lock("job");
            jobLock.lock();
            try {

                // 1、预读任务
                long nowTime = System.currentTimeMillis();
                List<MonitorJob> scheduleList = JobConfig.getInstance().scheduleJobQuery(nowTime + PRE_READ_MS, preReadCount);
                if (scheduleList != null && !scheduleList.isEmpty()) {
                    // 2、推送到时间环
                    for (MonitorJob jobInfo : scheduleList) {

                        if (nowTime > jobInfo.getJobTriggerNextTime() + PRE_READ_MS) {
                            // 2.1、触发超时 > 5s：跳过 && 更新下次触发时间
                            log.warn(">>>>>>>>>>> 任务触发超时, jobId={}", jobInfo.getJobId());

                            // 失效策略匹配
                            MisfireStrategyEnum misfireStrategyEnum = MisfireStrategyEnum.match(jobInfo.getJobExecuteMisfireStrategy(), MisfireStrategyEnum.DO_NOTHING);
                            if (MisfireStrategyEnum.FIRE_ONCE_NOW == misfireStrategyEnum) {
                                // 立即触发一次
                                JobTriggerPoolHelper.trigger(jobInfo.getJobId(), TriggerTypeEnum.MISFIRE, -1, null, null);
                                log.debug(">>>>>>>>>>> 任务失效补征触发, jobId={}", jobInfo.getJobId());
                            }

                            // 更新下次触发时间
                            refreshNextValidTime(jobInfo, new Date());

                        } else if (nowTime > jobInfo.getJobTriggerNextTime()) {
                            // 2.2、触发超时 < 5s：直接触发 && 更新下次触发时间

                            // 触发
                            JobTriggerPoolHelper.trigger(jobInfo.getJobId(), TriggerTypeEnum.CRON, -1, null, null);
                            log.debug(">>>>>>>>>>> 任务调度触发, jobId={}", jobInfo.getJobId());

                            // 更新下次触发时间
                            refreshNextValidTime(jobInfo, new Date());

                            // 下次触发时间在5s内，再次预读
                            if (jobInfo.getJobTriggerStatus() == 1 && nowTime + PRE_READ_MS > jobInfo.getJobTriggerNextTime()) {

                                // 计算时间环秒数
                                int ringSecond = (int) ((jobInfo.getJobTriggerNextTime() / 1000) % 60);

                                // 推送时间环
                                pushTimeRing(ringSecond, jobInfo.getJobId());

                                // 更新下次触发时间
                                refreshNextValidTime(jobInfo, new Date(jobInfo.getJobTriggerNextTime()));

                            }

                        } else {
                            // 2.3、预读：时间环触发 && 更新下次触发时间

                            // 计算时间环秒数
                            int ringSecond = (int) ((jobInfo.getJobTriggerNextTime() / 1000) % 60);

                            // 推送时间环
                            pushTimeRing(ringSecond, jobInfo.getJobId());

                            // 更新下次触发时间
                            refreshNextValidTime(jobInfo, new Date(jobInfo.getJobTriggerNextTime()));

                        }

                    }

                    // 3、更新触发信息
                    for (MonitorJob jobInfo : scheduleList) {
                        JobConfig.getInstance().scheduleUpdate(jobInfo);
                    }

                } else {
                    preReadSuc = false;
                }

            } catch (Exception e) {
                if (!scheduleThreadToStop) {
                    log.error(">>>>>>>>>>> 核心调度线程异常: {}", e.getMessage(), e);
                }
            } finally {
                jobLock.unlock();
            }
            long cost = System.currentTimeMillis() - start;

            // 等待，对齐秒
            if (cost < 1000) {
                try {
                    TimeUnit.MILLISECONDS.sleep((preReadSuc ? 1000 : PRE_READ_MS) - System.currentTimeMillis() % 1000);
                } catch (InterruptedException e) {
                    if (!scheduleThreadToStop) {
                        log.error(e.getMessage(), e);
                    }
                }
            }

        }

        log.info(">>>>>>>>>>> 核心调度线程已停止");
    }

    private void refreshNextValidTime(MonitorJob jobInfo, Date fromTime) throws Exception {
        Date nextValidTime = generateNextValidTime(jobInfo, fromTime);
        if (nextValidTime != null) {
            jobInfo.setJobTriggerLastTime(jobInfo.getJobTriggerNextTime());
            jobInfo.setJobTriggerNextTime(nextValidTime.getTime());
        } else {
            jobInfo.setJobTriggerStatus(0);
            jobInfo.setJobTriggerLastTime(0L);
            jobInfo.setJobTriggerNextTime(0L);
            log.warn(">>>>>>>>>>> 更新下次触发时间失败, jobId={}, 调度类型={}, 调度配置={}",
                    jobInfo.getJobId(), jobInfo.getJobScheduleType(), jobInfo.getJobScheduleTime());
        }
    }

    public static Date generateNextValidTime(MonitorJob jobInfo, Date fromTime) throws Exception {
        SchedulerTypeEnum scheduleTypeEnum = SchedulerTypeEnum.match(jobInfo.getJobScheduleType(), null);
        if (SchedulerTypeEnum.CRON == scheduleTypeEnum) {
            return new CronExpression(jobInfo.getJobScheduleTime()).getNextValidTimeAfter(fromTime);
        } else if (SchedulerTypeEnum.FIXED == scheduleTypeEnum) {
            return new Date(fromTime.getTime() + Integer.parseInt(jobInfo.getJobScheduleTime()) * 1000L);
        }
        return null;
    }
}

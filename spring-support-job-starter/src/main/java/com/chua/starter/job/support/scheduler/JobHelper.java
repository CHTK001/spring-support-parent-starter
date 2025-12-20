package com.chua.starter.job.support.scheduler;

import com.chua.advanced.support.express.CronExpression;
import com.chua.starter.job.support.entity.MonitorJob;

import java.util.Date;

/**
 * 任务调度辅助工具类
 * <p>
 * 提供任务调度相关的工具方法，主要包括下次执行时间的计算。
 * </p>
 *
 * <h3>主要功能</h3>
 * <ul>
 *     <li>计算CRON表达式的下次执行时间</li>
 *     <li>计算固定频率任务的下次执行时间</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see CoreTriggerHandler
 * @see SchedulerTypeEnum
 */
public class JobHelper {
    
    /** 
     * 预读时间窗口，单位毫秒
     * 调度线程会提前读取未来5秒内需要执行的任务
     */
    public static final long PRE_READ_MS = 5000;

    /**
     * 生成下一个有效时间
     *
     * @param jobInfo  任务信息
     * @param fromTime 从时间
     * @return {@link Date}
     * @throws Exception 例外
     */
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

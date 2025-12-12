package com.chua.starter.job.support.scheduler;

import com.chua.advanced.support.express.CronExpression;
import com.chua.starter.job.support.entity.MonitorJob;

import java.util.Date;

/**
 * 工作助手
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
public class JobHelper {
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

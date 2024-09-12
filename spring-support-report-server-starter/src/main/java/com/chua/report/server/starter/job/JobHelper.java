package com.chua.report.server.starter.job;

import com.chua.common.support.express.CronExpression;
import com.chua.report.server.starter.entity.MonitorJob;

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
        SchedulerTypeEnum scheduleTypeEnum = SchedulerTypeEnum.match(jobInfo.getJobType(), null);
        if (SchedulerTypeEnum.CRON == scheduleTypeEnum) {
            return new CronExpression(jobInfo.getJobConf()).getNextValidTimeAfter(fromTime);
        } else if (SchedulerTypeEnum.FIXED == scheduleTypeEnum /*|| ScheduleTypeEnum.FIX_DELAY == scheduleTypeEnum*/) {
            return new Date(fromTime.getTime() + Integer.parseInt(jobInfo.getJobConf()) * 1000L);
        }
        return null;
    }
}

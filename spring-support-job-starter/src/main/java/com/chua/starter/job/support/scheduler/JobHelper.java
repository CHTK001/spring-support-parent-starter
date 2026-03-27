package com.chua.starter.job.support.scheduler;

import com.chua.extension.support.express.CronExpression;
import com.chua.starter.job.support.entity.SysJob;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

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

    private static final List<DateTimeFormatter> ABSOLUTE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );
    
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
    public static Date generateNextValidTime(SysJob jobInfo, Date fromTime) throws Exception {
        SchedulerTypeEnum scheduleTypeEnum = SchedulerTypeEnum.match(jobInfo.getJobScheduleType(), null);
        if (SchedulerTypeEnum.CRON == scheduleTypeEnum) {
            try {
                return new CronExpression(jobInfo.getJobScheduleTime()).getNextValidTimeAfter(fromTime);
            } catch (ParseException e) {
                throw new Exception("解析CRON表达式失败: " + jobInfo.getJobScheduleTime(), e);
            }
        } else if (SchedulerTypeEnum.FIXED == scheduleTypeEnum) {
            return new Date(fromTime.getTime() + Integer.parseInt(jobInfo.getJobScheduleTime()) * 1000L);
        } else if (SchedulerTypeEnum.FIXED_MS == scheduleTypeEnum) {
            return new Date(fromTime.getTime() + Long.parseLong(jobInfo.getJobScheduleTime()));
        } else if (SchedulerTypeEnum.DELAY == scheduleTypeEnum) {
            if (jobInfo.getJobTriggerLastTime() != null && jobInfo.getJobTriggerLastTime() > 0) {
                return null;
            }
            return new Date(fromTime.getTime() + Long.parseLong(jobInfo.getJobScheduleTime()));
        } else if (SchedulerTypeEnum.AT == scheduleTypeEnum) {
            long targetTime = parseAbsoluteTime(jobInfo.getJobScheduleTime());
            return targetTime > fromTime.getTime() ? new Date(targetTime) : null;
        } else if (SchedulerTypeEnum.NONE == scheduleTypeEnum) {
            return null;
        }
        return null;
    }

    private static long parseAbsoluteTime(String scheduleTime) throws Exception {
        if (scheduleTime == null || scheduleTime.trim().isEmpty()) {
            throw new IllegalArgumentException("绝对时间调度配置不能为空");
        }
        String candidate = scheduleTime.trim();
        if (candidate.matches("^\\d{13}$")) {
            return Long.parseLong(candidate);
        }
        if (candidate.matches("^\\d{10}$")) {
            return Long.parseLong(candidate) * 1000L;
        }
        for (DateTimeFormatter formatter : ABSOLUTE_TIME_FORMATTERS) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(candidate, formatter);
                return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // continue
            }
        }
        try {
            return Instant.parse(candidate).toEpochMilli();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("解析绝对时间失败: " + candidate, e);
        }
    }
}

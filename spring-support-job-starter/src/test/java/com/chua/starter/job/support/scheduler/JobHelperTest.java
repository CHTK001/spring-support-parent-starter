package com.chua.starter.job.support.scheduler;

import com.chua.starter.job.support.entity.SysJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JobHelperTest {

    @Test
    void shouldSupportAllBuiltInScheduleTypes() throws Exception {
        Date fromTime = new Date(1_700_000_000_000L);

        SysJob cronJob = new SysJob();
        cronJob.setJobScheduleType(SchedulerTypeEnum.CRON.name());
        cronJob.setJobScheduleTime("0/5 * * * * ?");
        assertThat(JobHelper.generateNextValidTime(cronJob, fromTime))
                .isNotNull()
                .matches(next -> next.getTime() > fromTime.getTime());

        SysJob fixedJob = new SysJob();
        fixedJob.setJobScheduleType(SchedulerTypeEnum.FIXED.name());
        fixedJob.setJobScheduleTime("30");
        assertThat(JobHelper.generateNextValidTime(fixedJob, fromTime).getTime())
                .isEqualTo(fromTime.getTime() + 30_000L);

        SysJob fixedMsJob = new SysJob();
        fixedMsJob.setJobScheduleType(SchedulerTypeEnum.FIXED_MS.name());
        fixedMsJob.setJobScheduleTime("1500");
        assertThat(JobHelper.generateNextValidTime(fixedMsJob, fromTime).getTime())
                .isEqualTo(fromTime.getTime() + 1_500L);

        SysJob delayJob = new SysJob();
        delayJob.setJobScheduleType(SchedulerTypeEnum.DELAY.name());
        delayJob.setJobScheduleTime("2500");
        assertThat(JobHelper.generateNextValidTime(delayJob, fromTime).getTime())
                .isEqualTo(fromTime.getTime() + 2_500L);
        delayJob.setJobTriggerLastTime(fromTime.getTime());
        assertThat(JobHelper.generateNextValidTime(delayJob, fromTime)).isNull();

        LocalDateTime atTime = LocalDateTime.of(2030, 1, 2, 3, 4, 5);
        SysJob atJob = new SysJob();
        atJob.setJobScheduleType(SchedulerTypeEnum.AT.name());
        atJob.setJobScheduleTime(atTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        assertThat(JobHelper.generateNextValidTime(atJob, new Date(1_700_000_000_000L)).getTime())
                .isEqualTo(atTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        SysJob noneJob = new SysJob();
        noneJob.setJobScheduleType(SchedulerTypeEnum.NONE.name());
        noneJob.setJobScheduleTime(null);
        assertThat(JobHelper.generateNextValidTime(noneJob, fromTime)).isNull();
    }
}

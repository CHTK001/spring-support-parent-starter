package com.chua.report.server.starter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.report.server.starter.entity.MonitorJobLog;
import com.chua.report.server.starter.job.pojo.JobStatistic;
import com.chua.report.server.starter.job.pojo.JobStatisticResult;
import com.chua.report.server.starter.mapper.MonitorJobLogMapper;
import com.chua.report.server.starter.service.MonitorJobLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 监控日志
 * @author Administrator
 */
@Service
public class MonitorJobLogServiceImpl extends ServiceImpl<MonitorJobLogMapper, MonitorJobLog> implements MonitorJobLogService {

    @Override
    public JobStatisticResult time(MonitorJobLog entity) {
        LocalDateTime startDate = entity.getStartDate();
        LocalDateTime endDate = entity.getEndDate();
        if(null == startDate || null == endDate) {
            return new JobStatisticResult();
        }
        List<JobStatistic> time = baseMapper.time(entity);
        JobStatisticResult result = new JobStatisticResult();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Long> tpl = new HashMap<>();
        for (JobStatistic statistic : time) {
            tpl.put(statistic.getJobLogTriggerDate().format(formatter) +
                    ("00000".equals(statistic.getJobLogTriggerCode()) ? "SUCCESS": "FAILURE") , statistic.getCnt());
        }
        List<Long> success = new LinkedList<>();
        List<Long> failure = new LinkedList<>();

        LocalDateTime start = startDate;
        while (start.isBefore(endDate) || start.equals(endDate)) {
            String format = start.format(formatter);
            success.add(tpl.getOrDefault(format + "SUCCESS", 0L));
            failure.add(tpl.getOrDefault(format + "FAILURE", 0L));
            start = start.plusDays(1);
        }

        result.setSuccessCount(success.toArray(new Long[0]));
        result.setFailureCount(failure.toArray(new Long[0]));
        return result;
    }

    @Override
    public Boolean clear(MonitorJobLog entity) {
        LocalDateTime startDate = entity.getStartDate();
        LocalDateTime endDate = entity.getEndDate();
        if(null == startDate || null == endDate) {
            return true;
        }
        baseMapper.delete(Wrappers.<MonitorJobLog>lambdaUpdate()
                .between(MonitorJobLog::getJobLogTriggerDate, startDate, endDate)
        );
        return true;
    }
}

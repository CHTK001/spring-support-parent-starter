package com.chua.report.server.starter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.date.DateTime;
import com.chua.report.server.starter.entity.MonitorProxyLimitLog;
import com.chua.report.server.starter.mapper.MonitorProxyLimitLogMapper;
import com.chua.report.server.starter.pojo.LogStatistic;
import com.chua.report.server.starter.pojo.MonitorProxyLimitLogResult;
import com.chua.report.server.starter.service.MonitorProxyLimitLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 *
 *
 * @since 2024/6/21 
 * @author CH
 */
@Service
public class MonitorProxyLimitLogServiceImpl extends ServiceImpl<MonitorProxyLimitLogMapper, MonitorProxyLimitLog> implements MonitorProxyLimitLogService{

    @Override
    public boolean delete(Integer limitMonth) {
        return baseMapper.delete(Wrappers.<MonitorProxyLimitLog>lambdaUpdate()
                .gt(MonitorProxyLimitLog::getCreateTime, LocalDate.now().minusMonths(limitMonth).plusDays(1))
                .lt(MonitorProxyLimitLog::getCreateTime, LocalDate.now().plusDays(1))
        ) >= 0;
    }

    @Override
    public Page<MonitorProxyLimitLogResult> pageForLog(Page<MonitorProxyLimitLog> page, MonitorProxyLimitLog entity) {
        return baseMapper.pageForLog(page, entity);
    }

    @Override
    public LogStatistic listForGeo(Page<MonitorProxyLimitLog> page, MonitorProxyLimitLog entity) {
        List<MonitorProxyLimitLogResult> monitorProxyLimitLogs = baseMapper.listForGeo(page, entity);
        LogStatistic logStatistic = new LogStatistic();
        logStatistic.setXAxis(monitorProxyLimitLogs.stream().map(MonitorProxyLimitLogResult::getCreateTime).map(DateTime::of).map(DateTime::toStandard).toArray(String[]::new));
        logStatistic.setAllowAxis(monitorProxyLimitLogs.stream().map(MonitorProxyLimitLogResult::getAllowCount).toArray(Integer[]::new));
        logStatistic.setDenyAxis(monitorProxyLimitLogs.stream().map(MonitorProxyLimitLogResult::getDenyCount).toArray(Integer[]::new));
        logStatistic.setWarnAxis(monitorProxyLimitLogs.stream().map(MonitorProxyLimitLogResult::getWarnCount).toArray(Integer[]::new));
        return logStatistic;
    }
}

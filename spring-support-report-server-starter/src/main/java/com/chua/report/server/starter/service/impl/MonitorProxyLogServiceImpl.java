package com.chua.report.server.starter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.date.DateTime;
import com.chua.report.server.starter.pojo.LogStatistic;
import com.chua.report.server.starter.pojo.MonitorProxyLimitLogResult;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.report.server.starter.entity.MonitorProxyLog;
import com.chua.report.server.starter.mapper.MonitorProxyLogMapper;
import com.chua.report.server.starter.service.MonitorProxyLogService;
/**
 *
 * @since 2024/9/16
 * @author CH    
 */
@Service
public class MonitorProxyLogServiceImpl extends ServiceImpl<MonitorProxyLogMapper, MonitorProxyLog> implements MonitorProxyLogService{

    @Override
    public boolean delete(Integer limitMonth) {
        return baseMapper.delete(Wrappers.<MonitorProxyLog>lambdaUpdate()
                .gt(MonitorProxyLog::getCreateTime, LocalDate.now().minusMonths(limitMonth).plusDays(1))
                .lt(MonitorProxyLog::getCreateTime, LocalDate.now().plusDays(1))
        ) >= 0;
    }

    @Override
    public Page<MonitorProxyLimitLogResult> pageForLog(Page<MonitorProxyLog> page, MonitorProxyLog entity) {
        return baseMapper.pageForLog(page, entity);
    }

    @Override
    public LogStatistic listForGeo(Page<MonitorProxyLog> page, MonitorProxyLog entity) {
        List<MonitorProxyLimitLogResult> monitorProxyLimitLogs = baseMapper.listForGeo(page, entity);
        LogStatistic logStatistic = new LogStatistic();
        logStatistic.setXAxis(monitorProxyLimitLogs.stream().map(MonitorProxyLimitLogResult::getCreateTime).map(DateTime::of).map(DateTime::toStandard).toArray(String[]::new));
        logStatistic.setAllowAxis(monitorProxyLimitLogs.stream().map(MonitorProxyLimitLogResult::getAllowCount).toArray(Integer[]::new));
        logStatistic.setDenyAxis(monitorProxyLimitLogs.stream().map(MonitorProxyLimitLogResult::getDenyCount).toArray(Integer[]::new));
        logStatistic.setWarnAxis(monitorProxyLimitLogs.stream().map(MonitorProxyLimitLogResult::getWarnCount).toArray(Integer[]::new));
        return logStatistic;
    }
}

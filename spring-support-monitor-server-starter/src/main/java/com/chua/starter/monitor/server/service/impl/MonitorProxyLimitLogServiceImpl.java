package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitLog;
import com.chua.starter.monitor.server.mapper.MonitorProxyLimitLogMapper;
import com.chua.starter.monitor.server.pojo.MonitorProxyLimitLogResult;
import com.chua.starter.monitor.server.service.MonitorProxyLimitLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
                .lt(MonitorProxyLimitLog::getCreateTime, LocalDate.now().minusMonths(limitMonth).plusDays(1))) >= 0;
    }

    @Override
    public Page<MonitorProxyLimitLogResult> pageForLog(Page<MonitorProxyLimitLog> page, MonitorProxyLimitLog entity) {
        return baseMapper.pageForLog(page, entity);
    }
}

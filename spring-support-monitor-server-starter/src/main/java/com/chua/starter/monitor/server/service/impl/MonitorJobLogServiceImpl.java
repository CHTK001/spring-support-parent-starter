package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.entity.MonitorJobLog;
import com.chua.starter.monitor.server.mapper.MonitorJobLogMapper;
import com.chua.starter.monitor.server.service.MonitorJobLogService;
import org.springframework.stereotype.Service;
@Service
public class MonitorJobLogServiceImpl extends ServiceImpl<MonitorJobLogMapper, MonitorJobLog> implements MonitorJobLogService{

}

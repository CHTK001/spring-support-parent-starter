package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.entity.MonitorLog;
import com.chua.starter.monitor.server.mapper.MonitorLogMapper;
import com.chua.starter.monitor.server.service.MonitorLogService;
import org.springframework.stereotype.Service;
@Service
public class MonitorLogServiceImpl extends ServiceImpl<MonitorLogMapper, MonitorLog> implements MonitorLogService{

}

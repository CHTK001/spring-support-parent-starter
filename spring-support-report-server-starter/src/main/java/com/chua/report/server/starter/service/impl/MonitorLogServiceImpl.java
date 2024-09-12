package com.chua.report.server.starter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.report.server.starter.entity.MonitorLog;
import com.chua.report.server.starter.mapper.MonitorLogMapper;
import com.chua.report.server.starter.service.MonitorLogService;
import org.springframework.stereotype.Service;

/**
 * 监控日志
 *
 * @author Administrator
 */
@Service
public class MonitorLogServiceImpl extends ServiceImpl<MonitorLogMapper, MonitorLog> implements MonitorLogService {

}

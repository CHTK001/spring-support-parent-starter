package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.entity.MonitorConfig;
import com.chua.starter.monitor.server.mapper.MonitorConfigMapper;
import com.chua.starter.monitor.server.service.MonitorConfigService;
import org.springframework.stereotype.Service;
@Service
public class MonitorConfigServiceImpl extends ServiceImpl<MonitorConfigMapper, MonitorConfig> implements MonitorConfigService{

}

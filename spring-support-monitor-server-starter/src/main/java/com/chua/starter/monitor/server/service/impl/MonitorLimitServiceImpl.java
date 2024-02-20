package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.entity.MonitorLimit;
import com.chua.starter.monitor.server.mapper.MonitorLimitMapper;
import com.chua.starter.monitor.server.service.MonitorLimitService;
import org.springframework.stereotype.Service;
@Service
public class MonitorLimitServiceImpl extends ServiceImpl<MonitorLimitMapper, MonitorLimit> implements MonitorLimitService{

}

package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.entity.MonitorApp;
import com.chua.starter.monitor.server.mapper.MonitorAppMapper;
import com.chua.starter.monitor.server.service.MonitorAppService;
import org.springframework.stereotype.Service;
@Service
public class MonitorAppServiceImpl extends ServiceImpl<MonitorAppMapper, MonitorApp> implements MonitorAppService{

}

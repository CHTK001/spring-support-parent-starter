package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.entity.MonitorJob;
import com.chua.starter.monitor.server.mapper.MonitorJobMapper;
import com.chua.starter.monitor.server.service.MonitorJobService;
import org.springframework.stereotype.Service;
@Service
public class MonitorJobServiceImpl extends ServiceImpl<MonitorJobMapper, MonitorJob> implements MonitorJobService{

}

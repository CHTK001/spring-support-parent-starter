package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.entity.MonitorMybatis;
import com.chua.starter.monitor.server.mapper.MonitorMybatisMapper;
import com.chua.starter.monitor.server.service.MonitorMybatisService;
import org.springframework.stereotype.Service;
@Service
public class MonitorMybatisServiceImpl extends ServiceImpl<MonitorMybatisMapper, MonitorMybatis> implements MonitorMybatisService{

}

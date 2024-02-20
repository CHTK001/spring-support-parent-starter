package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.entity.MonitorPatch;
import com.chua.starter.monitor.server.mapper.MonitorPatchMapper;
import com.chua.starter.monitor.server.service.MonitorPatchService;
import org.springframework.stereotype.Service;
@Service
public class MonitorPatchServiceImpl extends ServiceImpl<MonitorPatchMapper, MonitorPatch> implements MonitorPatchService{

}

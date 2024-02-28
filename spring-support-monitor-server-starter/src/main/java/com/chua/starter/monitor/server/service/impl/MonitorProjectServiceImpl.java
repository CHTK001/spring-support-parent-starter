package com.chua.starter.monitor.server.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.mapper.MonitorProjectMapper;
import com.chua.starter.monitor.server.entity.MonitorProject;
import com.chua.starter.monitor.server.service.MonitorProjectService;
@Service
public class MonitorProjectServiceImpl extends ServiceImpl<MonitorProjectMapper, MonitorProject> implements MonitorProjectService{

}

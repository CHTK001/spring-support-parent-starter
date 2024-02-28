package com.chua.starter.monitor.server.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.mapper.MonitorProjectVersionMapper;
import com.chua.starter.monitor.server.entity.MonitorProjectVersion;
import com.chua.starter.monitor.server.service.MonitorProjectVersionService;
@Service
public class MonitorProjectVersionServiceImpl extends ServiceImpl<MonitorProjectVersionMapper, MonitorProjectVersion> implements MonitorProjectVersionService{

}

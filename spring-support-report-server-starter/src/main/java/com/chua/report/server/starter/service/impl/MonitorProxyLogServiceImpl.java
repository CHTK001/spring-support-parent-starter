package com.chua.report.server.starter.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.report.server.starter.entity.MonitorProxyLog;
import com.chua.report.server.starter.mapper.MonitorProxyLogMapper;
import com.chua.report.server.starter.service.MonitorProxyLogService;
/**
 *
 * @since 2024/9/16
 * @author CH    
 */
@Service
public class MonitorProxyLogServiceImpl extends ServiceImpl<MonitorProxyLogMapper, MonitorProxyLog> implements MonitorProxyLogService{

}

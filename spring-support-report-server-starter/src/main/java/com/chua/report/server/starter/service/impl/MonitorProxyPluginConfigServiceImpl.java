package com.chua.report.server.starter.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.report.server.starter.entity.MonitorProxyPluginConfig;
import com.chua.report.server.starter.mapper.MonitorProxyPluginConfigMapper;
import com.chua.report.server.starter.service.MonitorProxyPluginConfigService;
/**
 *
 * @since 2024/9/16
 * @author CH    
 */
@Service
public class MonitorProxyPluginConfigServiceImpl extends ServiceImpl<MonitorProxyPluginConfigMapper, MonitorProxyPluginConfig> implements MonitorProxyPluginConfigService{

}

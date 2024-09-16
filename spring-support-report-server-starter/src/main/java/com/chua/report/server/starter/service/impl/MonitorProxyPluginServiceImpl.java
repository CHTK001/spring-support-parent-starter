package com.chua.report.server.starter.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.report.server.starter.entity.MonitorProxyPluginConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.report.server.starter.mapper.MonitorProxyPluginMapper;
import com.chua.report.server.starter.entity.MonitorProxyPlugin;
import com.chua.report.server.starter.service.MonitorProxyPluginService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 *
 * @since 2024/9/16
 * @author CH    
 */
@Service
@RequiredArgsConstructor
public class MonitorProxyPluginServiceImpl extends ServiceImpl<MonitorProxyPluginMapper, MonitorProxyPlugin> implements MonitorProxyPluginService{

}
package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.protocol.boot.BootOption;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.Protocol;
import com.chua.common.support.protocol.boot.ProtocolClient;
import com.chua.common.support.protocol.options.ServerOption;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.Preconditions;
import com.chua.starter.monitor.properties.MonitorProperties;
import com.chua.starter.monitor.properties.MonitorProtocolProperties;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorApp;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.server.mapper.MonitorAppMapper;
import com.chua.starter.monitor.server.properties.MonitorServerProperties;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.mybatis.entity.PageRequest;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class MonitorAppServiceImpl extends ServiceImpl<MonitorAppMapper, MonitorApp> implements MonitorAppService{
    @Resource
    private MonitorServerFactory monitorServerFactory;
    @Resource
    private MonitorServerProperties monitorServerProperties;

}

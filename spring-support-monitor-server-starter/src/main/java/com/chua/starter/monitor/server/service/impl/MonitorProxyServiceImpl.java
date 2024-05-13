package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.Server;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.netty.support.proxy.filter.AsyncHttpRoutingGatewayFilter;
import com.chua.netty.support.proxy.filter.AsyncWebSocketRoutingGatewayFilter;
import com.chua.starter.monitor.server.entity.MonitorProxy;
import com.chua.starter.monitor.server.mapper.MonitorProxyMapper;
import com.chua.starter.monitor.server.service.MonitorProxyService;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 *
 * @since 2024/5/13 
 * @author CH
 */
@Service
public class MonitorProxyServiceImpl extends ServiceImpl<MonitorProxyMapper, MonitorProxy> implements MonitorProxyService{

    private static final Map<String, Server> SERVER_MAP = new ConcurrentHashMap<>();
    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public ReturnResult<Boolean> start(MonitorProxy monitorProxy) {
        if(ObjectUtils.isEmpty(monitorProxy.getProxyHost()) || ObjectUtils.isEmpty(monitorProxy.getProxyPort())) {
            return ReturnResult.error("代理地址不能为空");
        }

        String key = createKey(monitorProxy);
        if(SERVER_MAP.containsKey(key)) {
            return ReturnResult.error("代理已启动");
        }

        return transactionTemplate.execute(it -> {
            monitorProxy.setProxyStatus(1);
            int i = baseMapper.updateById(monitorProxy);
            if(i > 0) {
                Server server = createServer(monitorProxy);
                try {
                    server.start();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                SERVER_MAP.put(key, server);
                return ReturnResult.success();
            }
            return ReturnResult.error("代理启动失败");
        });

    }

    @Override
    public ReturnResult<Boolean> stop(MonitorProxy monitorProxy) {
        if(ObjectUtils.isEmpty(monitorProxy.getProxyHost()) || ObjectUtils.isEmpty(monitorProxy.getProxyPort())) {
            return ReturnResult.error("代理地址不能为空");
        }

        String key = createKey(monitorProxy);
        if(!SERVER_MAP.containsKey(key) && 0 == monitorProxy.getProxyStatus()) {
            return ReturnResult.error("代理已停止");
        }

        monitorProxy.setProxyStatus(0);
        return transactionTemplate.execute(it -> {
            int i = baseMapper.updateById(monitorProxy);
            if(i > 0) {
                try {
                    Server server = SERVER_MAP.get(key);
                    if(null != server) {
                        server.stop();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                SERVER_MAP.remove(key);
                return ReturnResult.success();
            }

            return ReturnResult.error("代理停止失败");
        });
    }

    private Server createServer(MonitorProxy monitorProxy) {
        ServiceDiscovery serviceDiscovery = applicationContext.getBean(ServiceDiscovery.class);
        if(null == serviceDiscovery) {
            throw new RuntimeException("未找到服务发现");
        }
        Server server = Server.create("proxy", monitorProxy.getProxyPort());
        server.addDefinition(serviceDiscovery);
        server.addFilter(AsyncHttpRoutingGatewayFilter.class);
        server.addFilter(AsyncWebSocketRoutingGatewayFilter.class);
        return server;
    }

    private String createKey(MonitorProxy monitorProxy) {
        return monitorProxy.getProxyHost() + ":" + monitorProxy.getProxyPort();
    }
}

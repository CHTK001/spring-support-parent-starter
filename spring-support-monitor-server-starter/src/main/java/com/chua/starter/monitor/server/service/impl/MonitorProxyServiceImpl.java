package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.chain.filter.ChainFilter;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.log.Log;
import com.chua.common.support.log.Slf4jLog;
import com.chua.common.support.objects.constant.FilterOrderType;
import com.chua.common.support.objects.definition.FilterConfigTypeDefinition;
import com.chua.common.support.protocol.Server;
import com.chua.common.support.protocol.options.ServerSetting;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.monitor.server.entity.MonitorProxy;
import com.chua.starter.monitor.server.entity.MonitorProxyConfig;
import com.chua.starter.monitor.server.entity.MonitorProxyPlugin;
import com.chua.starter.monitor.server.entity.MonitorProxyPluginConfig;
import com.chua.starter.monitor.server.log.WebLog;
import com.chua.starter.monitor.server.mapper.MonitorProxyMapper;
import com.chua.starter.monitor.server.service.MonitorProxyConfigService;
import com.chua.starter.monitor.server.service.MonitorProxyPluginConfigService;
import com.chua.starter.monitor.server.service.MonitorProxyPluginService;
import com.chua.starter.monitor.server.service.MonitorProxyService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 *
 * @since 2024/5/13 
 * @author CH
 */
@Service
public class MonitorProxyServiceImpl extends ServiceImpl<MonitorProxyMapper, MonitorProxy> implements MonitorProxyService, InitializingBean {

    private static final Map<String, Server> SERVER_MAP = new ConcurrentHashMap<>();
    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private MonitorProxyConfigService monitorProxyConfigService;

    @Resource
    private MonitorProxyPluginService monitorProxyPluginService;
    @Resource
    private MonitorProxyPluginConfigService monitorProxyPluginConfigService;

    @Override
    public ReturnResult<Boolean> start(MonitorProxy monitorProxy) {
        if(ObjectUtils.isEmpty(monitorProxy.getProxyHost()) || ObjectUtils.isEmpty(monitorProxy.getProxyPort())) {
            return ReturnResult.error("代理地址不能为空");
        }

        String key = createKey(monitorProxy);
        if(SERVER_MAP.containsKey(key)) {
            return ReturnResult.error("代理已启动, 请刷新页面");
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

    @SuppressWarnings("unchecked")
    private Server createServer(MonitorProxy monitorProxy) {
        ServiceDiscovery serviceDiscovery = null;
        try {
            serviceDiscovery = applicationContext.getBean(ServiceDiscovery.class);
        } catch (BeansException ignored) {
        }
        if(null == serviceDiscovery) {
            throw new RuntimeException("未找到服务发现");
        }
        List<MonitorProxyConfig> list = monitorProxyConfigService.list(Wrappers.<MonitorProxyConfig>lambdaQuery().eq(MonitorProxyConfig::getProxyId, monitorProxy.getProxyId()));
        String proxyProtocol = monitorProxy.getProxyProtocol();
        Log log = new Slf4jLog();
        for (MonitorProxyConfig monitorProxyConfig : list) {
            if("open-log".equals(monitorProxyConfig.getConfigName()) && "true".equals(monitorProxyConfig.getConfigValue())) {
                log = new WebLog(monitorProxy.getProxyId() + "");
                SpringBeanUtils.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(log);
                break;
            }
        }
        ServerSetting serverSetting = ServerSetting.builder().log(log).port(monitorProxy.getProxyPort()).build();
        Server server = Server.create(proxyProtocol, serverSetting);
        for (MonitorProxyConfig monitorProxyConfig : list) {
            server.addConfig(monitorProxyConfig.getConfigName(), monitorProxyConfig.getConfigValue());
        }
        List<MonitorProxyPlugin> list1 = monitorProxyPluginService.list(Wrappers.<MonitorProxyPlugin>lambdaQuery()
                .eq(MonitorProxyPlugin::getProxyId, monitorProxy.getProxyId())
                .orderByDesc(MonitorProxyPlugin::getPluginSort)
        );

        if(CollectionUtils.isEmpty(list1)) {
            throw new RuntimeException("未找到代理插件");
        }
        List<MonitorProxyPluginConfig> list2 = monitorProxyPluginConfigService.list(Wrappers.<MonitorProxyPluginConfig>lambdaQuery().eq(MonitorProxyPluginConfig::getProxyId, monitorProxy.getProxyId()));
        Map<String, List<MonitorProxyPluginConfig>> configMap = new HashMap<>(list2.size());
        for (MonitorProxyPluginConfig config : list2) {
            configMap.computeIfAbsent(config.getPluginName() + config.getPluginSort(), it -> new LinkedList<>()).add(config);
        }

        for (MonitorProxyPlugin monitorProxyPlugin : list1) {
            String key = monitorProxyPlugin.getPluginName() + monitorProxyPlugin.getPluginSort();
            List<MonitorProxyPluginConfig> monitorProxyPluginConfigList = configMap.get(key);
            ServiceDefinition serviceDefinition = ServiceProvider.of(ChainFilter.class).getDefinitions(monitorProxyPlugin.getPluginName()).first();
            if(null == serviceDefinition) {
                throw new RuntimeException("未找到代理插件:" + monitorProxyPlugin.getPluginName());
            }
            if(CollectionUtils.isEmpty(monitorProxyPluginConfigList)) {
                server.addFilter((Class<? extends ChainFilter>) serviceDefinition.getImplClass());
            } else {
                FilterConfigTypeDefinition filterConfigTypeDefinition =
                        new FilterConfigTypeDefinition(serviceDefinition.getImplClass(), FilterOrderType.NEXT);

                for (MonitorProxyPluginConfig monitorProxyPluginConfig : monitorProxyPluginConfigList) {
                    filterConfigTypeDefinition.addConfig(monitorProxyPluginConfig.getPluginConfigName(), monitorProxyPluginConfig.getPluginConfigValue());
                }
                server.addDefinition(filterConfigTypeDefinition);
            }
        }
//        if("tcp-proxy".equals(proxyProtocol)) {
//            server.addFilter(AsyncTcpRoutingGatewayFilter.class);
//            return server;
//        }
//        server.addFilter(AsyncHttpRoutingGatewayFilter.class);
//        server.addFilter(AsyncWebSocketRoutingGatewayFilter.class);
        return server;
    }

    private String createKey(MonitorProxy monitorProxy) {
        return monitorProxy.getProxyHost() + ":" + monitorProxy.getProxyPort();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            try {
                List<MonitorProxy> monitorProxies = baseMapper.selectList(Wrappers.<MonitorProxy>lambdaQuery().eq(MonitorProxy::getProxyStatus, 1));
                for (MonitorProxy monitorProxy : monitorProxies) {
                    try {
                        start(monitorProxy);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }
}

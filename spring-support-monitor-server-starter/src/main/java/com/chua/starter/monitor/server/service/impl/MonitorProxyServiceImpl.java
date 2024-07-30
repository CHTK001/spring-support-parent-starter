package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.chain.filter.Filter;
import com.chua.common.support.chain.filter.ServiceDiscoveryFilter;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.log.Log;
import com.chua.common.support.log.Slf4jLog;
import com.chua.common.support.objects.constant.FilterOrderType;
import com.chua.common.support.objects.definition.FilterConfigTypeDefinition;
import com.chua.common.support.objects.definition.ObjectElementDefinition;
import com.chua.common.support.objects.definition.TypeElementDefinition;
import com.chua.common.support.protocol.ServerSetting;
import com.chua.common.support.protocol.server.Server;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.common.support.utils.BooleanUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.monitor.server.chain.filter.*;
import com.chua.starter.monitor.server.entity.*;
import com.chua.starter.monitor.server.log.WebLog;
import com.chua.starter.monitor.server.mapper.MonitorProxyMapper;
import com.chua.starter.monitor.server.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 *
 * @since 2024/5/13 
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class MonitorProxyServiceImpl extends ServiceImpl<MonitorProxyMapper, MonitorProxy> implements MonitorProxyService, InitializingBean {

    private static final Map<String, Server> SERVER_MAP = new ConcurrentHashMap<>();
    private static final Map<String, MonitorFactory> LIMIT_MAP = new ConcurrentHashMap<>();
    final ApplicationContext applicationContext;
    final SocketSessionTemplate socketSessionTemplate;
    final TransactionTemplate transactionTemplate;
    final MonitorProxyConfigService monitorProxyConfigService;
    final MonitorProxyLimitService monitorproxyLimitService;
    final MonitorProxyLimitListService monitorproxyLimitListService;
    final MonitorProxyPluginService monitorProxyPluginService;
    final MonitorProxyPluginConfigService monitorProxyPluginConfigService;

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
                MonitorFactory monitorFactory = new MonitorFactory();
                Server server = createServer(monitorProxy, monitorFactory);
                try {
                    server.start();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                LIMIT_MAP.put(createKey(monitorProxy), monitorFactory);
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
                LIMIT_MAP.remove(key);
                return ReturnResult.success();
            }

            return ReturnResult.error("代理停止失败");
        });
    }

    @Override
    public void refresh(String proxyId) {
        MonitorProxy monitorProxy = baseMapper.selectById(proxyId);
        if(null == monitorProxy) {
            return;
        }

        String key = createKey(monitorProxy);
        MonitorFactory monitorFactory = LIMIT_MAP.get(key);
        Server server = SERVER_MAP.get(key);
        if(null == server) {
            return;
        }
        List<MonitorProxyConfig> list = monitorProxyConfigService.list(Wrappers.<MonitorProxyConfig>lambdaQuery().eq(MonitorProxyConfig::getProxyId, monitorProxy.getProxyId()));

        for (MonitorProxyConfig monitorProxyConfig : list) {
            server.addConfig(monitorProxyConfig.getConfigName(), monitorProxyConfig.getConfigValue());
        }

        ServerSetting serverSetting = ServerSetting.builder()
                .log(createLog(monitorProxy, list))
                .port(monitorProxy.getProxyPort())
                .build();

        server.upgrade(serverSetting);

        if(null != monitorFactory) {
            monitorFactory.upgrade(monitorproxyLimitService.list(Wrappers.<MonitorProxyLimit>lambdaQuery().eq(MonitorProxyLimit::getProxyId, monitorProxy.getProxyId()).eq(MonitorProxyLimit::getLimitDisable, 1)));
            monitorFactory.upgradeList(monitorproxyLimitListService.list(Wrappers.<MonitorProxyLimitList>lambdaQuery().eq(MonitorProxyLimitList::getProxyId, monitorProxy.getProxyId()).eq(MonitorProxyLimitList::getListStatus, 1)));
        }
    }

    @Override
    public Boolean updateConfigForProxy(MonitorProxyConfig one) {
        monitorProxyConfigService.updateById(one);
        return upgrade(one.getProxyId());
    }

    @SuppressWarnings("unchecked")
    private Server createServer(MonitorProxy monitorProxy, MonitorFactory monitorFactory) {
        List<MonitorProxyConfig> list = monitorProxyConfigService.list(Wrappers.<MonitorProxyConfig>lambdaQuery().eq(MonitorProxyConfig::getProxyId, monitorProxy.getProxyId()));
        String proxyProtocol = monitorProxy.getProxyProtocol();

        AtomicInteger counter = new AtomicInteger();
        ServerSetting serverSetting = ServerSetting.builder()
                .log(createLog(monitorProxy, list))
                .port(monitorProxy.getProxyPort())
                .build();
        Server server = Server.create(proxyProtocol, serverSetting);
        registerLimit(server, monitorProxy, list, monitorFactory, counter);
        registerServiceDiscovery(server, list, counter);

        for (MonitorProxyConfig monitorProxyConfig : list) {
            server.addConfig(monitorProxyConfig.getConfigName(), monitorProxyConfig.getConfigValue());
        }
        server.addConfig("serverId", monitorProxy.getProxyId());
        List<MonitorProxyPlugin> plugins = monitorProxyPluginService.list(Wrappers.<MonitorProxyPlugin>lambdaQuery()
                .eq(MonitorProxyPlugin::getProxyId, monitorProxy.getProxyId())
                .orderByDesc(MonitorProxyPlugin::getPluginSort)
        );
        registerPlugin(server, monitorProxy, plugins, counter);

        return server;
    }

    private void registerPlugin(Server server, MonitorProxy monitorProxy, List<MonitorProxyPlugin> plugins, AtomicInteger counter) {
        if(CollectionUtils.isEmpty(plugins)) {
            throw new RuntimeException("未找到代理插件");
        }
        List<MonitorProxyPluginConfig> list2 = monitorProxyPluginConfigService.list(Wrappers.<MonitorProxyPluginConfig>lambdaQuery().eq(MonitorProxyPluginConfig::getProxyId, monitorProxy.getProxyId()));
        Map<String, List<MonitorProxyPluginConfig>> configMap = new HashMap<>(list2.size());
        for (MonitorProxyPluginConfig config : list2) {
            configMap.computeIfAbsent(config.getPluginName() + config.getPluginSort(), it -> new LinkedList<>()).add(config);
        }

        for (MonitorProxyPlugin monitorProxyPlugin : plugins) {
            String key = monitorProxyPlugin.getPluginName() + monitorProxyPlugin.getPluginSort();
            List<MonitorProxyPluginConfig> monitorProxyPluginConfigList = configMap.get(key);
            ServiceDefinition serviceDefinition = ServiceProvider.of(Filter.class).getDefinitions(monitorProxyPlugin.getPluginName()).first();
            if(null == serviceDefinition) {
                throw new RuntimeException("未找到代理插件:" + monitorProxyPlugin.getPluginName());
            }
            if(CollectionUtils.isEmpty(monitorProxyPluginConfigList)) {
                server.addDefinition(new TypeElementDefinition(serviceDefinition.getImplClass()).order(counter.getAndIncrement()));
                continue;
            }
            FilterConfigTypeDefinition filterConfigTypeDefinition =
                    new FilterConfigTypeDefinition(serviceDefinition.getImplClass(), FilterOrderType.NONE);
            filterConfigTypeDefinition.order(counter.getAndIncrement());

            for (MonitorProxyPluginConfig monitorProxyPluginConfig : monitorProxyPluginConfigList) {
                filterConfigTypeDefinition.addConfig(monitorProxyPluginConfig.getPluginConfigName(), monitorProxyPluginConfig.getPluginConfigValue());
            }
            server.addDefinition(filterConfigTypeDefinition);
        }

    }

    /**
     * 注册服务发现
     *
     * @param server server
     * @param list
     * @param counter
     */
    private void registerServiceDiscovery(Server server, List<MonitorProxyConfig> list, AtomicInteger counter) {
        Map<String, ServiceDiscovery> beansOfType = applicationContext.getBeansOfType(ServiceDiscovery.class);
        Optional<MonitorProxyConfig> first = list.stream().filter(it -> "serviceDiscovery".equalsIgnoreCase(it.getConfigName())).findFirst();
        if(first.isEmpty()) {
            return ;
        }

        MonitorProxyConfig monitorProxyConfig = first.get();
        ServiceDiscovery serviceDiscovery = beansOfType.get(monitorProxyConfig.getConfigValue());
        if(null == serviceDiscovery) {
            serviceDiscovery = beansOfType.entrySet().stream().filter(it -> it.getKey().toUpperCase().endsWith("#" + monitorProxyConfig.getConfigValue())).findFirst().get().getValue();
        }
        server.addDefinition(new ObjectElementDefinition(new ServiceDiscoveryFilter(serviceDiscovery)).order(counter.getAndIncrement()));
    }

    /**
     * 注册限流
     *
     * @param server
     * @param monitorProxy
     * @param list
     * @param monitorFactory
     * @param counter
     */
    private void registerLimit(Server server, MonitorProxy monitorProxy, List<MonitorProxyConfig> list, MonitorFactory monitorFactory, AtomicInteger counter) {
        Optional<MonitorProxyConfig> openBlack = list.stream().filter(it -> "open-black".equalsIgnoreCase(it.getConfigName())).findFirst();
        Optional<MonitorProxyConfig> openWhite = list.stream().filter(it -> "open-white".equalsIgnoreCase(it.getConfigName())).findFirst();
        if(openBlack.isPresent() || openWhite.isPresent()) {
            List<MonitorProxyLimitList> list1 = monitorproxyLimitListService.list(Wrappers.<MonitorProxyLimitList>lambdaQuery().eq(MonitorProxyLimitList::getProxyId, monitorProxy.getProxyId()).eq(MonitorProxyLimitList::getListStatus, 1));
            registerBlackAndWhite(server, list1, openWhite, openBlack, monitorFactory, counter);
        }

        Optional<MonitorProxyConfig> openIpLimit = list.stream().filter(it -> "open-ip-limit".equalsIgnoreCase(it.getConfigName())).findFirst();
        Optional<MonitorProxyConfig> openUrlLimit = list.stream().filter(it -> "open-url-limit".equalsIgnoreCase(it.getConfigName())).findFirst();
        if(openIpLimit.isPresent() || openUrlLimit.isPresent()) {
            List<MonitorProxyLimit> list1 = monitorproxyLimitService.list(Wrappers.<MonitorProxyLimit>lambdaQuery().eq(MonitorProxyLimit::getProxyId, monitorProxy.getProxyId()).eq(MonitorProxyLimit::getLimitDisable, 1));
            registerIpAndUrlLimit(server, list1, openIpLimit, openUrlLimit, monitorFactory, counter);
        }
    }

    /**
     * 注册IP和URL限流
     *
     * @param server
     * @param list1
     * @param openIpLimit
     * @param openUrlLimit
     * @param monitorFactory
     * @param counter
     */
    private void registerIpAndUrlLimit(Server server, List<MonitorProxyLimit> list1, Optional<MonitorProxyConfig> openIpLimit, Optional<MonitorProxyConfig> openUrlLimit, MonitorFactory monitorFactory, AtomicInteger counter) {
        if(openIpLimit.isPresent() && !BooleanUtils.invalid(openIpLimit.get().getConfigValue())) {
            IpLimitFactory ipLimitFactory = new IpLimitFactory(list1);
            ipLimitFactory.initialize();
            server.addDefinition(ipLimitFactory);
            TypeElementDefinition filterConfigTypeDefinition = new TypeElementDefinition(MonitorIpLimitChainFilter.class);
            filterConfigTypeDefinition.order(counter.getAndIncrement());
            server.addDefinition(filterConfigTypeDefinition);
            monitorFactory.setIpLimitFactory(ipLimitFactory);
        }

        if(openUrlLimit.isPresent() && !BooleanUtils.invalid(openUrlLimit.get().getConfigValue())) {
            UrlLimitFactory urlLimitFactory = new UrlLimitFactory(list1);
            urlLimitFactory.initialize();
            server.addDefinition(urlLimitFactory);
            TypeElementDefinition filterConfigTypeDefinition = new TypeElementDefinition(MonitorUrlLimitChainFilter.class);
            filterConfigTypeDefinition.order(counter.getAndIncrement());
            server.addDefinition(filterConfigTypeDefinition);
            monitorFactory.setUrlLimitFactory(urlLimitFactory);
        }
    }

    /**
     * 注册黑名单和白名单
     *
     * @param server
     * @param list1
     * @param openWhite
     * @param openBlack
     * @param monitorFactory
     * @param counter
     */
    private void registerBlackAndWhite(Server server, List<MonitorProxyLimitList> list1, Optional<MonitorProxyConfig> openWhite, Optional<MonitorProxyConfig> openBlack, MonitorFactory monitorFactory, AtomicInteger counter) {
        if(openBlack.isPresent() && !BooleanUtils.invalid(openBlack.get().getConfigValue())) {
            BlackLimitFactory blackLimitFactory = new BlackLimitFactory(list1);
            blackLimitFactory.initialize();
            server.addDefinition(blackLimitFactory);
            TypeElementDefinition filterConfigTypeDefinition = new TypeElementDefinition(MonitorBlackChainFilter.class);
            filterConfigTypeDefinition.order(counter.getAndIncrement());
            server.addDefinition(filterConfigTypeDefinition);
            monitorFactory.setBlackLimitFactory(blackLimitFactory);
        }

        if(openWhite.isPresent() && !BooleanUtils.invalid(openWhite.get().getConfigValue())) {
            WhiteLimitFactory whiteLimitFactory = new WhiteLimitFactory(list1);
            whiteLimitFactory.initialize();
            server.addDefinition(whiteLimitFactory);
            TypeElementDefinition filterConfigTypeDefinition = new TypeElementDefinition(MonitorWhiteChainFilter.class);
            filterConfigTypeDefinition.order(counter.getAndIncrement());
            server.addDefinition(filterConfigTypeDefinition);
            monitorFactory.setWhiteLimitFactory(whiteLimitFactory);
        }
    }

    /**
     * 是否开启限流
     * @param list list
     * @return boolean
     */
    private boolean isLimitOpen(List<MonitorProxyConfig> list) {
        for (MonitorProxyConfig monitorProxyConfig : list) {
            if("open-limit".equals(monitorProxyConfig.getConfigName()) && "true".equals(monitorProxyConfig.getConfigValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建日志
     * @param monitorProxy monitorProxy
     * @param list list
     * @return Log
     */
    private Log createLog(MonitorProxy monitorProxy, List<MonitorProxyConfig> list) {
        for (MonitorProxyConfig monitorProxyConfig : list) {
            if("open-log".equals(monitorProxyConfig.getConfigName()) && "true".equals(monitorProxyConfig.getConfigValue())) {
                SpringBeanUtils.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(log);
                return new WebLog(monitorProxy.getProxyId() + "", socketSessionTemplate);
            }
        }
        return new Slf4jLog();
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

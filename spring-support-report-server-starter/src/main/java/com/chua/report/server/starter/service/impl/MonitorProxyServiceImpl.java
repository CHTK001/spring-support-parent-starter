package com.chua.report.server.starter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.chain.filter.Filter;
import com.chua.common.support.chain.filter.ServiceDiscoveryFilter;
import com.chua.common.support.discovery.DefaultServiceDiscovery;
import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.discovery.DiscoveryOption;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.log.Log;
import com.chua.common.support.log.Slf4jLog;
import com.chua.common.support.net.NetAddress;
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
import com.chua.report.server.starter.chain.filter.*;
import com.chua.report.server.starter.entity.*;
import com.chua.report.server.starter.mapper.MonitorProxyMapper;
import com.chua.report.server.starter.pojo.WebLog;
import com.chua.report.server.starter.service.*;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    private static final Map<String, ReportFactory> LIMIT_MAP = new ConcurrentHashMap<>();
    final ApplicationContext applicationContext;
    final SocketSessionTemplate socketSessionTemplate;
    final TransactionTemplate transactionTemplate;
    final MonitorProxyPluginService monitorProxyPluginService;
    final MonitorProxyPluginConfigService monitorProxyPluginConfigService;
    final MonitorProxyPluginListService monitorProxyPluginConfigListService;
    final MonitorProxyPluginLimitService monitorProxyPluginConfigLimitService;
    final MonitorProxyPluginStatisticServiceDiscoveryService monitorProxyPluginStatisticServiceDiscoveryService;

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
                ReportFactory reportFactory = new ReportFactory();
                Server server = createServer(monitorProxy, reportFactory);
                try {
                    server.start();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                LIMIT_MAP.put(createKey(monitorProxy), reportFactory);
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
        ReportFactory reportFactory = LIMIT_MAP.get(key);
        Server server = SERVER_MAP.get(key);
        if(null == server) {
            return;
        }

        List<MonitorProxyPlugin> list1 = monitorProxyPluginService.list(Wrappers.<MonitorProxyPlugin>lambdaQuery().eq(MonitorProxyPlugin::getProxyId, proxyId));
        Set<Integer> ids = list1.stream().map(MonitorProxyPlugin::getProxyPluginId).collect(Collectors.toUnmodifiableSet());
        List<MonitorProxyPluginConfig> configList = monitorProxyPluginConfigService.list(Wrappers.<MonitorProxyPluginConfig>lambdaQuery()
                .eq(MonitorProxyPluginConfig::getProxyId, proxyId));

        //注册配置|
        registerConfig(server, configList);
        Optional<MonitorProxyPluginConfig> first = configList.stream().filter(it -> "serviceDiscovery".equalsIgnoreCase(it.getProxyConfigName())).findFirst();
        if(first.isPresent()) {
            MonitorProxyPluginConfig monitorProxyConfig = first.get();
            upgradeServiceDiscovery(proxyId, monitorProxyConfig, server);
        }


        ServerSetting serverSetting = ServerSetting.builder()
                .log(createLog(monitorProxy, configList))
                .port(monitorProxy.getProxyPort())
                .build();

        server.upgrade(serverSetting);

        if(null != reportFactory) {
            reportFactory.upgrade(monitorProxyPluginConfigLimitService.list(Wrappers.<MonitorProxyPluginLimit>lambdaQuery()
                    .eq(MonitorProxyPluginLimit::getProxyId, proxyId).eq(MonitorProxyPluginLimit::getProxyConfigLimitDisabled, 1)));
            reportFactory.upgradeList(monitorProxyPluginConfigListService.list(Wrappers.<MonitorProxyPluginList>lambdaQuery()
                    .eq(MonitorProxyPluginList::getProxyId, proxyId).eq(MonitorProxyPluginList::getProxyConfigListDisabled, 1)));
        }
    }

    /**
     * 注册配置
     * @param server
     * @param configList
     */
    private void registerConfig(Server server, List<MonitorProxyPluginConfig> configList) {
        for (MonitorProxyPluginConfig monitorProxyConfig : configList) {
            server.addConfig(monitorProxyConfig.getProxyConfigName(), monitorProxyConfig.getProxyConfigValue());
        }
    }

    @Override
    public Boolean updateConfigForProxy(MonitorProxyPluginConfig one) {
        monitorProxyPluginConfigService.updateById(one);
        return upgrade(String.valueOf(one.getProxyId()));
    }

    @SuppressWarnings("unchecked")
    private Server createServer(MonitorProxy monitorProxy, ReportFactory reportFactory) {
        List<MonitorProxyPluginConfig> list = monitorProxyPluginConfigService.list(Wrappers.<MonitorProxyPluginConfig>lambdaQuery()
                .eq(MonitorProxyPluginConfig::getProxyId, monitorProxy.getProxyId()));
        String proxyProtocol = monitorProxy.getProxyProtocol();

        AtomicInteger counter = new AtomicInteger();
        ServerSetting serverSetting = ServerSetting.builder()
                .log(createLog(monitorProxy, list))
                .port(monitorProxy.getProxyPort())
                .build();
        Server server = Server.create(proxyProtocol, serverSetting);
        registerServiceDiscovery(server, list, counter);

        for (MonitorProxyPluginConfig monitorProxyConfig : list) {
            server.addConfig(monitorProxyConfig.getProxyConfigName(), monitorProxyConfig.getProxyConfigValue());
        }

        ReportBlackLimitFactory reportBlackLimitFactory = new ReportBlackLimitFactory(Collections.emptyList());
        reportBlackLimitFactory.initialize();
        server.addDefinition(reportBlackLimitFactory);

        ReportWhiteLimitFactory reportWhiteLimitFactory = new ReportWhiteLimitFactory(Collections.emptyList());
        reportWhiteLimitFactory.initialize();
        server.addDefinition(reportWhiteLimitFactory);

        ReportIpLimitFactory reportIpLimitFactory = new ReportIpLimitFactory(Collections.emptyList());
        reportIpLimitFactory.initialize();
        server.addDefinition(reportIpLimitFactory);

        ReportUrlLimitFactory reportUrlLimitFactory = new ReportUrlLimitFactory(Collections.emptyList());
        reportUrlLimitFactory.initialize();
        server.addDefinition(reportUrlLimitFactory);
        reportFactory.setReportBlackLimitFactory(reportBlackLimitFactory);
        reportFactory.setReportWhiteLimitFactory(reportWhiteLimitFactory);
        reportFactory.setReportIpLimitFactory(reportIpLimitFactory);
        reportFactory.setReportUrlLimitFactory(reportUrlLimitFactory);
        refresh(String.valueOf(monitorProxy.getProxyId()));

        server.addConfig("serverId", monitorProxy.getProxyId());
        server.addConfig("protocol", monitorProxy.getProxyProtocol().replace("-proxy", ""));
        registerPlugin(server, monitorProxy, counter);
//        registerLimit(server, monitorProxy, list, reportFactory, counter);

        return server;
    }

    private void registerPlugin(Server server, MonitorProxy monitorProxy,  AtomicInteger counter) {
        List<MonitorProxyPlugin> plugins = monitorProxyPluginService.list(Wrappers.<MonitorProxyPlugin>lambdaQuery()
                .eq(MonitorProxyPlugin::getProxyId, monitorProxy.getProxyId())
                .orderByAsc(MonitorProxyPlugin::getProxyPluginSort)
        );
        if(CollectionUtils.isEmpty(plugins)) {
            throw new RuntimeException("未找到代理插件");
        }

        List<MonitorProxyPluginConfig> configList = monitorProxyPluginConfigService.list(Wrappers.<MonitorProxyPluginConfig>lambdaQuery().eq(MonitorProxyPluginConfig::getProxyId, monitorProxy.getProxyId()));
        Map<Integer, List<MonitorProxyPluginConfig>> collect = configList.stream().collect(Collectors.groupingBy(MonitorProxyPluginConfig::getProxyPluginId));

        for (MonitorProxyPlugin monitorProxyPlugin : plugins) {
            ServiceDefinition serviceDefinition = ServiceProvider.of(Filter.class).getDefinitions(monitorProxyPlugin.getProxyPluginSpi()).first();
            if(null == serviceDefinition) {
                throw new RuntimeException("未找到代理插件:" + monitorProxyPlugin.getProxyPluginName());
            }

            List<MonitorProxyPluginConfig> monitorProxyPluginConfigList = collect.get(monitorProxyPlugin.getProxyPluginId());
            if(CollectionUtils.isEmpty(monitorProxyPluginConfigList)) {
                server.addDefinition(new TypeElementDefinition(serviceDefinition.getImplClass()).order(counter.getAndIncrement()));
                continue;
            }
            FilterConfigTypeDefinition filterConfigTypeDefinition =
                    new FilterConfigTypeDefinition(serviceDefinition.getImplClass(), FilterOrderType.NONE);
            filterConfigTypeDefinition.order(counter.getAndIncrement());

            for (MonitorProxyPluginConfig monitorProxyPluginConfig : monitorProxyPluginConfigList) {
                filterConfigTypeDefinition.addConfig(monitorProxyPluginConfig.getProxyConfigName(), monitorProxyPluginConfig.getProxyConfigValue());
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
    private void registerServiceDiscovery(Server server, List<MonitorProxyPluginConfig> list, AtomicInteger counter) {
        Optional<MonitorProxyPluginConfig> first = list.stream().filter(it -> "serviceDiscovery".equalsIgnoreCase(it.getProxyConfigName())).findFirst();
        if(first.isEmpty()) {
            return ;
        }

        ServiceDiscovery serviceDiscovery = null;
        MonitorProxyPluginConfig monitorProxyConfig = first.get();
        if("statistic".equalsIgnoreCase(monitorProxyConfig.getProxyConfigValue())) {
            serviceDiscovery = new DefaultServiceDiscovery(new DiscoveryOption());
            upgradeServiceDiscovery(String.valueOf(monitorProxyConfig.getProxyId()), serviceDiscovery);
        } else {
            Map<String, ServiceDiscovery> beansOfType = applicationContext.getBeansOfType(ServiceDiscovery.class);
            serviceDiscovery = beansOfType.get(monitorProxyConfig.getProxyConfigValue());
            if(null == serviceDiscovery) {
                serviceDiscovery = beansOfType.entrySet().stream().filter(it -> it.getKey().toUpperCase().endsWith("#" +
                        monitorProxyConfig.getProxyConfigValue())).findFirst().get().getValue();
            }
        }
        server.addDefinition(new ObjectElementDefinition(new ServiceDiscoveryFilter(serviceDiscovery)).order(counter.getAndIncrement()));
    }

    /**
     * 升级服务发现
     * @param proxyId
     * @param monitorProxyConfig
     * @param server
     */
    private void upgradeServiceDiscovery(String proxyId, MonitorProxyPluginConfig monitorProxyConfig, Server server) {
        if("statistic".equalsIgnoreCase(monitorProxyConfig.getProxyConfigValue())) {
            ServiceDiscoveryFilter serviceDiscoveryFilter = (ServiceDiscoveryFilter) server.getFilter(ServiceDiscoveryFilter.class);
            if(null != serviceDiscoveryFilter) {
                upgradeServiceDiscovery(proxyId, serviceDiscoveryFilter.getServiceDiscovery());
            }
        }
    }

    private void upgradeServiceDiscovery(String proxyId, ServiceDiscovery serviceDiscovery) {
        if(null == serviceDiscovery) {
            return ;
        }

        List<MonitorProxyPluginStatisticServiceDiscovery> list = monitorProxyPluginStatisticServiceDiscoveryService
                .list(Wrappers.<MonitorProxyPluginStatisticServiceDiscovery>lambdaQuery()
                        .eq(MonitorProxyPluginStatisticServiceDiscovery::getProxyId, proxyId)
                        .eq(MonitorProxyPluginStatisticServiceDiscovery::getProxyStatisticStatus, 1));

        if(serviceDiscovery instanceof DefaultServiceDiscovery defaultServiceDiscovery) {
            defaultServiceDiscovery.reset();
        }
        for (MonitorProxyPluginStatisticServiceDiscovery monitorProxyStatisticServiceDiscovery : list) {
            String proxyStatisticProtocol = monitorProxyStatisticServiceDiscovery.getProxyStatisticProtocol();
            String host = monitorProxyStatisticServiceDiscovery.getProxyStatisticHostname();
            int port = 0;
            if("tcp".equalsIgnoreCase(proxyStatisticProtocol) || "websockify".equalsIgnoreCase(proxyStatisticProtocol)) {
                NetAddress netAddress = NetAddress.of(host);
                port = netAddress.getPort(0);
                host = netAddress.getHost("127.0.0.1");
            }
            Discovery discovery = Discovery.builder()
                    .host(host)
                    .protocol(proxyStatisticProtocol)
                    .port(port)
                    .weight(ObjectUtils.defaultIfNull(monitorProxyStatisticServiceDiscovery.getProxyStatisticWeight(), 0))
                    .build();
            serviceDiscovery.registerService(monitorProxyStatisticServiceDiscovery.getProxyStatisticUrl(), discovery);
        }

    }

    /**
     * 注册限流
     *
     * @param server
     * @param monitorProxy
     * @param list
     * @param reportFactory
     * @param counter
     */
    private void registerLimit(Server server, MonitorProxy monitorProxy, List<MonitorProxyPluginConfig> list, ReportFactory reportFactory, AtomicInteger counter) {
        Optional<MonitorProxyPluginConfig> openBlack = list.stream().filter(it -> "open-black".equalsIgnoreCase(it.getProxyConfigName())).findFirst();
        Optional<MonitorProxyPluginConfig> openWhite = list.stream().filter(it -> "open-white".equalsIgnoreCase(it.getProxyConfigName())).findFirst();
        if(openBlack.isPresent() || openWhite.isPresent()) {
            List<MonitorProxyPluginList> list1 = monitorProxyPluginConfigListService.list(Wrappers.<MonitorProxyPluginList>lambdaQuery()
                    .eq(MonitorProxyPluginList::getProxyId, monitorProxy.getProxyId()).eq(MonitorProxyPluginList::getProxyConfigListDisabled, 1));
            registerBlackAndWhite(server, list1, openWhite, openBlack, reportFactory, counter);
        }

        Optional<MonitorProxyPluginConfig> openIpLimit = list.stream().filter(it -> "open-ip-limit".equalsIgnoreCase(it.getProxyConfigName())).findFirst();
        Optional<MonitorProxyPluginConfig> openUrlLimit = list.stream().filter(it -> "open-url-limit".equalsIgnoreCase(it.getProxyConfigName())).findFirst();
        if(openIpLimit.isPresent() || openUrlLimit.isPresent()) {
            List<MonitorProxyPluginLimit> list1 = monitorProxyPluginConfigLimitService.list(Wrappers.<MonitorProxyPluginLimit>lambdaQuery()
                    .eq(MonitorProxyPluginLimit::getProxyId, monitorProxy.getProxyId()).eq(MonitorProxyPluginLimit::getProxyConfigLimitDisabled, 1));
            registerIpAndUrlLimit(server, list1, openIpLimit, openUrlLimit, reportFactory, counter);
        }
    }

    /**
     * 注册IP和URL限流
     *
     * @param server
     * @param list1
     * @param openIpLimit
     * @param openUrlLimit
     * @param reportFactory
     * @param counter
     */
    private void registerIpAndUrlLimit(Server server, List<MonitorProxyPluginLimit> list1, Optional<MonitorProxyPluginConfig> openIpLimit, Optional<MonitorProxyPluginConfig> openUrlLimit, ReportFactory reportFactory, AtomicInteger counter) {
        if(openIpLimit.isPresent() && !BooleanUtils.invalid(openIpLimit.get().getProxyConfigValue())) {
            ReportIpLimitFactory reportIpLimitFactory = new ReportIpLimitFactory(list1);
            reportIpLimitFactory.initialize();
            server.addDefinition(reportIpLimitFactory);
            TypeElementDefinition filterConfigTypeDefinition = new TypeElementDefinition(ReportIpLimitChainFilter.class);
            filterConfigTypeDefinition.order(counter.getAndIncrement());
            server.addDefinition(filterConfigTypeDefinition);
            reportFactory.setReportIpLimitFactory(reportIpLimitFactory);
        }

        if(openUrlLimit.isPresent() && !BooleanUtils.invalid(openUrlLimit.get().getProxyConfigValue())) {
            ReportUrlLimitFactory reportUrlLimitFactory = new ReportUrlLimitFactory(list1);
            reportUrlLimitFactory.initialize();
            server.addDefinition(reportUrlLimitFactory);
            TypeElementDefinition filterConfigTypeDefinition = new TypeElementDefinition(ReportUrlLimitChainFilter.class);
            filterConfigTypeDefinition.order(counter.getAndIncrement());
            server.addDefinition(filterConfigTypeDefinition);
            reportFactory.setReportUrlLimitFactory(reportUrlLimitFactory);
        }
    }

    /**
     * 注册黑名单和白名单
     *
     * @param server
     * @param list1
     * @param openWhite
     * @param openBlack
     * @param reportFactory
     * @param counter
     */
    private void registerBlackAndWhite(Server server, List<MonitorProxyPluginList> list1, Optional<MonitorProxyPluginConfig> openWhite, Optional<MonitorProxyPluginConfig> openBlack, ReportFactory reportFactory, AtomicInteger counter) {
        if(openBlack.isPresent() && !BooleanUtils.invalid(openBlack.get().getProxyConfigValue())) {
            ReportBlackLimitFactory reportBlackLimitFactory = new ReportBlackLimitFactory(list1);
            reportBlackLimitFactory.initialize();
            server.addDefinition(reportBlackLimitFactory);
            TypeElementDefinition filterConfigTypeDefinition = new TypeElementDefinition(ReportBlackChainFilter.class);
            filterConfigTypeDefinition.order(counter.getAndIncrement());
            server.addDefinition(filterConfigTypeDefinition);
            reportFactory.setReportBlackLimitFactory(reportBlackLimitFactory);
        }

        if(openWhite.isPresent() && !BooleanUtils.invalid(openWhite.get().getProxyConfigValue())) {
            ReportWhiteLimitFactory reportWhiteLimitFactory = new ReportWhiteLimitFactory(list1);
            reportWhiteLimitFactory.initialize();
            server.addDefinition(reportWhiteLimitFactory);
            TypeElementDefinition filterConfigTypeDefinition = new TypeElementDefinition(ReportWhiteChainFilter.class);
            filterConfigTypeDefinition.order(counter.getAndIncrement());
            server.addDefinition(filterConfigTypeDefinition);
            reportFactory.setReportWhiteLimitFactory(reportWhiteLimitFactory);
        }
    }

    /**
     * 是否开启限流
     * @param list list
     * @return boolean
     */
    private boolean isLimitOpen(List<MonitorProxyPluginConfig> list) {
        for (MonitorProxyPluginConfig monitorProxyConfig : list) {
            if("open-limit".equals(monitorProxyConfig.getProxyConfigName()) && "true".equals(monitorProxyConfig.getProxyConfigValue())) {
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
    private Log createLog(MonitorProxy monitorProxy, List<MonitorProxyPluginConfig> list) {
        for (MonitorProxyPluginConfig monitorProxyConfig : list) {
            if("open-log".equals(monitorProxyConfig.getProxyConfigName()) && "true".equals(monitorProxyConfig.getProxyConfigValue())) {
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

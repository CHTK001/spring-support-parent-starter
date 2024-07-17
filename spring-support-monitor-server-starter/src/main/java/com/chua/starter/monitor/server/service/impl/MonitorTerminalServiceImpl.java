package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.ClientSetting;
import com.chua.common.support.protocol.channel.Channel;
import com.chua.common.support.protocol.session.Session;
import com.chua.common.support.session.indicator.CollectionIndicator;
import com.chua.common.support.session.indicator.MapIndicator;
import com.chua.common.support.session.indicator.TimeIndicator;
import com.chua.common.support.session.indicator.WIndicator;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.redis.support.constant.RedisConstant;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.ssh.support.ssh.ExecChannel;
import com.chua.ssh.support.ssh.SshClient;
import com.chua.ssh.support.ssh.SshSession;
import com.chua.starter.monitor.server.entity.MonitorTerminal;
import com.chua.starter.monitor.server.entity.MonitorTerminalBase;
import com.chua.starter.monitor.server.mapper.MonitorTerminalMapper;
import com.chua.starter.monitor.server.service.*;
import com.chua.starter.redis.support.service.TimeSeriesService;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.chua.starter.monitor.server.constant.RedisConstant.REDIS_TIME_SERIES_INDICATOR_PREFIX;


/**
 * @author CH
 * @since 2024/6/19
 */
@Service
@Slf4j
public class MonitorTerminalServiceImpl extends ServiceImpl<MonitorTerminalMapper, MonitorTerminal> implements MonitorTerminalService, InitializingBean {

    private static final Map<String, CacheClient> SERVER_MAP = new ConcurrentHashMap<>();
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private SocketSessionTemplate socketSessionTemplate;

    @Resource
    private TimeSeriesService timeSeriesService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private IptablesService iptablesService;

    @Resource
    private MonitorTerminalBaseService monitorTerminalBaseService;
    @Resource
    private MonitorProxyConfigService monitorProxyConfigService;

    @Resource
    private MonitorProxyPluginService monitorProxyPluginService;
    @Resource
    private MonitorProxyPluginConfigService monitorProxyPluginConfigService;

    private final ScheduledExecutorService executorService = ThreadUtils.newScheduledThreadPoolExecutor(1);
    private final ExecutorService reporterService = ThreadUtils.newFixedThreadExecutor(100);

    @Override
    public ReturnResult<Boolean> start(MonitorTerminal monitorTerminal) {
        if (ObjectUtils.isEmpty(monitorTerminal.getTerminalHost()) || ObjectUtils.isEmpty(monitorTerminal.getTerminalPort())) {
            return ReturnResult.error("代理地址不能为空");
        }

        String key = createKey(monitorTerminal);
        if (SERVER_MAP.containsKey(key)) {
            return ReturnResult.error("代理已启动, 请刷新页面");
        }

        if (null != monitorTerminal.getTerminalStatus() && 2 == monitorTerminal.getTerminalStatus()) {
            return ReturnResult.error("代理正在启动中");
        }

        return transactionTemplate.execute(it -> {
            monitorTerminal.setTerminalStatus(2);
            int i = baseMapper.updateById(monitorTerminal);
            if (i > 0) {
                SshClient sshClient = createClient(monitorTerminal);
                SERVER_MAP.put(key, new CacheClient(sshClient, monitorTerminal));
                monitorTerminal.setTerminalStatus(1);
                baseMapper.updateById(monitorTerminal);
                return ReturnResult.success();
            }
            return ReturnResult.error("代理启动失败");
        });

    }

    @Override
    public ReturnResult<Boolean> stop(MonitorTerminal monitorTerminal) {
        if (ObjectUtils.isEmpty(monitorTerminal.getTerminalHost()) || ObjectUtils.isEmpty(monitorTerminal.getTerminalPort())) {
            return ReturnResult.error("代理地址不能为空");
        }

        String key = createKey(monitorTerminal);
        if (!SERVER_MAP.containsKey(key)) {
            if (0 == monitorTerminal.getTerminalStatus()) {
                return ReturnResult.error("代理已停止");
            }

            if (2 == monitorTerminal.getTerminalStatus()) {
                return ReturnResult.error("代理正在启动中");
            }
        }

        monitorTerminal.setTerminalStatus(0);
        return transactionTemplate.execute(it -> {
            int i = baseMapper.updateById(monitorTerminal);
            if (i > 0) {
                try {
                    CacheClient cacheClient = SERVER_MAP.get(key);
                    if (null != cacheClient) {
                        cacheClient.getSshClient().close();
                        SERVER_MAP.remove(key);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return ReturnResult.success();
            }

            return ReturnResult.error("代理停止失败");
        });
    }

    @Override
    public SshClient getClient(String requestId) {
        MonitorTerminal monitorTerminal = getById(requestId);
        if (null == monitorTerminal) {
            return null;
        }
        CacheClient cacheClient = SERVER_MAP.get(createKey(monitorTerminal));
        if(null == cacheClient) {
            return null;
        }
        return cacheClient.getSshClient();
    }

    @Override
    public boolean indicator(MonitorTerminal monitorTerminal) {
        String key = createKey(monitorTerminal);
        if (!SERVER_MAP.containsKey(key) && 0 == monitorTerminal.getTerminalStatus()) {
            return false;
        }
        try {
            CacheClient cacheClient = SERVER_MAP.get(key);
            if (null != cacheClient) {
                doIndicator(String.valueOf(monitorTerminal.getTerminalId()), cacheClient.getSshClient(), monitorTerminal);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    @Override
    public String ifconfig(MonitorTerminal monitorTerminal) {
        String key = createKey(monitorTerminal);
        if (!SERVER_MAP.containsKey(key) && 0 == monitorTerminal.getTerminalStatus()) {
            throw new RuntimeException("代理未启动");
        }
        try {
            CacheClient cacheClient = SERVER_MAP.get(key);
            if ((null != cacheClient)) {
                Channel channel = cacheClient.getSshClient().getSession().openChannel(key, "exec");
                String ip = channel.execute("curl ifconfig.me", 3000);
                checkRelease("public-ifconfig", ip, monitorTerminal, "公网IP");
                return ip;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("代理未启动/不支持");
    }

    @Override
    public List<MonitorTerminalBase> base(MonitorTerminal monitorTerminal) {
        return monitorTerminalBaseService.list(Wrappers.<MonitorTerminalBase>lambdaQuery()
                .eq(MonitorTerminalBase::getTerminalId, monitorTerminal.getTerminalId()));
    }

    @Override
    public List<MonitorTerminalBase> baseUpgrade(MonitorTerminal monitorTerminal) {
        String key = createKey(monitorTerminal);
        if (!SERVER_MAP.containsKey(key) && 0 == monitorTerminal.getTerminalStatus()) {
            throw new RuntimeException("代理未启动");
        }

        List<MonitorTerminalBase> result = new LinkedList<>();
        try {
            CacheClient cacheClient = SERVER_MAP.get(key);
            if ((null != cacheClient)) {
                try (Session session = cacheClient.getSshClient().createSession("exec")) {
                    ExecChannel channel = (ExecChannel) session.openChannel(key, "exec");
                    try {
                        CollectionUtils.addAll(result, checkRelease("public-ifconfig", channel.ifconfig(), monitorTerminal, "公网IP"));
                    } finally {
                        session.closeChannel(key);
                    }
                    channel = (ExecChannel) session.openChannel(key, "exec");
                    String release = channel.release();
                    try {
                        if(!StringUtils.isBlank(release)) {
                            CollectionUtils.addAll(result, checkRelease("release", release, monitorTerminal, "系统信息"));
                        }
                    } finally {
                        session.closeChannel(key);
                    }

                    if(StringUtils.isBlank(release)) {
                        channel = (ExecChannel) session.openChannel(key, "exec");
                        try {
                            CollectionUtils.addAll(result, checkRelease("release", channel.uname(), monitorTerminal, "系统信息"));
                        } finally {
                            session.closeChannel(key);
                        }
                    }
                    channel = (ExecChannel) session.openChannel(key, "exec");
                    try {
                        CollectionUtils.addAll(result, checkRelease("ulimit", channel.ulimit() + "", monitorTerminal, "最大连接数"));
                    } finally {
                        session.closeChannel(key);
                    }
                    channel = (ExecChannel) session.openChannel(key, "exec");
                    try {
                        CollectionUtils.addAll(result, checkRelease("mem-total", channel.memTotal() + "", monitorTerminal, "最大内存"));
                    } finally {
                        session.closeChannel(key);
                    }
                    channel = (ExecChannel) session.openChannel(key, "exec");
                    try {
                        CollectionUtils.addAll(result, checkRelease("cpu-model", channel.cpuModel(), monitorTerminal, "cpu型号"));
                    } finally {
                        session.closeChannel(key);
                    }
                    channel = (ExecChannel) session.openChannel(key, "exec");
                    try {
                        CollectionUtils.addAll(result, checkRelease("cpu-num", channel.cpuNum() + "", monitorTerminal, "cpu数"));
                    } finally {
                        session.closeChannel(key);
                    }
                    channel = (ExecChannel) session.openChannel(key, "exec");
                    try {
                        CollectionUtils.addAll(result, checkRelease("cpu-core-num", channel.cpuCore() + "", monitorTerminal, "cpu core数"));
                    } finally {
                        session.closeChannel(key);
                    }
                } finally {
                    cacheClient.getSshClient().closeSession("exec");
                }
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("代理未启动/不支持");
    }

    /**
     * 检查是否更新
     *
     * @param monitorTerminal
     * @return
     */

    private MonitorTerminalBase checkRelease(String name, String value, MonitorTerminal monitorTerminal, String desc) {
        try {
            MonitorTerminalBase monitorTerminalBase = monitorTerminalBaseService.getOne(Wrappers.<MonitorTerminalBase>lambdaQuery()
                    .eq(MonitorTerminalBase::getTerminalId, monitorTerminal.getTerminalId())
                    .eq(MonitorTerminalBase::getBaseName, name)
            );
            if (null == monitorTerminalBase) {
                monitorTerminalBase = new MonitorTerminalBase();
                monitorTerminalBase.setBaseName(name);
                monitorTerminalBase.setTerminalId(monitorTerminal.getTerminalId());
                monitorTerminalBase.setBaseValue(value);
                monitorTerminalBase.setBaseDesc(desc);
                monitorTerminalBaseService.save(monitorTerminalBase);
                return monitorTerminalBase;
            }
            monitorTerminalBase.setBaseValue(value);
            monitorTerminalBaseService.updateById(monitorTerminalBase);
            return monitorTerminalBase;
        } catch (Exception ignored) {
        }

        throw new RuntimeException("代理未启动/不支持");
    }

    private SshClient createClient(MonitorTerminal monitorTerminal) {
        SshClient sshClient = new SshClient(ClientSetting.builder()
                .username(monitorTerminal.getTerminalUser())
                .password(monitorTerminal.getTerminalPassword())
                .host(monitorTerminal.getTerminalHost())
                .port(Integer.valueOf(monitorTerminal.getTerminalPort()))
                .build()
        );
        sshClient.connect();
        return sshClient;
    }

    private String createKey(MonitorTerminal monitorTerminal) {
        return monitorTerminal.getTerminalId() + "_" + monitorTerminal.getTerminalHost() + ":" + monitorTerminal.getTerminalPort();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            try {
                List<MonitorTerminal> monitorProxies = baseMapper.selectList(Wrappers.<MonitorTerminal>lambdaQuery()
                        .in(MonitorTerminal::getTerminalStatus, 1, 2));
                for (MonitorTerminal monitorTerminal : monitorProxies) {
                    try {
                        monitorTerminal.setTerminalStatus(1);
                        start(monitorTerminal);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        });

        executorService.scheduleWithFixedDelay(() -> {
            for (Map.Entry<String, CacheClient> entry : SERVER_MAP.entrySet()) {
                CacheClient cacheClient = entry.getValue();
                SshClient sshClient = cacheClient.getSshClient();
                MonitorTerminal monitorTerminal = cacheClient.getMonitorTerminal();
                reporterService.execute(() -> {
                    synchronized (sshClient) {
                        doIndicator(String.valueOf(monitorTerminal.getTerminalId()), sshClient, monitorTerminal);
                    }
                });
            }
        }, 0, 10, TimeUnit.SECONDS);
    }
    @SuppressWarnings("ALL")
    private void doIndicator(String terminalId, SshClient sshClient, MonitorTerminal monitorTerminal) {
        SshSession sshClientSession = (SshSession) sshClient.getSession();
        Set<String> allIndicator = sshClientSession.getAllIndicator();
        for (String s : allIndicator) {
            TimeIndicator indicator = sshClientSession.getIndicator(s);
            if (null == indicator) {
                continue;
            }

            if(indicator instanceof CollectionIndicator collectionIndicator && collectionIndicator.is(WIndicator.class)) {
                collectionIndicator.forEach(new Consumer<WIndicator>() {
                    @Override
                    public void accept(WIndicator it) {
                        GeoCity geoCity = iptablesService.getGeoCity(it.getFrom());
                        if(StringUtils.isNotEmpty(geoCity.getCity())) {
                            it.setCity(geoCity.getCity() + "-" + geoCity.getIsp());
                        }
                    }
                }, WIndicator.class);
            }
            socketSessionTemplate.send("terminal-report-" + terminalId, Json.toJson(indicator));

            indicator.toLinked().forEach(new Consumer<MapIndicator>() {

                @Override
                public void accept(MapIndicator mapIndicator) {
                    if(!mapIndicator.isPersistence()) {
                        return;
                    }
                    timeSeriesService.save(REDIS_TIME_SERIES_INDICATOR_PREFIX + terminalId + ":" + mapIndicator.getType() +":" + mapIndicator.getName(), mapIndicator.getTimestamp(), mapIndicator.getValue(), mapIndicator.get(), RedisConstant.DEFAULT_RETENTION_PERIOD);
                }
            });

        }
    }


    @Data
    @AllArgsConstructor
    static class CacheClient {
        private SshClient sshClient;
        private MonitorTerminal monitorTerminal;
    }
}

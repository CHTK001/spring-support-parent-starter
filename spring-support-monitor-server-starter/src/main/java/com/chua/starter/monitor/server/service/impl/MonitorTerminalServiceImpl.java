package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.datasource.jdbc.option.DataSourceOptions;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.Session;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.ssh.support.session.TerminalSession;
import com.chua.starter.monitor.server.entity.MonitorTerminal;
import com.chua.starter.monitor.server.mapper.MonitorTerminalMapper;
import com.chua.starter.monitor.server.service.MonitorProxyConfigService;
import com.chua.starter.monitor.server.service.MonitorProxyPluginConfigService;
import com.chua.starter.monitor.server.service.MonitorProxyPluginService;
import com.chua.starter.monitor.server.service.MonitorTerminalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 *
 * @since 2024/6/19 
 * @author CH
 */
@Service
@Slf4j
public class MonitorTerminalServiceImpl extends ServiceImpl<MonitorTerminalMapper, MonitorTerminal> implements MonitorTerminalService, InitializingBean {

    private static final Map<String, Session> SERVER_MAP = new ConcurrentHashMap<>();
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private SocketSessionTemplate socketSessionTemplate;

    @Resource
    private TransactionTemplate transactionTemplate;

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
        if(ObjectUtils.isEmpty(monitorTerminal.getTerminalHost()) || ObjectUtils.isEmpty(monitorTerminal.getTerminalPort())) {
            return ReturnResult.error("代理地址不能为空");
        }

        String key = createKey(monitorTerminal);
        if(SERVER_MAP.containsKey(key)) {
            return ReturnResult.error("代理已启动, 请刷新页面");
        }

        return transactionTemplate.execute(it -> {
            monitorTerminal.setTerminalStatus(1);
            int i = baseMapper.updateById(monitorTerminal);
            if(i > 0) {
                Session session = createSession(monitorTerminal);
                SERVER_MAP.put(key, session);
                return ReturnResult.success();
            }
            return ReturnResult.error("代理启动失败");
        });

    }

    @Override
    public ReturnResult<Boolean> stop(MonitorTerminal monitorTerminal) {
        if(ObjectUtils.isEmpty(monitorTerminal.getTerminalHost()) || ObjectUtils.isEmpty(monitorTerminal.getTerminalPort())) {
            return ReturnResult.error("代理地址不能为空");
        }

        String key = createKey(monitorTerminal);
        if(!SERVER_MAP.containsKey(key) && 0 == monitorTerminal.getTerminalStatus()) {
            return ReturnResult.error("代理已停止");
        }

        monitorTerminal.setTerminalStatus(0);
        return transactionTemplate.execute(it -> {
            int i = baseMapper.updateById(monitorTerminal);
            if(i > 0) {
                try {
                    Session session = SERVER_MAP.get(key);
                    if(null != session) {
                        session.close();
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
    public Session getSession(String requestId) {
        MonitorTerminal monitorTerminal = getById(requestId);
        if(null == monitorTerminal) {
            return null;
        }
        return SERVER_MAP.get(createKey(monitorTerminal));
    }

    @Override
    public boolean indicator(MonitorTerminal monitorTerminal) {
        String key = createKey(monitorTerminal);
        if(!SERVER_MAP.containsKey(key) && 0 == monitorTerminal.getTerminalStatus()) {
            return false;
        }
        try {
            Session session = SERVER_MAP.get(key);
            if(null != session) {
                doIndicator(String.valueOf(monitorTerminal.getTerminalId()), session);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private Session createSession(MonitorTerminal monitorTerminal) {
        return new TerminalSession(DataSourceOptions.newBuilder()
                .username(monitorTerminal.getTerminalUser())
                .password(monitorTerminal.getTerminalPassword())
                .url(monitorTerminal.getTerminalHost() + ":" + monitorTerminal.getTerminalPort())
                .build()
        );
    }

    private String createKey(MonitorTerminal monitorTerminal) {
        return monitorTerminal.getTerminalId() + "_" + monitorTerminal.getTerminalHost() + ":" + monitorTerminal.getTerminalPort();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            try {
                List<MonitorTerminal> monitorProxies = baseMapper.selectList(Wrappers.<MonitorTerminal>lambdaQuery().eq(MonitorTerminal::getTerminalStatus, 1));
                for (MonitorTerminal monitorTerminal : monitorProxies) {
                    try {
                        start(monitorTerminal);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        });

        executorService.scheduleWithFixedDelay(() -> {
            for (Map.Entry<String, Session> entry : SERVER_MAP.entrySet()) {
                reporterService.execute(() -> {
                    String terminalId = getTerminalId(entry.getKey());
                    Session session = entry.getValue();
                    doIndicator(terminalId, session);
                });
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void doIndicator(String terminalId, Session session) {
        if(session instanceof TerminalSession terminalSession) {
            Set<String> allIndicator = terminalSession.getAllIndicator();
            for (String s : allIndicator) {
                Object indicator = terminalSession.getIndicator(s);
                if(null == indicator) {
                    continue;
                }
                socketSessionTemplate.send("terminal-report-" + terminalId, Json.toJson(indicator));
            }
        }
    }

    /**
     * 获取终端id
     * @param key key
     * @return 终端id
     */
    private String getTerminalId(String key) {
        String[] split = key.split("_");
        return split[0];
    }
}

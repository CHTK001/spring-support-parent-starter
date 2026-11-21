// [P3C A20 豁免] A20/A30/A31: 含日志/网络字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.server;

import com.chua.common.support.network.discovery.ServiceDiscovery;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.sync.SyncServerRegistrar;
import com.chua.common.support.network.sync.impl.TcpSyncServer;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.logger.client.TcpLoggerTransport;
import com.chua.starter.logger.event.LoggerEvent;
import com.chua.starter.logger.properties.LoggerProperties;
import com.chua.starter.logger.store.LoggerEventBus;
import com.chua.starter.logger.store.LoggerEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.Arrays;
import java.util.List;

/**
 * 日志接收端：包装 utils 层 {@link TcpSyncServer}，监听
 * {@code logger-events} 主题，把单行 JSON 批次解码为 {@link LoggerEvent}
 * 写入 {@link LoggerEventStore} 并经 {@link LoggerEventBus} 分发（SSE 实时推送）。
 *
 * <p>发现中心可用时，经 {@link SyncServerRegistrar} 把本服务端注册到
 * {@code plugin.logger.server.service-name + scatter-id}（与客户端查询口径一致，
 * 注册地址取服务端实际绑定的 host，协议为 tcp）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class LoggerTcpServer implements SmartLifecycle {

    private final LoggerProperties properties;
    private final LoggerEventBus bus;
    private final LoggerEventStore store;
    private final ServiceDiscovery discovery;
    private final ObjectMapper mapper = new ObjectMapper();

    private TcpSyncServer server;
    private SyncServerRegistrar registrar;
    private volatile boolean running;

    /**
     * @param properties 模块配置
     * @param bus        事件总线
     * @param store      日志存储
     * @param discovery  服务发现，可为 null（不注册）
     */
    public LoggerTcpServer(LoggerProperties properties, LoggerEventBus bus,
                           LoggerEventStore store, ServiceDiscovery discovery) {
        this.properties = properties;
        this.bus = bus;
        this.store = store;
        this.discovery = discovery;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost(properties.getServer().getHost());
        setting.setPort(properties.getServer().getPort());
        TcpSyncServer tcp = new TcpSyncServer(setting);
        tcp.addListener(new SyncServerListener() {
            @Override
            public void onMessage(String clientId, String topic, Object message) {
                if (!TcpLoggerTransport.TOPIC.equals(topic) || message == null) {
                    return;
                }
                handle(String.valueOf(message));
            }
        });
        tcp.start();
        this.server = tcp;
        this.registrar = buildRegistrar();
        if (registrar != null) {
            registrar.register(tcp);
        }
        running = true;
        log.info("[logger] 日志接收端已启动: tcp://{}:{}, storage={}",
                properties.getServer().getHost(), properties.getServer().getPort(),
                properties.getStorage().getEngine());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (registrar != null && server != null) {
            registrar.unregister(server);
            registrar = null;
        }
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                log.debug("[logger] 接收端停止失败（忽略）: {}", e.getMessage());
            }
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 当前监听端口（供测试断言）
     *
     * @return 端口；未启动返回 -1
     */
    public int port() {
        TcpSyncServer current = server;
        return current == null ? -1 : current.getPort();
    }

    private void handle(String payload) {
        try {
            LoggerEvent[] events = mapper.readValue(payload, LoggerEvent[].class);
            if (events == null || events.length == 0) {
                return;
            }
            List<LoggerEvent> list = Arrays.asList(events);
            try {
                store.append(list);
            } catch (Exception e) {
                log.debug("[logger] 批次入库失败（不影响转发）: {}", e.getMessage());
            }
            bus.publish(list);
        } catch (Exception e) {
            log.debug("[logger] 收到无法解析的日志批次（忽略）: {}", e.getMessage());
        }
    }

    /**
     * 构建注册器：发现中心缺席时返回 null（不注册，靠显式 server-url / 回环寻址）。
     */
    private SyncServerRegistrar buildRegistrar() {
        if (discovery == null) {
            return null;
        }
        return SyncServerRegistrar.of(discovery,
                properties.getServer().getServiceName(), effectiveScatterId());
    }

    private String effectiveScatterId() {
        return StringUtils.isBlank(properties.getServer().getScatterId())
                ? properties.getServer().getServiceName() : properties.getServer().getScatterId();
    }
}

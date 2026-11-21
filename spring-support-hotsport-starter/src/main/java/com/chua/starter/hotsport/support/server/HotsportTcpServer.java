// [P3C A20 豁免] A20/A30/A31: 含日志/网络字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.hotsport.support.server;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.sync.impl.TcpSyncServer;
import com.chua.starter.hotsport.support.config.HotsportProperties;
import com.chua.starter.hotsport.support.service.ApmIngestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * APM 数据 TCP 接收端 — 仿 logger TCP 通道，供无法直连 HTTP 的采集端推送。
 *
 * <p>默认关闭（{@code plugin.hotsport.apm.tcp.enabled=false}）；启用后监听
 * {@code plugin.hotsport.apm.tcp.host:port}（默认 0.0.0.0:19392），主题
 * {@code plugin.hotsport.apm.tcp.topic}（默认 hotsport-events）。
 * 载荷与 HTTP {@code POST /api/apm/ingest} 完全一致（JSON：数组 / 单条 / 混合对象），
 * 经 {@link ApmIngestService} 解析落库，既有查询 / SSE 轮询自然可见。</p>
 *
 * <p>与 logger 的 TCP 接收端不同，这里没有事件总线 —— hotsport 的 SSE 是
 * 定时轮询存储快照，入库后下一轮轮询自然带出，无需额外分发。</p>
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.hotsport.apm.tcp", name = "enabled", havingValue = "true")
public class HotsportTcpServer implements SmartLifecycle {

    /**
     * 监听端口兜底值（配置非法时使用）。
    */
    private static final int DEFAULT_PORT = 19392;

    /**
     * 主题名兜底值。
    */
    private static final String DEFAULT_TOPIC = "hotsport-events";

    /**
     * Hotsport 配置（读取 plugin.hotsport.apm.tcp.*）。
    */
    private final HotsportProperties properties;

    /**
     * 上报接入服务（解析 + 落库）。
    */
    private final ApmIngestService ingestService;

    /**
     * JSON 解析（TCP 载荷）。
    */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * TCP 服务端。
    */
    private TcpSyncServer server;

    /**
     * 生命周期状态。
    */
    private volatile boolean running;

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        HotsportProperties.Tcp tcp = properties.getApm() == null
                ? null : properties.getApm().getTcp();
        String host = tcp == null || tcp.getHost() == null || tcp.getHost().isBlank()
                ? "0.0.0.0" : tcp.getHost().trim();
        int port = tcp == null || tcp.getPort() <= 0 ? DEFAULT_PORT : tcp.getPort();
        String topic = tcp == null || tcp.getTopic() == null || tcp.getTopic().isBlank()
                ? DEFAULT_TOPIC : tcp.getTopic().trim();
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost(host);
        setting.setPort(port);
        TcpSyncServer tcpServer = new TcpSyncServer(setting);
        tcpServer.addListener(new SyncServerListener() {
            @Override
            public void onMessage(String clientId, String msgTopic, Object message) {
                if (!topic.equals(msgTopic) || message == null) {
                    return;
                }
                handle(String.valueOf(message));
            }
        });
        tcpServer.start();
        this.server = tcpServer;
        running = true;
        log.info("[Hotsport] APM TCP 接收端已启动: tcp://{}:{}, topic={}", host, port, topic);
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                log.debug("[Hotsport] TCP 接收端停止失败（忽略）: {}", e.getMessage());
            }
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 当前监听端口（供测试断言）。
     *
     * @return 端口；未启动返回 -1
     */
    public int port() {
        TcpSyncServer current = server;
        return current == null ? -1 : current.getPort();
    }

    /**
     * 解析 TCP 载荷并入库；解析失败记 debug 后丢弃，不影响接收端存活。
     */
    private void handle(String payload) {
        try {
            JsonNode body = mapper.readTree(payload);
            Map<String, Object> result = ingestService.ingest(body);
            log.debug("[Hotsport] TCP 上报入库: {}", result);
        } catch (Exception e) {
            log.debug("[Hotsport] 收到无法解析的上报载荷（忽略）: {}", e.getMessage());
        }
    }
}

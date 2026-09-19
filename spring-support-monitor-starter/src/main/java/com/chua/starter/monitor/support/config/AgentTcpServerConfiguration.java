package com.chua.starter.monitor.support.config;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.sync.SyncMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent TCP 长连接服务端配置。
 *
 * <p>启动 TcpSyncServer，接收 agent 长连接上报。
 * agent 通过 TCP 长连接发送 {@code topic:message} 格式的消息，
 * 服务端按 topic 分发处理。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AgentTcpServerConfiguration {

    /** 服务端端口（默认 19390） */
    @Value("${monitor.agent.tcp-port:19390}")
    private int tcpPort;

    /**
     * 启动 TcpSyncServer。
     *
     * @return SyncServer Bean
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public SyncServer agentTcpSyncServer() {
        ServerSetting setting = ServerSetting.defaults();
        setting.setPort(tcpPort);
        setting.setHost("0.0.0.0");
        SyncServer server = new com.chua.common.support.network.sync.impl.TcpSyncServer(setting);
        // 注册消息处理器：agent-metrics topic
        server.addListener(new SyncServerListener() {
            @Override
            public void onClientConnected(String clientId, java.util.Map<String, Object> metadata) {
                log.info("[AgentTcpServer] agent 连接: {}", clientId);
            }

            @Override
            public void onClientDisconnected(String clientId) {
                log.info("[AgentTcpServer] agent 断开: {}", clientId);
            }

            @Override
            public void onMessage(String clientId, String topic, Object message) {
                log.debug("[AgentTcpServer] 收到消息 clientId={} topic={}", clientId, topic);
                // 这里后续可以调用 HostAgentController 的逻辑处理消息
                // 当前先打印日志
            }

            @Override
            public void onError(String clientId, Throwable cause) {
                log.warn("[AgentTcpServer] agent 错误 clientId={}: {}", clientId, cause.getMessage());
            }
        });
        log.info("[AgentTcpServer] 启动 TCP 服务端端口: {}", tcpPort);
        return server;
    }
}

package com.chua.starter.sync.support.datasync;

import com.chua.starter.datasync.agent.DataSyncAgentServer;
import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.server.SyncServer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.function.BiConsumer;

/**
 * DataSync Agent 生命周期桥接配置
 * <p>
 * 将 Spring Sync 的客户端连接/断开事件桥接到 DataSyncAgentServer：
 * - 客户端断开时，自动从 DataSyncAgentServer 注销对应 Agent
 * </p>
 *
 * @author CH
 * @since 2026/01/15
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "com.chua.starter.datasync.agent.DataSyncAgentServer")
@ConditionalOnProperty(prefix = "plugin.sync.datasync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSyncAgentLifecycleConfiguration {

    /**
     * 客户端能力标识：DataSync Agent
     */
    private static final String CAPABILITY_DATASYNC = "datasync";

    private final ObjectProvider<SyncServer> syncServerProvider;
    private final ObjectProvider<DataSyncAgentServer> dataSyncAgentServerProvider;

    public DataSyncAgentLifecycleConfiguration(ObjectProvider<SyncServer> syncServerProvider,
                                               ObjectProvider<DataSyncAgentServer> dataSyncAgentServerProvider) {
        this.syncServerProvider = syncServerProvider;
        this.dataSyncAgentServerProvider = dataSyncAgentServerProvider;
    }

    /**
     * 判断客户端是否声明了 DataSync 能力。
     *
     * @param clientInfo 客户端信息
     * @return 是否包含 datasync 能力标识
     */
    private boolean hasDatasyncCapability(ClientInfo clientInfo) {
        String[] capabilities = clientInfo.getClientCapabilities();
        if (capabilities == null) {
            return false;
        }
        for (String capability : capabilities) {
            if (CAPABILITY_DATASYNC.equals(capability)) {
                return true;
            }
        }
        return false;
    }

    @PostConstruct
    public void init() {
        SyncServer syncServer = syncServerProvider.getIfAvailable();
        DataSyncAgentServer agentServer = dataSyncAgentServerProvider.getIfAvailable();
        if (syncServer == null || agentServer == null) {
            log.info("[SyncDataSync] Agent 生命周期桥接未启用（SyncServer 或 DataSyncAgentServer 不存在）");
            return;
        }

        // 客户端断开时，注销对应 Agent
        syncServer.addDisconnectListener(new BiConsumer<String, ClientInfo>() {
            @Override
            public void accept(String sessionId, ClientInfo clientInfo) {
                String clientId = clientInfo != null ? clientInfo.getClientId() : null;
                if (clientId != null && hasDatasyncCapability(clientInfo)) {
                    log.info("[SyncDataSync] 客户端断开，注销 Agent: clientId={}, sessionId={}", clientId, sessionId);
                    agentServer.unregister(clientId);
                }
            }
        });

        log.info("[SyncDataSync] Agent 生命周期桥接已启用");
    }
}

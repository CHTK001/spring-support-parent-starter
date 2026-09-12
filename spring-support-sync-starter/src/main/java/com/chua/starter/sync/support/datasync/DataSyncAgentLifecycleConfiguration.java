package com.chua.starter.sync.support.datasync;

import com.chua.starter.datasync.agent.DataSyncAgentServer;
import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.server.SyncServer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.function.BiConsumer;

/**
* 数据同步 智能体 生命周期桥接配置
* <p>
* 将 Spring 同步 的客户端连接/断开事件桥接到 数据同步智能体服务端：
* - 客户端断开时，自动从 数据同步智能体服务端 注销对应 智能体
* </p>
*
* @since 2026/01/15
* @author CH
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.chua.starter.datasync.agent.DataSyncAgentServer")
@ConditionalOnProperty(prefix = "plugin.sync.datasync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSyncAgentLifecycleConfiguration {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(DataSyncAgentLifecycleConfiguration.class);

    /**
    * 客户端能力标识：数据同步 智能体
     */
    private static final String CAPABILITY_DATASYNC = "datasync";

    /** 同步服务器提供者 */
    private final ObjectProvider<SyncServer> syncServerProvider;
    /** 数据同步智能体服务器提供者 */
    private final ObjectProvider<DataSyncAgentServer> dataSyncAgentServerProvider;

    /**
    * 创建 数据同步智能体lifecycle配置 实例
    * @param syncServerProvider 同步服务端提供者
    * @param dataSyncAgentServerProvider 数据同步智能体服务端提供者
     */
    public DataSyncAgentLifecycleConfiguration(ObjectProvider<SyncServer> syncServerProvider,
                                               ObjectProvider<DataSyncAgentServer> dataSyncAgentServerProvider) {
        this.syncServerProvider = syncServerProvider;
        this.dataSyncAgentServerProvider = dataSyncAgentServerProvider;
    }

    /**
    * 判断客户端是否声明了 数据同步 能力。
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
    /** 初始化 */
    public void init() {
        SyncServer syncServer = syncServerProvider.getIfAvailable();
        DataSyncAgentServer agentServer = dataSyncAgentServerProvider.getIfAvailable();
        boolean enabled = syncServer != null && agentServer != null;
        com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry.getInstance().registerFlag(
                "sync-agent-bridge", "数据同步", enabled,
                "DataSync Agent 生命周期桥接", null);
        if (syncServer == null || agentServer == null) {
            log.info("[SyncDataSync] Agent 生命周期桥接未启用（SyncServer 或 DataSyncAgentServer 不存在）");
            return;
        }

 // 客户端断开时，注销对应 智能体
        syncServer.addDisconnectListener(new BiConsumer<String, ClientInfo>() {
            @Override
            /** Accept */
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

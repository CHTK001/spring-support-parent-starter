package com.chua.starter.gateway;

import com.chua.remote.support.agent.BaseRemoteAgent;
import com.chua.remote.support.agent.launch.AgentProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 网关客户端 Agent
 * 连接到网关并注册为 HTTP 服务，使网关可以转发 HTTP 请求到本应用
 * @author CH
 */
@Slf4j
public class GatewayClientAgent extends BaseRemoteAgent {

    public GatewayClientAgent(AgentProperties properties) {
        super(properties);
    }

    @Override
    protected void handleConnect(String sessionId, String protocol,
                                  Map<String, Object> target, Map<String, Object> auth) {
        // HTTP 协议的请求由网关直接转发到本应用的 HTTP 端口，不需要 Agent 处理
        log.debug("[GatewayClient] 收到连接请求（忽略）: sessionId={} protocol={}", sessionId, protocol);
    }

    @Override
    protected void handleDisconnect(String sessionId) {
        log.debug("[GatewayClient] 收到断开请求: sessionId={}", sessionId);
    }

    @Override
    protected void handleInput(String sessionId, String type, Map<String, Object> payload) {
        // HTTP 协议无输入处理
    }

    @Override
    protected void onRegistered() {
        log.info("[GatewayClient] 已注册到网关, agentId={}", properties.getAgentId());
    }
}


package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.vo.AgentPushVO;

/**
 * Agent 上报的历史指标写入 SPI。
 *
 * <p>主机上报通道（{@code /ms/server/host/agent/{agentKey}/push}）属于资产台账域，
 * 只负责回写主机当前值；历史时序点属于监控域，由 {@code spring-support-monitor-starter}
 * 提供实现，避免服务端启动器反向依赖监控启动器。未接入监控模块时扩展点为空，上报仅回写台账。</p>
 *
 * <p>实现方按主机解析其归属的监控项后写历史；解析不到应自行跳过并告警，不得抛异常影响上报。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface HostAgentHistoryWriter {

    /**
     * 把一次 Agent 上报的实时指标落成历史时序点。
     *
     * @param host    已回写台账的主机
     * @param payload 上报负载
     */
    void write(MsServerHost host, AgentPushVO payload);
}

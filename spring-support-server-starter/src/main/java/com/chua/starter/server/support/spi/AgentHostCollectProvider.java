package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Agent 主动上报采集 Provider（AGENT）
 * <p>
 * 目标主机上的 Agent 主动上报硬件/状态数据，服务端按 agentKey 定位主机并回写。
 * 不主动发起 SSH，适用于无法从服务端直连（NAT / 防火墙）的主机。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentHostCollectProvider implements HostCollectProvider {

    /** 主机 Mapper */
    private final MsServerHostMapper hostMapper;

    @Override
    public HostCollectType type() {
        return HostCollectType.AGENT;
    }

    @Override
    public CollectOutcome collect(HostCollectContext context) {
        if (context == null || context.getHost() == null || context.getHost().getId() == null) {
            return CollectOutcome.fail("主机数据不完整");
        }
        try {
            MsServerHost host = context.getHost();
            host.setServerHostOnline(1);
            host.setServerHostOnlineTime(LocalDateTime.now());
            hostMapper.updateById(host);
            return CollectOutcome.ok("Agent 上报已回写");
        } catch (Exception e) {
            log.warn("[collect] AGENT 回写失败 hostId={}: {}",
                    context.getHost().getId(), e.getMessage());
            return CollectOutcome.fail(e.getMessage());
        }
    }
}

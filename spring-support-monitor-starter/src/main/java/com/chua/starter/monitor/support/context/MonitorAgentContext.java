package com.chua.starter.monitor.support.context;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
* 监控目标探针上下文
* <p>
* 内存维护每个监控目标(智能体)的最后心跳时间，供在线判定使用。
* 判定规则：距最后一次心跳超过 {@code timeoutMinutes} 即视为离线；
* 从未上报过心跳的目标不纳入本上下文（视为从未上线）。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Component
public class MonitorAgentContext {

    /** 默认离线判定阈值（分钟） */
    private static final int DEFAULT_OFFLINE_MINUTES = 5;

    /** 最后心跳时间表: 监控标识 -> 心跳时间 */
    private final Map<Long, LocalDateTime> lastHeartbeatMap = new ConcurrentHashMap<>();

    /** 离线状态表: 监控标识 -> 是否已判定离线（用于状态翻转检测） */
    private final Map<Long, Boolean> offlineFlagMap = new ConcurrentHashMap<>();

    /**
    * 标记目标心跳活跃
    *
    * @param monitorId 目标 标识
     */
    public void markActive(Long monitorId) {
        if (monitorId == null) {
            return;
        }
        lastHeartbeatMap.put(monitorId, LocalDateTime.now());
        offlineFlagMap.put(monitorId, false);
    }

    /**
    * 判断目标当前是否在线
    *
    * @param monitorId 目标 标识
    * @return true 表示在超时窗口内有心跳
     */
    public boolean isOnline(Long monitorId) {
        LocalDateTime last = lastHeartbeatMap.get(monitorId);
        return last != null && ChronoUnit.MINUTES.between(last, LocalDateTime.now()) < DEFAULT_OFFLINE_MINUTES;
    }

    /**
    * 扫描并翻转离线状态（由定时任务调用）
    *
    * @return 本轮新转为离线的目标 标识 列表
     */
    public List<Long> sweepOffline() {
        List<Long> newlyOffline = new ArrayList<>();
        for (Map.Entry<Long, LocalDateTime> entry : lastHeartbeatMap.entrySet()) {
            boolean offline = ChronoUnit.MINUTES.between(entry.getValue(), LocalDateTime.now()) >= DEFAULT_OFFLINE_MINUTES;
            Boolean previous = offlineFlagMap.getOrDefault(entry.getKey(), false);
            if (offline && !previous) {
                offlineFlagMap.put(entry.getKey(), true);
                newlyOffline.add(entry.getKey());
            }
        }
        return newlyOffline;
    }

    /**
    * 全量状态快照
    *
    * @return monitorId -> [最后一个心跳, online]
     */
    public List<AgentStatus> snapshot() {
        List<AgentStatus> list = new ArrayList<>(lastHeartbeatMap.size());
        for (Map.Entry<Long, LocalDateTime> entry : lastHeartbeatMap.entrySet()) {
            boolean online = ChronoUnit.MINUTES.between(entry.getValue(), LocalDateTime.now()) < DEFAULT_OFFLINE_MINUTES;
            list.add(new AgentStatus(entry.getKey(), entry.getValue(), online));
        }
        return list;
    }

    /**
    * 探针在线状态视图
    *
    * @param monitorId     目标 标识
    * @param lastHeartbeat 最后心跳时间
    * @param online        是否在线
    * @return 智能体状态的结果
     */
    public record AgentStatus(Long monitorId, LocalDateTime lastHeartbeat, boolean online) {
    }
}

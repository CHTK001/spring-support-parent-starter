package com.chua.starter.monitor.support.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.asset.model.AssetDetailView;
import com.chua.starter.monitor.support.asset.model.AssetDetailView.NodeChannel;
import com.chua.starter.monitor.support.asset.model.AssetDetailView.MonitorItem;
import com.chua.starter.monitor.support.asset.model.AssetSearchItemVO;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmEvent;
import com.chua.starter.monitor.support.mapper.MsMonitorAlarmEventMapper;
import com.chua.starter.monitor.support.service.MonitorService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.server.model.ServerNodeView;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 资产联动服务 —— 聚合「主机台账 + 服务节点 + 通道状态」。
 *
 * <p>把资产监控系统的两个核心概念（资产 inventory 与协议服务节点）打通：
 * 按主机查询其关联的服务节点，聚合在线/采集/Agent/远控等通道状态，
 * 供监控台「资产详情 / 链路总览」一次性获取，避免前端拉多处接口再拼接。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetLinkageService {

    /**
     * 资产台账服务（来自 spring-支持-服务端-启动）
    */
    private final ServerHostService serverHostService;
    /**
     * 服务节点查询门面（含运行态）
    */
    private final ServerManageService serverManageService;
    /**
    */
    /**
     * 监控项 服务，主机↔监控归属解析出口
    */
    private final MonitorService monitorService;
    /**
     * 告警事件 映射器
    */
    private final MsMonitorAlarmEventMapper alarmEventMapper;

    /**
     * 按主机 标识 聚合资产详情。
     *
     * @param hostId 主机 标识
     * @return 资产聚合详情；主机不存在返回 数据=空（编码 仍 00000）
     */
    public ReturnResult<AssetDetailView> assetDetail(Long hostId) {
        try {
            ReturnResult<MsServerHost> hostResult = serverHostService.get(hostId);
            MsServerHost host = hostResult != null ? hostResult.getData() : null;
            if (host == null) {
                return ReturnResult.ok(null);
            }
            return ReturnResult.ok(buildView(host));
        } catch (Exception e) {
            log.warn("[AssetLinkage] 资产聚合查询失败: hostId={} - {}", hostId, e.getMessage());
            return ReturnResult.ok(null);
        }
    }

    /**
     * 按关键字列出资产（含简化的在线/通道状态），供监控台搜索、导航。
     *
     * @param keyword 关键字（名称/IP 模糊，可空）
     * @return 资产简表列表
     */
    public ReturnResult<List<AssetSearchItemVO>> searchAssets(String keyword) {
        try {
            ReturnResult<List<MsServerHost>> q = serverHostService.query(keyword, null, null);
            List<MsServerHost> hosts = q != null ? q.getData() : null;
            List<AssetSearchItemVO> result = new ArrayList<>();
            if (hosts != null) {
                for (MsServerHost h : hosts) {
                    result.add(new AssetSearchItemVO(h.getId(), h.getServerHostName(),
                            h.getServerHostAddress(), h.getServerHostPort(), h.getServerHostOsType(),
                            h.getServerHostOnline(), toBoolean(h.getServerHostCollectEnabled()),
                            toBoolean(h.getServerHostAgentEnabled()), toBoolean(h.getServerHostRemoteEnabled())));
                }
            }
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.warn("[AssetLinkage] 资产搜索失败: keyword={} - {}", keyword, e.getMessage());
            return ReturnResult.ok(new ArrayList<>());
        }
    }

    /**
     * 构建资产聚合视图。
     *
     * @param host 主机台账
     * @return 聚合视图
     */
    private AssetDetailView buildView(MsServerHost host) {
        AssetDetailView view = new AssetDetailView()
                .setHostId(host.getId())
                .setName(host.getServerHostName())
                .setAddress(host.getServerHostAddress())
                .setPort(host.getServerHostPort())
                .setOsType(host.getServerHostOsType())
                .setOnline(host.getServerHostOnline())
                .setOnlineTime(host.getServerHostOnlineTime() == null
                        ? null : host.getServerHostOnlineTime().toString())
                .setCollectEnabled(toBoolean(host.getServerHostCollectEnabled()))
                .setAgentEnabled(toBoolean(host.getServerHostAgentEnabled()))
                .setRemoteEnabled(toBoolean(host.getServerHostRemoteEnabled()));

        List<ServerNodeView> nodes = loadNodesForHost(host);
        view.setNodeCount(nodes.size());
        List<NodeChannel> channels = new ArrayList<>();
        int running = 0;
        for (ServerNodeView node : nodes) {
            NodeChannel ch = toChannel(node);
            if ("RUNNING".equals(ch.getStatus())) {
                running++;
            }
            channels.add(ch);
        }
        view.setChannels(channels);
        view.setRunningCount(running);

        // 资产 → 监控项 → 告警 聚合
        /**
         * aggregate监控和报警器。
         * @param view view
         * @param host 主机
         */
        aggregateMonitorsAndAlarms(view, host);
        return view;
    }

    /**
     * 聚合该资产关联的监控项及其活跃告警。
     *
     * @param view 资产视图（就地填充）
     * @param host 主机台账
     */
    private void aggregateMonitorsAndAlarms(AssetDetailView view, MsServerHost host) {
        List<MsMonitor> monitors = loadMonitorsForHost(host);
        if (monitors.isEmpty()) {
            view.setMonitorCount(0);
            view.setActiveAlarmCount(0);
            view.setMonitors(new ArrayList<>());
            return;
        }
        List<Long> monitorIds = monitors.stream().map(MsMonitor::getId).collect(Collectors.toList());
        // 一次查出这些监控项的全部告警事件
        List<MsMonitorAlarmEvent> alarms = alarmEventMapper.selectList(
                new LambdaQueryWrapper<MsMonitorAlarmEvent>()
                        .in(MsMonitorAlarmEvent::getMsMonitorAlarmEventMonitorId, monitorIds));
 // 按 监控标识 归集活跃告警
        Map<Long, List<MsMonitorAlarmEvent>> byMonitor = alarms == null ? Map.of()
                : alarms.stream().collect(Collectors.groupingBy(MsMonitorAlarmEvent::getMsMonitorAlarmEventMonitorId));

        List<MonitorItem> items = new ArrayList<>();
        int totalActive = 0;
        for (MsMonitor m : monitors) {
            List<MsMonitorAlarmEvent> active = byMonitor.getOrDefault(m.getId(), new ArrayList<>())
                    .stream().filter(a -> !isProcessed(a)).collect(Collectors.toList());
            totalActive += active.size();
            MonitorItem item = new MonitorItem()
                    .setMonitorId(m.getId())
                    .setName(m.getMonitorName())
                    .setHost(m.getMonitorHost())
                    .setServiceType(m.getServiceType())
                    .setEnabled(toBoolean(m.getMonitorEnabled()))
                    .setActiveAlarmCount(active.size())
                    .setMaxLevel(maxLevel(active));
            items.add(item);
        }
        view.setMonitors(items);
        view.setMonitorCount(items.size());
        view.setActiveAlarmCount(totalActive);
    }

    /**
     * 取关联该主机的监控项，归属口径统一由 {@link MonitorService#listByServerHost} 决定。
     *
     * @param host 主机
     * @return 监控项列表
     */
    private List<MsMonitor> loadMonitorsForHost(MsServerHost host) {
        if (host.getId() == null) {
            return new ArrayList<>();
        }
        List<MsMonitor> monitors = monitorService.listByServerHost(host.getId()).getData();
        return monitors == null ? new ArrayList<>() : monitors;
    }

    /**
     * 告警是否已处理（新 之外的状态视为已结束）。
     *
     * @param alarm 告警事件
     * @return true 已处理
     */
    private static boolean isProcessed(MsMonitorAlarmEvent alarm) {
        String s = alarm.getMsMonitorAlarmEventStatus();
        return s != null && !"NEW".equalsIgnoreCase(s) && !"ACTIVE".equalsIgnoreCase(s);
    }

    /**
     * 取未处理告警中的最高级别（P0 最高）。
     *
     * @param alarms 未处理告警
     * @return 最高级别；无则 空
     */
    private static String maxLevel(List<MsMonitorAlarmEvent> alarms) {
        if (CollectionUtils.isEmpty(alarms)) {
            return null;
        }
        String max = null;
        for (MsMonitorAlarmEvent a : alarms) {
            String lv = a.getMsMonitorAlarmEventLevel();
            if (lv == null || lv.isBlank()) {
                continue;
            }
            if (max == null || severity(lv) < severity(max)) {
                max = lv;
            }
        }
        return max;
    }

    /**
     * 告警级别严重度（P0=0 最高，P4=4最低；未知按 P9）。
     *
     * @param level 级别
     * @return 严重度数字
     */
    private static int severity(String level) {
        try {
            String digit = level.trim().substring(1);
            int v = Integer.parseInt(digit);
            return Math.max(0, Math.min(v, 4));
        } catch (Exception e) {
            return 9;
        }
    }

    /**
     * 从库里取挂在该主机 IP 上的全部服务节点（无论是否运行）。
     *
     * @param host 主机
     * @return 节点列表
     */
    private List<ServerNodeView> loadNodesForHost(MsServerHost host) {
        String address = host.getServerHostAddress();
        if (address == null || address.isBlank()) {
            return new ArrayList<>();
        }
 // 主机 字段可能存 IP 或域名字面量；兼容主机 地址/公共IP/私有IP
        List<String> candidates = new ArrayList<>();
        candidates.add(address.trim());
        if (host.getServerHostPublicIp() != null && !host.getServerHostPublicIp().isBlank()) {
            candidates.add(host.getServerHostPublicIp().trim());
        }
        if (host.getServerHostPrivateIp() != null && !host.getServerHostPrivateIp().isBlank()) {
            candidates.add(host.getServerHostPrivateIp().trim());
        }
        ReturnResult<List<ServerNodeView>> result = serverManageService.list();
        if (result == null || !result.isOk() || result.getData() == null) {
            return new ArrayList<>();
        }
        return result.getData().stream()
                .filter(node -> candidates.contains(node.host()))
                .sorted(java.util.Comparator.comparing(ServerNodeView::id))
                .toList();
    }

    /**
     * 把节点定义为通道摘要，并叠加运行时真实状态。
     *
     * @param node 节点定义
     * @return 通道摘要
     */
    private NodeChannel toChannel(ServerNodeView node) {
        NodeChannel ch = new NodeChannel()
                .setNodeId(node.id())
                .setName(node.name())
                .setProtocol(node.protocol())
                .setPort(node.port());
        if ("RUNNING".equals(node.status()) && node.serverUrl() != null) {
            ch.setUrl(node.serverUrl());
        }
        ch.setStatus(node.status());
        ch.setExtra(node.config());
        return ch;
    }

    /**
     * 0/1/空 → 布尔值。
     *
     * @param flag 原始整型开关
     * @return 布尔；null 视为 false
     */
    private static Boolean toBoolean(Integer flag) {
        return flag != null && flag == 1;
    }
}

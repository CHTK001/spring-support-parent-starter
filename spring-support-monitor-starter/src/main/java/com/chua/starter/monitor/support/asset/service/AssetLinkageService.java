package com.chua.starter.monitor.support.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.asset.model.AssetDetailView;
import com.chua.starter.monitor.support.asset.model.AssetDetailView.NodeChannel;
import com.chua.starter.monitor.support.entity.MsServerNode;
import com.chua.starter.monitor.support.mapper.MsServerNodeMapper;
import com.chua.starter.monitor.support.server.ServerNodeRegistry;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.service.ServerHostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    /** 资产台账服务（来自 spring-support-server-starter） */
    private final ServerHostService serverHostService;
    /** 服务节点 Mapper */
    private final MsServerNodeMapper nodeMapper;
    /** 服务节点运行时注册表 */
    private final ServerNodeRegistry nodeRegistry;

    /**
     * 按主机 ID 聚合资产详情。
     *
     * @param hostId 主机 ID
     * @return 资产聚合详情；主机不存在返回 data=null（code 仍 00000）
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
    public ReturnResult<List<Map<String, Object>>> searchAssets(String keyword) {
        try {
            ReturnResult<List<MsServerHost>> q = serverHostService.query(keyword, null, null);
            List<MsServerHost> hosts = q != null ? q.getData() : null;
            List<Map<String, Object>> result = new ArrayList<>();
            if (hosts != null) {
                for (MsServerHost h : hosts) {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("hostId", h.getId());
                    item.put("name", h.getServerHostName());
                    item.put("address", h.getServerHostAddress());
                    item.put("port", h.getServerHostPort());
                    item.put("osType", h.getServerHostOsType());
                    item.put("online", h.getServerHostOnline());
                    item.put("collectEnabled", h.getServerHostCollectEnabled());
                    item.put("agentEnabled", h.getServerHostAgentEnabled());
                    item.put("remoteEnabled", h.getServerHostRemoteEnabled());
                    result.add(item);
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

        List<MsServerNode> nodes = loadNodesForHost(host);
        view.setNodeCount(nodes.size());
        List<NodeChannel> channels = new ArrayList<>();
        int running = 0;
        for (MsServerNode node : nodes) {
            NodeChannel ch = toChannel(node);
            if ("RUNNING".equals(ch.getStatus())) {
                running++;
            }
            channels.add(ch);
        }
        view.setChannels(channels);
        view.setRunningCount(running);
        return view;
    }

    /**
     * 从库里取挂在该主机 IP 上的全部服务节点（无论是否运行）。
     *
     * @param host 主机
     * @return 节点列表
     */
    private List<MsServerNode> loadNodesForHost(MsServerHost host) {
        String address = host.getServerHostAddress();
        if (address == null || address.isBlank()) {
            return new ArrayList<>();
        }
        // host 字段可能存 IP 或域名字面量；兼容主机 address/公共IP/私有IP
        List<String> candidates = new ArrayList<>();
        candidates.add(address.trim());
        if (host.getServerHostPublicIp() != null && !host.getServerHostPublicIp().isBlank()) {
            candidates.add(host.getServerHostPublicIp().trim());
        }
        if (host.getServerHostPrivateIp() != null && !host.getServerHostPrivateIp().isBlank()) {
            candidates.add(host.getServerHostPrivateIp().trim());
        }
        return nodeMapper.selectList(new LambdaQueryWrapper<MsServerNode>()
                .in(MsServerNode::getHost, candidates)
                .orderByAsc(MsServerNode::getId));
    }

    /**
     * 把节点定义为通道摘要，并叠加运行时真实状态。
     *
     * @param node 节点定义
     * @return 通道摘要
     */
    private NodeChannel toChannel(MsServerNode node) {
        NodeChannel ch = new NodeChannel()
                .setNodeId(node.getId())
                .setName(node.getName())
                .setProtocol(node.getProtocol())
                .setPort(node.getPort());
        String url = buildUrl(node);
        ServerNodeRegistry.RuntimeServer runtime = nodeRegistry.get(node.getId());
        String status = runtime == null ? "STOPPED" : runtime.status();
        if ("RUNNING".equals(status) && url != null) {
            ch.setUrl(url);
        }
        ch.setStatus(status);
        ch.setExtra(nodeRegistry.parseConfig(node));
        return ch;
    }

    /**
     * 构造节点访问地址（仅对已运行的 HTTP 节点给出完整 URL）。
     *
     * @param node 节点
     * @return 访问地址；未知协议返回 null
     */
    private String buildUrl(MsServerNode node) {
        if (node.getPort() == null) {
            return null;
        }
        String protocol = node.getProtocol();
        if ("HTTP".equalsIgnoreCase(protocol)) {
            return "http://" + node.getHost() + ":" + node.getPort() + "/";
        }
        // proxy/sip 仅给出 tcp://host:port 形式提示
        return null;
    }

    /**
     * 0/1/null → Boolean。
     *
     * @param flag 原始整型开关
     * @return 布尔；null 视为 false
     */
    private static Boolean toBoolean(Integer flag) {
        return flag != null && flag == 1;
    }
}

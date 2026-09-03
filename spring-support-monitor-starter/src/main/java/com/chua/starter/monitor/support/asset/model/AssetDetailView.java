package com.chua.starter.monitor.support.asset.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 资产聚合详情视图。
 *
 * <p>把一个资产（主机台账）与其在监控侧关联的资源聚合为一个视图：
 * 基础信息 + 在线/采集/远控通道状态 + 挂在该资产 IP 上的服务节点 + 协议端点清单。
 * 用于监控台「资产详情 / 链路总览」，避免分别查询台账与节点再手工拼装。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Accessors(chain = true)
public class AssetDetailView {

    /** 资产 ID（对应主机台账 MsServerHost.id） */
    private Long hostId;
    /** 资产名称 */
    private String name;
    /** 资产 IP/域名 */
    private String address;
    /** 资产 SSH/默认端口 */
    private Integer port;
    /** 操作系统类型 */
    private String osType;
    /** 在线状态：1=在线 0=离线 */
    private Integer online;
    /** 在线时间 */
    private String onlineTime;

    /** 采集通道：是否开启采集 */
    private Boolean collectEnabled;
    /** Agent 通道：是否开启 agent */
    private Boolean agentEnabled;
    /** 远控通道：是否开启 guacd 远控 */
    private Boolean remoteEnabled;

    /** 服务节点数量 */
    private Integer nodeCount;
    /** 运行中的服务节点数量 */
    private Integer runningCount;
    /** 服务节点摘要列表 */
    private List<NodeChannel> channels;

    /**
     * 单个服务节点 / 通道摘要。
     */
    @Data
    @Accessors(chain = true)
    public static class NodeChannel {
        /** 节点 ID */
        private Long nodeId;
        /** 节点名称 */
        private String name;
        /** 协议 */
        private String protocol;
        /** 监听端口 */
        private Integer port;
        /** 运行状态：RUNNING / STOPPED / ERROR */
        private String status;
        /** 访问地址 */
        private String url;
        /** 附加信息 */
        private Map<String, Object> extra;
    }
}

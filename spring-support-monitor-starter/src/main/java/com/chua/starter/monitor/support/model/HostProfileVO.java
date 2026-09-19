package com.chua.starter.monitor.support.model;

import com.chua.starter.server.support.entity.MsServerHost;
import lombok.Data;

/**
 * 主机画像概览视图。
 *
 * <p>{@code /ms/server/host/profile/{hostId}} 的返回结构：概览与实时指标由所选采集通道
 * （Agent / SSH / WinRM / 本机 OSHI）取得，指标由同一次采集的基础信息与磁盘结果推导，
 * 因此四条通道的画像页完全同构。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class HostProfileVO {

    /**
     * 主机基础信息
     */
    private HostInfoVO info;

    /**
     * 实时资源指标
     */
    private HostMetricsVO metrics;

    /**
     * 主机台账（含最近一次回写的硬件与用量字段）
     */
    private MsServerHost host;

    /**
     * Agent 端口，非 Agent 接入时为 0
     */
    private int port;

    /**
     * 台账 osType 与实测平台不一致时的提示，一致时为 null
     */
    private String osMismatch;

    /**
     * 本次采集时间（ISO 文本）
     */
    private String collectedAt;
}

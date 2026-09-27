package com.chua.starter.monitor.support.asset.model;

/**
 * 资产检索行 —— 监控台搜索/导航用的资产简表。
 *
 * @param hostId         主机标识
 * @param name           主机名称
 * @param address        主机地址
 * @param port           SSH 端口
 * @param osType         操作系统类型
 * @param online         在线状态：1 在线 0 离线 -1 未知
 * @param collectEnabled 硬件采集开关
 * @param agentEnabled   Agent 上报开关
 * @param remoteEnabled  远控开关
 * @author CH
 * @since 4.0.0.42
 */
public record AssetSearchItemVO(
        Long hostId,
        String name,
        String address,
        Integer port,
        String osType,
        Integer online,
        Boolean collectEnabled,
        Boolean agentEnabled,
        Boolean remoteEnabled
) {
}

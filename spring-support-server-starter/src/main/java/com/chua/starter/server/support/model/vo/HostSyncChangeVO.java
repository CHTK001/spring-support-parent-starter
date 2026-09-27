package com.chua.starter.server.support.model.vo;

/**
 * 单台主机的资产同步变更明细
 * <p>
 * 仅当探测到的公网 IP 或内网 IP 与库中不一致时才产生一条记录；键名与历史 Map 载荷一致。
 * </p>
 *
 * @param hostId    主机主键
 * @param hostName  主机名称
 * @param publicIp  同步后的公网 IP
 * @param privateIp 同步后的内网 IP
 * @author CH
 * @since 4.0.0.42
 */
public record HostSyncChangeVO(
        Long hostId,
        String hostName,
        String publicIp,
        String privateIp
) {
}

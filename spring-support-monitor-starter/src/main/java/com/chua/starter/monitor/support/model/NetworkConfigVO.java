package com.chua.starter.monitor.support.model;

/**
 * 网络配置项（IP / 网关 / DNS）。
 *
 * <p>目标主机走 query 参数 {@code hostId}，本对象只承载配置项。命令拼装由
 * {@link com.chua.starter.monitor.support.spi.SystemInfoProvider#applyNetworkConfig} 的平台实现负责。</p>
 *
 * @param ifname  网卡名（Linux NetworkManager 连接名），必填
 * @param ipMode  取址方式，{@code static} 为静态，其余按 dhcp 处理
 * @param ip      静态 IP，静态模式下必填
 * @param mask    子网掩码（点分或前缀长度），为空按 /24
 * @param gateway 默认网关，可为空
 * @param dnsMode DNS 取址方式，仅前端回显
 * @param dns1    主 DNS，可为空
 * @param dns2    备 DNS，可为空
 * @author CH
 * @since 4.0.0.42
 */
public record NetworkConfigVO(String ifname, String ipMode, String ip, String mask,
                              String gateway, String dnsMode, String dns1, String dns2) {

    /**
     * 是否为静态取址。
     *
     * @return 静态返回 true
     */
    public boolean staticMode() {
        return "static".equalsIgnoreCase(ipMode == null ? "" : ipMode.trim());
    }
}

package com.chua.starter.monitor.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * IPv4 配置（参考 Windows「Internet 协议版本 4 (TCP/IPv4) 属性」）。
 *
 * <p>读取时回填当前配置；下发时：{@code dhcp=true} 改为自动获取 IP 与 DNS，
 * {@code dhcp=false} 使用静态地址 / 前缀 / 网关 / DNS。</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 4.0.0.51
 */
@Data
@Schema(description = "IPv4 配置")
public class IPv4ConfigVO {

    /**
     * 网卡名称
     */
    @Schema(description = "网卡名称")
    private String adapterName;

    /**
     * 接口索引
     */
    @Schema(description = "接口索引")
    private Integer ifIndex;

    /**
     * true=自动获得 IP 地址(DHCP)；false=使用下面的静态 IP
     */
    @Schema(description = "是否自动获取(DHCP)")
    private Boolean dhcp = true;

    /**
     * 静态 IP 地址
     */
    @Schema(description = "IP 地址")
    private String address;

    /**
     * 子网前缀长度（如 24）
     */
    @Schema(description = "子网前缀长度")
    // [P3C A20 豁免]
    private Integer prefixLength = 24;

    /**
     * 默认网关
     */
    @Schema(description = "默认网关")
    private String gateway;

    /**
     * true=自动获得 DNS；false=使用首选/备用 DNS
     */
    @Schema(description = "DNS 是否自动获取")
    private Boolean dnsDhcp = true;

    /**
     * 首选 DNS 服务器
     */
    @Schema(description = "首选 DNS")
    private String dnsPreferred;

    /**
     * 备用 DNS 服务器
     */
    @Schema(description = "备用 DNS")
    private String dnsAlternate;
}

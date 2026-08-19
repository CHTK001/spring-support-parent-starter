package com.chua.common.starter.support.network.quality;

/**
 * IP 质量检测器
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IpQualityDetector {

    /**
     * 检测 IP 质量
     *
     * @param ip IP 地址
     * @return IP 质量信息
     */
    IpQuality detect(String ip);

    /**
     * 批量检测 IP 质量
     *
     * @param ips IP 地址列表
     * @return IP 质量信息列表
     */
    IpQuality[] detect(String[] ips);
}
package com.chua.common.support.network.net;

import java.net.URI;

/**
 * 网络地址封装类，提供 URL 地址解析功能
 *
 * @author CH
 */
public class NetAddress {
    /**
     * 解析后的网络地址（主机名或 IP）
     */
    private final String address;

    /**
     * 私有构造函数，通过 {@link #of(String)} 工厂方法创建实例
     *
     * @param address 网络地址字符串
     */
    private NetAddress(String address) {
        this.address = address;
    }

    /**
     * 从 URL 字符串中解析并创建 NetAddress 实例
     * <p>内部通过 {@link URI} 解析 URL，提取主机部分（host）。如果解析失败则直接使用原始字符串。</p>
     *
     * @param url URL 字符串
     * @return NetAddress 实例，url 为 null 时返回地址为 "unknown"
     */
    public static NetAddress of(String url) {
        if (url == null) return new NetAddress("unknown");
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return new NetAddress(host != null ? host : url);
        } catch (Exception e) {
            return new NetAddress(url);
        }
    }

    /**
     * 获取解析后的网络地址
     *
     * @return 主机名或 IP 地址字符串
     */
    public String getAddress() {
        return address;
    }
}

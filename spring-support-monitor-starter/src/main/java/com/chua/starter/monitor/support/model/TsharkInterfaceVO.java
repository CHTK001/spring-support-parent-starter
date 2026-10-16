package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * 一个可抓包的网卡。
 *
 * <p><strong>为什么不能只回 tshark 的 {@code -D} 名字</strong>：Windows 上 tshark 输出的名字是
 * {@code \Device\NPF_{EFFB110A-D387-...}} 这种 NPF 设备 GUID，人根本读不出来哪个是
 * 「以太网 3」、哪个是蓝牙、哪个才是本机在用的那张。友好名在它后面的括号里
 * （{@code 1. \Device\NPF_{...} (以太网 3)}），必须一起带出来。</p>
 *
 * @param name        tshark 的设备名，抓包时要用它（可能就是上面那串 GUID）
 * @param description 友好名，如「以太网 3」；解析不出为空串
 * @param ipv4        该网卡在本机的 IPv4 地址（从 JDK {@code NetworkInterface} 反查），无则空列表
 * @param local       是否承载本机默认出口地址（最可能就是用户要抓的那张）
 * @author CH
 * @since 4.0.0.42
 */
public record TsharkInterfaceVO(String name, String description, List<String> ipv4, boolean local) {
}
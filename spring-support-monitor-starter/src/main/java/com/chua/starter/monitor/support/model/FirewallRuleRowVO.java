package com.chua.starter.monitor.support.model;

/**
 * 防火墙规则行视图。
 *
 * <p>Linux 来自 {@code iptables -L -n}（chain/target/source/destination 一段），
 * Windows 来自 {@code Get-NetFirewallRule}（名称/方向/动作/作用域一段）；
 * 两侧各有取不到的列，留空即表示该平台无此维度。</p>
 *
 * @param name        规则名（Windows DisplayName，Linux 无则为空）
 * @param chain       规则链
 * @param direction   方向（Inbound / Outbound）
 * @param action      动作（Allow / Deny / ACCEPT / DROP …）
 * @param enabled     是否启用
 * @param profile     生效网络作用域（Domain / Private / Public）
 * @param num         规则序号（仅 iptables 有行号，Windows 规则为 null）
 * @param protocol    协议
 * @param localPort   本地端口
 * @param remotePort  远端端口
 * @param target      目标（iptables TARGET 列）
 * @param source      源地址
 * @param destination 目的地址
 * @param info        附加信息（iptables 选项列）
 * @author CH
 * @since 4.0.0.42
 */
public record FirewallRuleRowVO(
        String name,
        String chain,
        String direction,
        String action,
        String enabled,
        String profile,
        Integer num,
        String protocol,
        String localPort,
        String remotePort,
        String target,
        String source,
        String destination,
        String info) {
}

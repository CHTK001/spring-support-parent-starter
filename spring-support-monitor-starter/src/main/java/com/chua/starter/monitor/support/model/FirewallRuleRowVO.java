package com.chua.starter.monitor.support.model;

import lombok.Data;

/**
 * 防火墙规则行视图。
 *
 * <p>Linux 来自 {@code iptables -L -n}（chain/target/source/destination 一段），
 * Windows 来自 {@code Get-NetFirewallRule}（名称/方向/动作/作用域一段）；
 * 两侧各有取不到的列，留空即表示该平台无此维度。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class FirewallRuleRowVO {

    /**
     * 规则名（Windows DisplayName，Linux 无则为空）
     */
    private String name;

    /**
     * 规则链
     */
    private String chain;

    /**
     * 方向（Inbound / Outbound）
     */
    private String direction;

    /**
     * 动作（Allow / Deny / ACCEPT / DROP …）
     */
    private String action;

    /**
     * 是否启用
     */
    private String enabled;

    /**
     * 生效网络作用域（Domain / Private / Public）
     */
    private String profile;

    /**
     * 规则序号（仅 iptables 有行号，Windows 规则为 null）
     */
    private Integer num;

    /**
     * 协议
     */
    private String protocol;

    /**
     * 本地端口
     */
    private String localPort;

    /**
     * 远端端口
     */
    private String remotePort;

    /**
     * 目标（iptables TARGET 列）
     */
    private String target;

    /**
     * 源地址
     */
    private String source;

    /**
     * 目的地址
     */
    private String destination;

    /**
     * 附加信息（iptables 选项列）
     */
    private String info;
}

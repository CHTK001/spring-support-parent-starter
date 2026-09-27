package com.chua.starter.monitor.support.model;

/**
 * 防火墙规则写入入参。
 *
 * @param name      规则显示名，必填
 * @param direction 方向：{@code in} 入站 / {@code out} 出站，默认 in
 * @param action    动作：{@code allow} 放行 / {@code block} 阻止，默认 allow
 * @param protocol  协议：{@code tcp} / {@code udp}，为空表示任意
 * @param localPort 本地端口，可为空
 * @param program   程序路径，可为空
 * @author CH
 * @since 4.0.0.42
 */
public record FirewallRuleInputVO(String name, String direction, String action,
                                 String protocol, String localPort, String program) {
}

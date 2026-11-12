package com.chua.starter.monitor.support.model;

/**
 * 可还原协议（能力矩阵行）。
 *
 * <p>页面用它回答「这个工具到底能还原什么」—— 协议名 + 还原深度，而不是让用户
 * 逐个报文点开去猜。数据来源是 SPI 注册的 {@code ProtocolRestorer}，因此新增还原器
 * 会自动出现在列表里，不会出现「代码支持了、页面还说没有」。</p>
 *
 * @param protocol 协议名（与 SPI 注册名一致，如 http / email / wechat）
 * @param priority 调度优先级（越小越先尝试，便于排障时判断谁抢到了这一帧）
 * @param level    还原深度：FULL 正文可读 / STRUCTURE 结构可读 / CONNECTION 仅连接识别
 * @author CH
 * @since 4.0.0.60
 */
public record TsharkRestoreCapabilityVO(String protocol, int priority, String level) {
}

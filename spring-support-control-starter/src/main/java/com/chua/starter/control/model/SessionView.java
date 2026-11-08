package com.chua.starter.control.model;

/**
 * 远控会话视图。
 *
 * @param sessionId  会话 ID
 * @param peerCount  连接数
 * @param capturing  是否正在采集桌面
 * @author CH
 * @since 4.0.0.51
 */
public record SessionView(String sessionId, int peerCount, boolean capturing) {
}

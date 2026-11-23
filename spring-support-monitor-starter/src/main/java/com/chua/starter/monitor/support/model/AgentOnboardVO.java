package com.chua.starter.monitor.support.model;

/**
 * 一键接入本机结果
 *
 * @param hostId      本机主机 ID（复用或新建）
 * @param hostName    主机名称
 * @param hostCreated 本次是否新建了主机资产
 * @param message     受理提示信息
 * @author CH
 * @since 4.0.0.51
 */
public record AgentOnboardVO(long hostId, String hostName, boolean hostCreated, String message) {
}

package com.chua.starter.server.support.model.vo;

/**
 * 实时日志帧（SSE 推送）
 * <p>
 * LINE 帧带 {@code line}，连接/错误等控制帧带 {@code message}；字段名与历史 Map 载荷完全一致。
 * </p>
 *
 * @param sessionId 日志会话标识
 * @param projectId 发布项目标识
 * @param status    帧状态：LINE / CONNECTED / ERROR
 * @param line      日志行，控制帧为 {@code null}
 * @param message   控制帧说明，LINE 帧为 {@code null}
 * @author CH
 * @since 4.0.0.42
 */
public record LogPushVO(
        String sessionId,
        Long projectId,
        String status,
        String line,
        String message
) {
}

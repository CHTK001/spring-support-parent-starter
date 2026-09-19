package com.chua.starter.monitor.support.model;

/**
 * Agent 部署进度事件（socket 广播 agent:deploy 的载荷）
 *
 * @param hostId    主机标识
 * @param progress  百分比进度
 * @param message   步骤文案
 * @param status    状态（info/success/warn/error）
 * @param timestamp 服务端时间戳（毫秒）
 * @author CH
 * @since 4.0.0.42
 */
public record AgentDeployProgressVO(Long hostId, int progress, String message, String status, long timestamp) {
}

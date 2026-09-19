package com.chua.starter.monitor.support.model;

import java.time.LocalDateTime;

/**
 * Agent 状态视图：台账记录 + 实时探活
 *
 * @param hostId          主机标识
 * @param installed       台账是否已安装（1 已安装）
 * @param enabled         台账是否启用
 * @param version         台账记录的版本
 * @param port            实际探测端口
 * @param lastCollectTime 最后一次采集回写时间
 * @param lastError       最后一次失败原因
 * @param busy            是否有部署任务在执行
 * @param live            实时探活是否可达
 * @param liveVersion     探活返回的版本
 * @param buildTime       探活返回的构建时间
 * @author CH
 * @since 4.0.0.42
 */
public record AgentDeployStatusVO(Long hostId, Integer installed, Integer enabled, String version,
                                  Integer port, LocalDateTime lastCollectTime, String lastError,
                                  boolean busy, boolean live, String liveVersion, String buildTime) {
}

package com.chua.starter.monitor.support.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 维护计划新增 / 修改入参。
 *
 * @param planName      计划名称
 * @param planType      计划类型
 * @param startTime     开始时间
 * @param endTime       结束时间
 * @param targetHostIds 目标主机 ID JSON 数组
 * @param status        计划状态，为空时按 PENDING 保存
 * @param description   计划说明
 * @author CH
 * @since 4.0.0.44
 */
public record MaintenancePlanDTO(
        @Schema(description = "计划名称", example = "核心服务升级") String planName,
        @Schema(description = "计划类型 MAINTENANCE/UPGRADE/BACKUP/REBOOT", example = "MAINTENANCE") String planType,
        @Schema(description = "开始时间") LocalDateTime startTime,
        @Schema(description = "结束时间") LocalDateTime endTime,
        @Schema(description = "目标主机 ID JSON 数组", example = "[1,2]") String targetHostIds,
        @Schema(description = "计划状态 PENDING/RUNNING/COMPLETED/CANCELLED", example = "PENDING") String status,
        @Schema(description = "计划说明") String description
) {
}
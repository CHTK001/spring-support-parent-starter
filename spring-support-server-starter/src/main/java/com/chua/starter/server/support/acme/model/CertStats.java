package com.chua.starter.server.support.acme.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 证书统计信息
 *
 * @param accountCount  账户总数
 * @param certCount     证书总数
 * @param validCount    有效证书数
 * @param pendingCount  待验证证书数
 * @param expiringCount 即将到期证书数（30 天内）
 * @param expiredCount  已过期证书数
 * @param revokedCount  已吊销证书数
 * @param failedCount   失败证书数
 * @since 2025-12-06
 * @author CH
 */
@ApiModel(description = "证书统计信息")
@Schema(description = "证书统计信息")
public record CertStats(
        @ApiModelProperty("账户总数") @Schema(description = "账户总数") Long accountCount,
        @ApiModelProperty("证书总数") @Schema(description = "证书总数") Long certCount,
        @ApiModelProperty("有效证书数") @Schema(description = "有效证书数") Long validCount,
        @ApiModelProperty("待验证证书数") @Schema(description = "待验证证书数") Long pendingCount,
        @ApiModelProperty("即将到期证书数") @Schema(description = "即将到期证书数") Long expiringCount,
        @ApiModelProperty("已过期证书数") @Schema(description = "已过期证书数") Long expiredCount,
        @ApiModelProperty("已吊销证书数") @Schema(description = "已吊销证书数") Long revokedCount,
        @ApiModelProperty("失败证书数") @Schema(description = "失败证书数") Long failedCount) {
}

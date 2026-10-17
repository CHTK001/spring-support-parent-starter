package com.chua.starter.software.support.pojo.install;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 软件安装请求
 *
 * @param softwareId  软件ID
 * @param userId      用户ID
 * @param deviceId    设备ID
 * @param installPath 安装路径
 * @param sourceId    源ID
 * @since 2026/7/25
 * @author CH
 */
@Schema(description = "软件安装请求")
public record InstallRequest(
        @NotBlank(message = "软件ID不能为空")
        @Schema(description = "软件ID") String softwareId,
        @NotBlank(message = "用户ID不能为空")
        @Schema(description = "用户ID") String userId,
        @Schema(description = "设备ID") String deviceId,
        @Schema(description = "安装路径") String installPath,
        @Schema(description = "源ID") String sourceId) {
}

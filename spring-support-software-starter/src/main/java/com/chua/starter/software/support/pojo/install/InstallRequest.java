package com.chua.starter.software.support.pojo.install;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 软件安装请求
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@Schema(description = "软件安装请求")
public class InstallRequest {

    /**
     * 软件ID
     */
    @NotBlank(message = "软件ID不能为空")
    @Schema(description = "软件ID")
    /** SoftwareID */
    private String softwareId;

    /**
     * 用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    @Schema(description = "用户ID")
    /** 用户ID */
    private String userId;

    /**
     * 设备ID
     */
    @Schema(description = "设备ID")
    /** DeviceID */
    private String deviceId;

    /**
     * 安装路径
     */
    @Schema(description = "安装路径")
    /** Install路径 */
    private String installPath;

    /**
     * 源ID
     */
    @Schema(description = "源ID")
    /** 来源ID */
    private String sourceId;

    public String getSoftwareId() {
        return softwareId;
    }

    public void setSoftwareId(String softwareId) {
        this.softwareId = softwareId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getInstallPath() {
        return installPath;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }
}
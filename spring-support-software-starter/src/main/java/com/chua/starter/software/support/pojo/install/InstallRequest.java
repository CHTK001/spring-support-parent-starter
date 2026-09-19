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
     * 软件标识
     */
    @NotBlank(message = "软件ID不能为空")
    @Schema(description = "软件ID")
    /** softwareid */
    private String softwareId;

    /**
    * 用户标识
    */
    @NotBlank(message = "用户ID不能为空")
    @Schema(description = "用户ID")
    /** 用户标识 */
    private String userId;

    /**
    * 设备标识
    */
    @Schema(description = "设备ID")
    /** deviceid */
    private String deviceId;

    /**
    * 安装路径
    */
    @Schema(description = "安装路径")
    /** Install路径 */
    private String installPath;

    /**
    * 源标识
    */
    @Schema(description = "源ID")
    /** 来源标识 */
    private String sourceId;

    /**
    * 获取softwareid
    *
    * @return 获取softwareid的结果
    */
    public String getSoftwareId() {
        return softwareId;
    }

    /**
     * 设置softwareid
     *
     * @param softwareId softwareid
     */
    public void setSoftwareId(String softwareId) {
        this.softwareId = softwareId;
    }

    /**
     * 获取用户标识
     *
     * @return 获取用户id的结果
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 设置用户标识
     *
     * @param userId 用户标识
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 获取deviceid
     *
     * @return 获取deviceid的结果
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 设置deviceid
     *
     * @param deviceId deviceid
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * 获取install路径
     *
     * @return 获取install路径的结果
     */
    public String getInstallPath() {
        return installPath;
    }

    /**
     * 设置install路径
     *
     * @param installPath install路径
     */
    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }

    /**
     * 获取源标识
     *
     * @return 获取源id的结果
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * 设置源标识
     *
     * @param sourceId 源标识
     */
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }
}

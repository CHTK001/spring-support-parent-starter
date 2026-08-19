package com.chua.starter.software.support.pojo.install;

import com.chua.starter.software.support.entity.SoftInstallRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安装记录详情响应
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@Schema(description = "安装记录详情响应")
public class InstallDetailResponse extends SoftInstallRecord {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 软件名称
     */
    @Schema(description = "软件名称")
    /** Software名称 */
    private String softwareName;

    /**
     * 软件版本
     */
    @Schema(description = "软件版本")
    /** Software版本 */
    private String softwareVersion;

    /**
     * 软件图标
     */
    @Schema(description = "软件图标")
    /** Softwareicon */
    private String softwareIcon;

    /**
     * 状态文本
     */
    @Schema(description = "状态文本")
    /** 状态文本 */
    private String statusText;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    /** 开始时间 */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    /** 结束时间 */
    private LocalDateTime endTime;

    /** 获取SoftwareName */
    public String getSoftwareName() {
        return softwareName;
    }

    /** 设置SoftwareName */
    public void setSoftwareName(String softwareName) {
        this.softwareName = softwareName;
    }

    /** 获取SoftwareVersion */
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    /** 设置SoftwareVersion */
    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    /** 获取SoftwareIcon */
    public String getSoftwareIcon() {
        return softwareIcon;
    }

    /** 设置SoftwareIcon */
    public void setSoftwareIcon(String softwareIcon) {
        this.softwareIcon = softwareIcon;
    }

    /** 获取StatusText */
    public String getStatusText() {
        return statusText;
    }

    /** 设置StatusText */
    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    /** 获取开始Time */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /** 设置开始Time */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /** 获取EndTime */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /** 设置EndTime */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
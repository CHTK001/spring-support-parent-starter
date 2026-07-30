package com.chua.starter.software.support.pojo.install;

import com.chua.starter.software.support.entity.SoftInstallRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安装记录详情响应
 *
 * @author CH
 * @since 2026/7/25
 */
@Data
@Schema(description = "安装记录详情响应")
public class InstallDetailResponse extends SoftInstallRecord {

    private static final long serialVersionUID = 1L;

    /**
     * 软件名称
     */
    @Schema(description = "软件名称")
    private String softwareName;

    /**
     * 软件版本
     */
    @Schema(description = "软件版本")
    private String softwareVersion;

    /**
     * 软件图标
     */
    @Schema(description = "软件图标")
    private String softwareIcon;

    /**
     * 状态文本
     */
    @Schema(description = "状态文本")
    private String statusText;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    public String getSoftwareName() {
        return softwareName;
    }

    public void setSoftwareName(String softwareName) {
        this.softwareName = softwareName;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public String getSoftwareIcon() {
        return softwareIcon;
    }

    public void setSoftwareIcon(String softwareIcon) {
        this.softwareIcon = softwareIcon;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
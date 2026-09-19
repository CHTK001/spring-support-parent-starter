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

    /** 串行版本UID */
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

    /**
    * 获取software名称
    *
    * @return 获取software名称的结果
    */
    public String getSoftwareName() {
        return softwareName;
    }

    /**
     * 设置software名称
     *
     * @param softwareName software名称
     */
    public void setSoftwareName(String softwareName) {
        this.softwareName = softwareName;
    }

    /**
     * 获取software版本
     *
     * @return 获取software版本的结果
     */
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    /**
     * 设置software版本
     *
     * @param softwareVersion software版本
     */
    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    /**
     * 获取softwareicon
     *
     * @return 获取softwareicon的结果
     */
    public String getSoftwareIcon() {
        return softwareIcon;
    }

    /**
     * 设置softwareicon
     *
     * @param softwareIcon softwareicon
     */
    public void setSoftwareIcon(String softwareIcon) {
        this.softwareIcon = softwareIcon;
    }

    /**
     * 获取状态文本
     *
     * @return 获取状态文本的结果
     */
    public String getStatusText() {
        return statusText;
    }

    /**
     * 设置状态文本
     *
     * @param statusText 状态文本
     */
    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    /**
     * 获取开始时间
     *
     * @return 获取启动时间的结果
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * 设置开始时间
     *
     * @param startTime 启动时间
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * 获取结束时间
     *
     * @return 获取结束时间的结果
     */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /**
     * 设置结束时间
     *
     * @param endTime 结束时间
     */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}

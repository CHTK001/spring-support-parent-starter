package com.chua.starter.software.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * soft_software 软件信息表
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@TableName("soft_software")
public class SoftSoftware implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /** software_id - 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    /** SoftwareID */
    private String softwareId;

    /** software_name - 软件名称 */
    private String softwareName;

    /** software_code - 软件编码 */
    private String softwareCode;

    /** software_version - 当前版本 */
    private String softwareVersion;

    /** software_latest_version - 最新版本 */
    private String softwareLatestVersion;

    /** software_description - 软件描述 */
    private String softwareDescription;

    /** software_icon - 软件图标URL */
    private String softwareIcon;

    /** software_category - 软件分类 */
    private String softwareCategory;

    /** software_tags - 标签，多个用逗号分隔 */
    private String softwareTags;

    /** software_download_url - 下载URL */
    private String softwareDownloadUrl;

    /** software_file_size - 文件大小(字节) */
    private Long softwareFileSize;

    /** software_md5 - 文件MD5 */
    private String softwareMd5;

    /** software_sha256 - 文件SHA256 */
    private String softwareSha256;

    /** software_min_os_version - 最低支持系统版本 */
    private String softwareMinOsVersion;

    /** software_cpu_arch - 支持的CPU架构 */
    private String softwareCpuArch;

    /** software_source_id - 所属源ID */
    private String softwareSourceId;

    /** software_install_count - 安装次数 */
    private Integer softwareInstallCount;

    /** software_rating - 评分 */
    private Double softwareRating;

    /** software_is_official - 是否官方软件 */
    private Boolean softwareIsOfficial;

    /** software_is_recommended - 是否推荐软件 */
    private Boolean softwareIsRecommended;

    /** software_is_free - 是否免费 */
    private Boolean softwareIsFree;

    /** software_price - 价格(分) */
    private Integer softwarePrice;

    /** software_status - 状态: 0正常 1已删除 2已禁用 */
    private Integer softwareStatus;

    /** software_sort - 排序 */
    private Integer softwareSort;

    /** software_extra - 扩展信息JSON */
    private String softwareExtra;

    /** software_create_time - 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    /** Softwarecreate时间 */
    private LocalDateTime softwareCreateTime;

    /** software_update_time - 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** Softwareupdate时间 */
    private LocalDateTime softwareUpdateTime;

    /** software_create_by - 创建人 */
    private String softwareCreateBy;

    /** software_update_by - 更新人 */
    private String softwareUpdateBy;

    /** 获取SoftwareId */
    public String getSoftwareId() {
        return softwareId;
    }

    /** 设置SoftwareId */
    public void setSoftwareId(String softwareId) {
        this.softwareId = softwareId;
    }

    /** 获取SoftwareName */
    public String getSoftwareName() {
        return softwareName;
    }

    /** 设置SoftwareName */
    public void setSoftwareName(String softwareName) {
        this.softwareName = softwareName;
    }

    /** 获取SoftwareCode */
    public String getSoftwareCode() {
        return softwareCode;
    }

    /** 设置SoftwareCode */
    public void setSoftwareCode(String softwareCode) {
        this.softwareCode = softwareCode;
    }

    /** 获取SoftwareVersion */
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    /** 设置SoftwareVersion */
    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    /** 获取SoftwareLatestVersion */
    public String getSoftwareLatestVersion() {
        return softwareLatestVersion;
    }

    /** 设置SoftwareLatestVersion */
    public void setSoftwareLatestVersion(String softwareLatestVersion) {
        this.softwareLatestVersion = softwareLatestVersion;
    }

    /** 获取SoftwareDescription */
    public String getSoftwareDescription() {
        return softwareDescription;
    }

    /** 设置SoftwareDescription */
    public void setSoftwareDescription(String softwareDescription) {
        this.softwareDescription = softwareDescription;
    }

    /** 获取SoftwareIcon */
    public String getSoftwareIcon() {
        return softwareIcon;
    }

    /** 设置SoftwareIcon */
    public void setSoftwareIcon(String softwareIcon) {
        this.softwareIcon = softwareIcon;
    }

    /** 获取SoftwareCategory */
    public String getSoftwareCategory() {
        return softwareCategory;
    }

    /** 设置SoftwareCategory */
    public void setSoftwareCategory(String softwareCategory) {
        this.softwareCategory = softwareCategory;
    }

    /** 获取SoftwareTags */
    public String getSoftwareTags() {
        return softwareTags;
    }

    /** 设置SoftwareTags */
    public void setSoftwareTags(String softwareTags) {
        this.softwareTags = softwareTags;
    }

    /** 获取SoftwareDownloadUrl */
    public String getSoftwareDownloadUrl() {
        return softwareDownloadUrl;
    }

    /** 设置SoftwareDownloadUrl */
    public void setSoftwareDownloadUrl(String softwareDownloadUrl) {
        this.softwareDownloadUrl = softwareDownloadUrl;
    }

    /** 获取SoftwareFile获取大小 */
    public Long getSoftwareFileSize() {
        return softwareFileSize;
    }

    /** 设置SoftwareFile获取大小 */
    public void setSoftwareFileSize(Long softwareFileSize) {
        this.softwareFileSize = softwareFileSize;
    }

    /** 获取SoftwareMd */
    public String getSoftwareMd5() {
        return softwareMd5;
    }

    /** 设置SoftwareMd */
    public void setSoftwareMd5(String softwareMd5) {
        this.softwareMd5 = softwareMd5;
    }

    /** 获取SoftwareSha */
    public String getSoftwareSha256() {
        return softwareSha256;
    }

    /** 设置SoftwareSha */
    public void setSoftwareSha256(String softwareSha256) {
        this.softwareSha256 = softwareSha256;
    }

    /** 获取Software最小值OsVersion */
    public String getSoftwareMinOsVersion() {
        return softwareMinOsVersion;
    }

    /** 设置Software最小值OsVersion */
    public void setSoftwareMinOsVersion(String softwareMinOsVersion) {
        this.softwareMinOsVersion = softwareMinOsVersion;
    }

    /** 获取SoftwareCpuArch */
    public String getSoftwareCpuArch() {
        return softwareCpuArch;
    }

    /** 设置SoftwareCpuArch */
    public void setSoftwareCpuArch(String softwareCpuArch) {
        this.softwareCpuArch = softwareCpuArch;
    }

    /** 获取SoftwareSourceId */
    public String getSoftwareSourceId() {
        return softwareSourceId;
    }

    /** 设置SoftwareSourceId */
    public void setSoftwareSourceId(String softwareSourceId) {
        this.softwareSourceId = softwareSourceId;
    }

    /** 获取SoftwareInstall计算数量 */
    public Integer getSoftwareInstallCount() {
        return softwareInstallCount;
    }

    /** 设置SoftwareInstall计算数量 */
    public void setSoftwareInstallCount(Integer softwareInstallCount) {
        this.softwareInstallCount = softwareInstallCount;
    }

    /** 获取SoftwareStatus */
    public Integer getSoftwareStatus() {
        return softwareStatus;
    }

    /** 设置SoftwareStatus */
    public void setSoftwareStatus(Integer softwareStatus) {
        this.softwareStatus = softwareStatus;
    }

    /** 获取Software创建Time */
    public LocalDateTime getSoftwareCreateTime() {
        return softwareCreateTime;
    }

    /** 设置Software创建Time */
    public void setSoftwareCreateTime(LocalDateTime softwareCreateTime) {
        this.softwareCreateTime = softwareCreateTime;
    }

    /** 获取Software更新Time */
    public LocalDateTime getSoftwareUpdateTime() {
        return softwareUpdateTime;
    }

    /** 设置Software更新Time */
    public void setSoftwareUpdateTime(LocalDateTime softwareUpdateTime) {
        this.softwareUpdateTime = softwareUpdateTime;
    }

    /** 获取Software创建By */
    public String getSoftwareCreateBy() {
        return softwareCreateBy;
    }

    /** 设置Software创建By */
    public void setSoftwareCreateBy(String softwareCreateBy) {
        this.softwareCreateBy = softwareCreateBy;
    }

    /** 获取Software更新By */
    public String getSoftwareUpdateBy() {
        return softwareUpdateBy;
    }

    /** 设置Software更新By */
    public void setSoftwareUpdateBy(String softwareUpdateBy) {
        this.softwareUpdateBy = softwareUpdateBy;
    }
}

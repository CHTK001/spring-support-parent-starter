package com.chua.starter.software.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * soft_software 软件信息表
 *
 * @author CH
 * @since 2026/7/25
 */
@Data
@TableName("soft_software")
public class SoftSoftware implements Serializable {

    private static final long serialVersionUID = 1L;

    /** software_id - 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
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
    private LocalDateTime softwareCreateTime;

    /** software_update_time - 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime softwareUpdateTime;

    /** software_create_by - 创建人 */
    private String softwareCreateBy;

    /** software_update_by - 更新人 */
    private String softwareUpdateBy;

    public String getSoftwareId() {
        return softwareId;
    }

    public void setSoftwareId(String softwareId) {
        this.softwareId = softwareId;
    }

    public String getSoftwareName() {
        return softwareName;
    }

    public void setSoftwareName(String softwareName) {
        this.softwareName = softwareName;
    }

    public String getSoftwareCode() {
        return softwareCode;
    }

    public void setSoftwareCode(String softwareCode) {
        this.softwareCode = softwareCode;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public String getSoftwareLatestVersion() {
        return softwareLatestVersion;
    }

    public void setSoftwareLatestVersion(String softwareLatestVersion) {
        this.softwareLatestVersion = softwareLatestVersion;
    }

    public String getSoftwareDescription() {
        return softwareDescription;
    }

    public void setSoftwareDescription(String softwareDescription) {
        this.softwareDescription = softwareDescription;
    }

    public String getSoftwareIcon() {
        return softwareIcon;
    }

    public void setSoftwareIcon(String softwareIcon) {
        this.softwareIcon = softwareIcon;
    }

    public String getSoftwareCategory() {
        return softwareCategory;
    }

    public void setSoftwareCategory(String softwareCategory) {
        this.softwareCategory = softwareCategory;
    }

    public String getSoftwareTags() {
        return softwareTags;
    }

    public void setSoftwareTags(String softwareTags) {
        this.softwareTags = softwareTags;
    }

    public String getSoftwareDownloadUrl() {
        return softwareDownloadUrl;
    }

    public void setSoftwareDownloadUrl(String softwareDownloadUrl) {
        this.softwareDownloadUrl = softwareDownloadUrl;
    }

    public Long getSoftwareFileSize() {
        return softwareFileSize;
    }

    public void setSoftwareFileSize(Long softwareFileSize) {
        this.softwareFileSize = softwareFileSize;
    }

    public String getSoftwareMd5() {
        return softwareMd5;
    }

    public void setSoftwareMd5(String softwareMd5) {
        this.softwareMd5 = softwareMd5;
    }

    public String getSoftwareSha256() {
        return softwareSha256;
    }

    public void setSoftwareSha256(String softwareSha256) {
        this.softwareSha256 = softwareSha256;
    }

    public String getSoftwareMinOsVersion() {
        return softwareMinOsVersion;
    }

    public void setSoftwareMinOsVersion(String softwareMinOsVersion) {
        this.softwareMinOsVersion = softwareMinOsVersion;
    }

    public String getSoftwareCpuArch() {
        return softwareCpuArch;
    }

    public void setSoftwareCpuArch(String softwareCpuArch) {
        this.softwareCpuArch = softwareCpuArch;
    }

    public String getSoftwareSourceId() {
        return softwareSourceId;
    }

    public void setSoftwareSourceId(String softwareSourceId) {
        this.softwareSourceId = softwareSourceId;
    }

    public Integer getSoftwareInstallCount() {
        return softwareInstallCount;
    }

    public void setSoftwareInstallCount(Integer softwareInstallCount) {
        this.softwareInstallCount = softwareInstallCount;
    }

    public Integer getSoftwareStatus() {
        return softwareStatus;
    }

    public void setSoftwareStatus(Integer softwareStatus) {
        this.softwareStatus = softwareStatus;
    }

    public LocalDateTime getSoftwareCreateTime() {
        return softwareCreateTime;
    }

    public void setSoftwareCreateTime(LocalDateTime softwareCreateTime) {
        this.softwareCreateTime = softwareCreateTime;
    }

    public LocalDateTime getSoftwareUpdateTime() {
        return softwareUpdateTime;
    }

    public void setSoftwareUpdateTime(LocalDateTime softwareUpdateTime) {
        this.softwareUpdateTime = softwareUpdateTime;
    }

    public String getSoftwareCreateBy() {
        return softwareCreateBy;
    }

    public void setSoftwareCreateBy(String softwareCreateBy) {
        this.softwareCreateBy = softwareCreateBy;
    }

    public String getSoftwareUpdateBy() {
        return softwareUpdateBy;
    }

    public void setSoftwareUpdateBy(String softwareUpdateBy) {
        this.softwareUpdateBy = softwareUpdateBy;
    }
}

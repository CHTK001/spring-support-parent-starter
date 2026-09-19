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

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /** software_标识 - 主键标识 */
    @TableId(type = IdType.ASSIGN_ID)
    /** softwareid */
    private String softwareId;

    /** software_名称 - 软件名称 */
    private String softwareName;

    /** software_编码 - 软件编码 */
    private String softwareCode;

    /** software_版本 - 当前版本 */
    private String softwareVersion;

    /** software_latest_版本 - 最新版本 */
    private String softwareLatestVersion;

    /** software_description - 软件描述 */
    private String softwareDescription;

    /** software_icon - 软件图标URL */
    private String softwareIcon;

    /** software_分类 - 软件分类 */
    private String softwareCategory;

    /** software_标签 - 标签，多个用逗号分隔 */
    private String softwareTags;

    /** software_download_url - 下载URL */
    private String softwareDownloadUrl;

    /** software_文件_大小 - 文件大小(字节) */
    private Long softwareFileSize;

    /** software_md5 - 文件MD5 */
    private String softwareMd5;

    /** software_sha256 - 文件SHA256 */
    private String softwareSha256;

    /** software_最小_os_版本 - 最低支持系统版本 */
    private String softwareMinOsVersion;

    /** software_cpu_Arch Linux Linux Linux - 支持的CPU架构 */
    private String softwareCpuArch;

    /** software_源_标识 - 所属源标识 */
    private String softwareSourceId;

    /** software_install_数量 - 安装次数 */
    private Integer softwareInstallCount;

    /** software_rating - 评分 */
    private Double softwareRating;

    /** software_是否_official - 是否官方软件 */
    private Boolean softwareIsOfficial;

    /** software_是否_recommended - 是否推荐软件 */
    private Boolean softwareIsRecommended;

    /** software_是否_free - 是否免费 */
    private Boolean softwareIsFree;

    /** software_price - 价格(分) */
    private Integer softwarePrice;

    /** software_状态 - 状态: 0正常 1已删除 2已禁用 */
    private Integer softwareStatus;

    /** software_排序 - 排序 */
    private Integer softwareSort;

    /** software_extra - 扩展信息JSON */
    private String softwareExtra;

    /** software_创建_时间 - 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    /** Softwarecreate时间 */
    private LocalDateTime softwareCreateTime;

    /** software_更新_时间 - 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** Softwareupdate时间 */
    private LocalDateTime softwareUpdateTime;

    /** software_创建_by - 创建人 */
    private String softwareCreateBy;

    /** software_更新_by - 更新人 */
    private String softwareUpdateBy;

    /**
     * 获取softwareid。
     * @return 获取softwareid的结果
     */
    public String getSoftwareId() {
        return softwareId;
    }

    /**
     * 设置softwareid。
     * @param softwareId softwareid
     */
    public void setSoftwareId(String softwareId) {
        this.softwareId = softwareId;
    }

    /**
     * 获取software名称。
     * @return 获取software名称的结果
     */
    public String getSoftwareName() {
        return softwareName;
    }

    /**
     * 设置software名称。
     * @param softwareName software名称
     */
    public void setSoftwareName(String softwareName) {
        this.softwareName = softwareName;
    }

    /**
     * 获取software编码。
     * @return 获取software编码的结果
     */
    public String getSoftwareCode() {
        return softwareCode;
    }

    /**
     * 设置software编码。
     * @param softwareCode software编码
     */
    public void setSoftwareCode(String softwareCode) {
        this.softwareCode = softwareCode;
    }

    /**
     * 获取software版本。
     * @return 获取software版本的结果
     */
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    /**
     * 设置software版本。
     * @param softwareVersion software版本
     */
    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    /**
     * 获取softwarelatest版本。
     * @return 获取softwarelatest版本的结果
     */
    public String getSoftwareLatestVersion() {
        return softwareLatestVersion;
    }

    /**
     * 设置softwarelatest版本。
     * @param softwareLatestVersion softwarelatest版本
     */
    public void setSoftwareLatestVersion(String softwareLatestVersion) {
        this.softwareLatestVersion = softwareLatestVersion;
    }

    /**
     * 获取softwaredescription。
     * @return 获取softwaredescription的结果
     */
    public String getSoftwareDescription() {
        return softwareDescription;
    }

    /**
     * 设置softwaredescription。
     * @param softwareDescription softwaredescription
     */
    public void setSoftwareDescription(String softwareDescription) {
        this.softwareDescription = softwareDescription;
    }

    /**
     * 获取softwareicon。
     * @return 获取softwareicon的结果
     */
    public String getSoftwareIcon() {
        return softwareIcon;
    }

    /**
     * 设置softwareicon。
     * @param softwareIcon softwareicon
     */
    public void setSoftwareIcon(String softwareIcon) {
        this.softwareIcon = softwareIcon;
    }

    /**
     * 获取software分类。
     * @return 获取software分类的结果
     */
    public String getSoftwareCategory() {
        return softwareCategory;
    }

    /**
     * 设置software分类。
     * @param softwareCategory software分类
     */
    public void setSoftwareCategory(String softwareCategory) {
        this.softwareCategory = softwareCategory;
    }

    /**
     * 获取software标签。
     * @return 获取software标签的结果
     */
    public String getSoftwareTags() {
        return softwareTags;
    }

    /**
     * 设置software标签。
     * @param softwareTags software标签
     */
    public void setSoftwareTags(String softwareTags) {
        this.softwareTags = softwareTags;
    }

    /**
     * 获取softwaredownloadurl。
     * @return 获取softwaredownloadurl的结果
     */
    public String getSoftwareDownloadUrl() {
        return softwareDownloadUrl;
    }

    /**
     * 设置softwaredownloadurl。
     * @param softwareDownloadUrl softwaredownloadurl
     */
    public void setSoftwareDownloadUrl(String softwareDownloadUrl) {
        this.softwareDownloadUrl = softwareDownloadUrl;
    }

    /**
     * 获取software文件大小。
     * @return 获取software文件大小的结果
     */
    public Long getSoftwareFileSize() {
        return softwareFileSize;
    }

    /**
     * 设置software文件大小。
     * @param softwareFileSize software文件大小
     */
    public void setSoftwareFileSize(Long softwareFileSize) {
        this.softwareFileSize = softwareFileSize;
    }

    /**
     * 获取softwaremd5。
     * @return 获取softwaremd5的结果
     */
    public String getSoftwareMd5() {
        return softwareMd5;
    }

    /**
     * 设置softwaremd5。
     * @param softwareMd5 softwaremd5
     */
    public void setSoftwareMd5(String softwareMd5) {
        this.softwareMd5 = softwareMd5;
    }

    /**
     * 获取softwaresha256。
     * @return 获取softwaresha256的结果
     */
    public String getSoftwareSha256() {
        return softwareSha256;
    }

    /**
     * 设置softwaresha256。
     * @param softwareSha256 softwaresha256
     */
    public void setSoftwareSha256(String softwareSha256) {
        this.softwareSha256 = softwareSha256;
    }

    /**
     * 获取software最小os版本。
     * @return 获取software最小os版本的结果
     */
    public String getSoftwareMinOsVersion() {
        return softwareMinOsVersion;
    }

    /**
     * 设置software最小os版本。
     * @param softwareMinOsVersion software最小os版本
     */
    public void setSoftwareMinOsVersion(String softwareMinOsVersion) {
        this.softwareMinOsVersion = softwareMinOsVersion;
    }

    /**
     * 获取softwarecpuArch Linux Linux Linux。
     * @return 获取softwarecpuArch Linux的结果
     */
    public String getSoftwareCpuArch() {
        return softwareCpuArch;
    }

    /**
     * 设置softwarecpuArch Linux Linux Linux。
     * @param softwareCpuArch softwarecpuArch Linux Linux Linux
     */
    public void setSoftwareCpuArch(String softwareCpuArch) {
        this.softwareCpuArch = softwareCpuArch;
    }

    /**
     * 获取software源标识。
     * @return 获取software源id的结果
     */
    public String getSoftwareSourceId() {
        return softwareSourceId;
    }

    /**
     * 设置software源标识。
     * @param softwareSourceId software源标识
     */
    public void setSoftwareSourceId(String softwareSourceId) {
        this.softwareSourceId = softwareSourceId;
    }

    /**
     * 获取softwareinstall数量。
     * @return 获取softwareinstall数量的结果
     */
    public Integer getSoftwareInstallCount() {
        return softwareInstallCount;
    }

    /**
     * 设置softwareinstall数量。
     * @param softwareInstallCount softwareinstall数量
     */
    public void setSoftwareInstallCount(Integer softwareInstallCount) {
        this.softwareInstallCount = softwareInstallCount;
    }

    /**
     * 获取software状态。
     * @return 获取software状态的结果
     */
    public Integer getSoftwareStatus() {
        return softwareStatus;
    }

    /**
     * 设置software状态。
     * @param softwareStatus software状态
     */
    public void setSoftwareStatus(Integer softwareStatus) {
        this.softwareStatus = softwareStatus;
    }

    /**
     * 获取software创建时间。
     * @return 获取software创建时间的结果
     */
    public LocalDateTime getSoftwareCreateTime() {
        return softwareCreateTime;
    }

    /**
     * 设置software创建时间。
     * @param softwareCreateTime software创建时间
     */
    public void setSoftwareCreateTime(LocalDateTime softwareCreateTime) {
        this.softwareCreateTime = softwareCreateTime;
    }

    /**
     * 获取software更新时间。
     * @return 获取software更新时间的结果
     */
    public LocalDateTime getSoftwareUpdateTime() {
        return softwareUpdateTime;
    }

    /**
     * 设置software更新时间。
     * @param softwareUpdateTime software更新时间
     */
    public void setSoftwareUpdateTime(LocalDateTime softwareUpdateTime) {
        this.softwareUpdateTime = softwareUpdateTime;
    }

    /**
     * 获取software创建by。
     * @return 获取software创建by的结果
     */
    public String getSoftwareCreateBy() {
        return softwareCreateBy;
    }

    /**
     * 设置software创建by。
     * @param softwareCreateBy software创建by
     */
    public void setSoftwareCreateBy(String softwareCreateBy) {
        this.softwareCreateBy = softwareCreateBy;
    }

    /**
     * 获取software更新by。
     * @return 获取software更新by的结果
     */
    public String getSoftwareUpdateBy() {
        return softwareUpdateBy;
    }

    /**
     * 设置software更新by。
     * @param softwareUpdateBy software更新by
     */
    public void setSoftwareUpdateBy(String softwareUpdateBy) {
        this.softwareUpdateBy = softwareUpdateBy;
    }
}

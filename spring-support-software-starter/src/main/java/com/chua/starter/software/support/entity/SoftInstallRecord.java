package com.chua.starter.software.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * soft_install_record 安装记录表
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@TableName("soft_install_record")
public class SoftInstallRecord implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /** install_record_标识 - 主键标识 */
    @TableId(type = IdType.ASSIGN_ID)
    /** installrecordid */
    private String installRecordId;

    /** install_record_software_标识 - 软件标识 */
    /** installrecordsoftwareid */
    private String installRecordSoftwareId;

    /** install_record_用户_标识 - 用户标识 */
    /** Installrecord用户标识 */
    private String installRecordUserId;

    /** install_record_device_标识 - 设备标识 */
    /** installrecorddeviceid */
    private String installRecordDeviceId;

    /** install_record_源_标识 - 软件源标识 */
    /** Installrecord来源标识 */
    private String installRecordSourceId;

    /** install_record_版本 - 操作版本 */
    /** Installrecord版本 */
    private String installRecordVersion;

    /** install_record_Target_版本 - 目标版本(升级用) */
    /** Installrecord目标版本 */
    private String installRecordTargetVersion;

    /** install_record_operation - 操作类型: install/uninstall/更新/rollback */
    /** Installrecordoperation */
    private String installRecordOperation;

    /** install_record_状态 - 状态: 0未安装 1安装中 2已安装 3安装失败 4卸载中 5已卸载 6更新中 7更新失败 */
    /** Installrecord状态 */
    private Integer installRecordStatus;

    /** install_record_进步 - 进度(0-100) */
    /** Installrecordprogress */
    private Integer installRecordProgress;

    /** install_record_启动_时间 - 开始时间 */
    /** Installrecord开始时间 */
    private LocalDateTime installRecordStartTime;

    /** install_record_结束_时间 - 结束时间 */
    /** Installrecord结束时间 */
    private LocalDateTime installRecordEndTime;

    /** install_record_持续时间 - 耗时(毫秒) */
    /** Installrecord持续时间 */
    private Long installRecordDuration;

    /** install_record_错误_消息 - 错误信息 */
    /** Installrecord错误消息 */
    private String installRecordErrorMessage;

    /** install_record_错误_detail - 错误详情 */
    /** Installrecord错误detail */
    private String installRecordErrorDetail;

    /** install_record_日志_路径 - 日志路径 */
    /** Installrecord日志路径 */
    private String installRecordLogPath;

    /** install_record_download_路径 - 下载文件路径 */
    /** Installrecord下载路径 */
    private String installRecordDownloadPath;

    /** install_record_install_路径 - 安装路径 */
    /** Installrecordinstall路径 */
    private String installRecordInstallPath;

    /** install_record_rollback_版本 - 回滚版本 */
    /** Installrecordrollback版本 */
    private String installRecordRollbackVersion;

    /** install_record_extra - 扩展信息JSON */
    /** Installrecordextra */
    private String installRecordExtra;

    /** install_record_创建_时间 - 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    /** Installrecordcreate时间 */
    private LocalDateTime installRecordCreateTime;

    /** install_record_更新_时间 - 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** Installrecordupdate时间 */
    private LocalDateTime installRecordUpdateTime;

    /**
    * 获取installrecordid。
    * @return 获取installrecordid的结果
    */
    public String getInstallRecordId() {
        return installRecordId;
    }

    /**
     * 设置installrecordid。
     * @param installRecordId installrecordid
     */
    public void setInstallRecordId(String installRecordId) {
        this.installRecordId = installRecordId;
    }

    /**
     * 获取installrecordsoftwareid。
     * @return 获取installrecordsoftwareid的结果
     */
    public String getInstallRecordSoftwareId() {
        return installRecordSoftwareId;
    }

    /**
     * 设置installrecordsoftwareid。
     * @param installRecordSoftwareId installrecordsoftwareid
     */
    public void setInstallRecordSoftwareId(String installRecordSoftwareId) {
        this.installRecordSoftwareId = installRecordSoftwareId;
    }

    /**
     * 获取installrecord用户标识。
     * @return 获取installrecord用户id的结果
     */
    public String getInstallRecordUserId() {
        return installRecordUserId;
    }

    /**
     * 设置installrecord用户标识。
     * @param installRecordUserId installrecord用户标识
     */
    public void setInstallRecordUserId(String installRecordUserId) {
        this.installRecordUserId = installRecordUserId;
    }

    /**
     * 获取installrecorddeviceid。
     * @return 获取installrecorddeviceid的结果
     */
    public String getInstallRecordDeviceId() {
        return installRecordDeviceId;
    }

    /**
     * 设置installrecorddeviceid。
     * @param installRecordDeviceId installrecorddeviceid
     */
    public void setInstallRecordDeviceId(String installRecordDeviceId) {
        this.installRecordDeviceId = installRecordDeviceId;
    }

    /**
     * 获取installrecord源标识。
     * @return 获取installrecord源id的结果
     */
    public String getInstallRecordSourceId() {
        return installRecordSourceId;
    }

    /**
     * 设置installrecord源标识。
     * @param installRecordSourceId installrecord源标识
     */
    public void setInstallRecordSourceId(String installRecordSourceId) {
        this.installRecordSourceId = installRecordSourceId;
    }

    /**
     * 获取installrecord版本。
     * @return 获取installrecord版本的结果
     */
    public String getInstallRecordVersion() {
        return installRecordVersion;
    }

    /**
     * 设置installrecord版本。
     * @param installRecordVersion installrecord版本
     */
    public void setInstallRecordVersion(String installRecordVersion) {
        this.installRecordVersion = installRecordVersion;
    }

    /**
     * 获取installrecordTarget版本。
     * @return 获取installrecordTarget版本的结果
     */
    public String getInstallRecordTargetVersion() {
        return installRecordTargetVersion;
    }

    /**
     * 设置installrecordTarget版本。
     * @param installRecordTargetVersion installrecordTarget版本
     */
    public void setInstallRecordTargetVersion(String installRecordTargetVersion) {
        this.installRecordTargetVersion = installRecordTargetVersion;
    }

    /**
     * 获取installrecordoperation。
     * @return 获取installrecordoperation的结果
     */
    public String getInstallRecordOperation() {
        return installRecordOperation;
    }

    /**
     * 设置installrecordoperation。
     * @param installRecordOperation installrecordoperation
     */
    public void setInstallRecordOperation(String installRecordOperation) {
        this.installRecordOperation = installRecordOperation;
    }

    /**
     * 获取installrecord状态。
     * @return 获取installrecord状态的结果
     */
    public Integer getInstallRecordStatus() {
        return installRecordStatus;
    }

    /**
     * 设置installrecord状态。
     * @param installRecordStatus installrecord状态
     */
    public void setInstallRecordStatus(Integer installRecordStatus) {
        this.installRecordStatus = installRecordStatus;
    }

    /**
     * 获取installrecord进步。
     * @return 获取installrecord进步的结果
     */
    public Integer getInstallRecordProgress() {
        return installRecordProgress;
    }

    /**
     * 设置installrecord进步。
     * @param installRecordProgress installrecord进步
     */
    public void setInstallRecordProgress(Integer installRecordProgress) {
        this.installRecordProgress = installRecordProgress;
    }

    /**
     * 获取installrecord启动时间。
     * @return 获取installrecord启动时间的结果
     */
    public LocalDateTime getInstallRecordStartTime() {
        return installRecordStartTime;
    }

    /**
     * 设置installrecord启动时间。
     * @param installRecordStartTime installrecord启动时间
     */
    public void setInstallRecordStartTime(LocalDateTime installRecordStartTime) {
        this.installRecordStartTime = installRecordStartTime;
    }

    /**
     * 获取installrecord结束时间。
     * @return 获取installrecord结束时间的结果
     */
    public LocalDateTime getInstallRecordEndTime() {
        return installRecordEndTime;
    }

    /**
     * 设置installrecord结束时间。
     * @param installRecordEndTime installrecord结束时间
     */
    public void setInstallRecordEndTime(LocalDateTime installRecordEndTime) {
        this.installRecordEndTime = installRecordEndTime;
    }

    /**
     * 获取installrecord持续时间。
     * @return 获取installrecord持续时间的结果
     */
    public Long getInstallRecordDuration() {
        return installRecordDuration;
    }

    /**
     * 设置installrecord持续时间。
     * @param installRecordDuration installrecord持续时间
     */
    public void setInstallRecordDuration(Long installRecordDuration) {
        this.installRecordDuration = installRecordDuration;
    }

    /**
     * 获取installrecord错误消息。
     * @return 获取installrecord错误消息的结果
     */
    public String getInstallRecordErrorMessage() {
        return installRecordErrorMessage;
    }

    /**
     * 设置installrecord错误消息。
     * @param installRecordErrorMessage installrecord错误消息
     */
    public void setInstallRecordErrorMessage(String installRecordErrorMessage) {
        this.installRecordErrorMessage = installRecordErrorMessage;
    }

    /**
     * 获取installrecord错误detail。
     * @return 获取installrecord错误detail的结果
     */
    public String getInstallRecordErrorDetail() {
        return installRecordErrorDetail;
    }

    /**
     * 设置installrecord错误detail。
     * @param installRecordErrorDetail installrecord错误detail
     */
    public void setInstallRecordErrorDetail(String installRecordErrorDetail) {
        this.installRecordErrorDetail = installRecordErrorDetail;
    }

    /**
     * 获取installrecord日志路径。
     * @return 获取installrecord日志路径的结果
     */
    public String getInstallRecordLogPath() {
        return installRecordLogPath;
    }

    /**
     * 设置installrecord日志路径。
     * @param installRecordLogPath installrecord日志路径
     */
    public void setInstallRecordLogPath(String installRecordLogPath) {
        this.installRecordLogPath = installRecordLogPath;
    }

    /**
     * 获取installrecorddownload路径。
     * @return 获取installrecorddownload路径的结果
     */
    public String getInstallRecordDownloadPath() {
        return installRecordDownloadPath;
    }

    /**
     * 设置installrecorddownload路径。
     * @param installRecordDownloadPath installrecorddownload路径
     */
    public void setInstallRecordDownloadPath(String installRecordDownloadPath) {
        this.installRecordDownloadPath = installRecordDownloadPath;
    }

    /**
     * 获取installrecordinstall路径。
     * @return 获取installrecordinstall路径的结果
     */
    public String getInstallRecordInstallPath() {
        return installRecordInstallPath;
    }

    /**
     * 设置installrecordinstall路径。
     * @param installRecordInstallPath installrecordinstall路径
     */
    public void setInstallRecordInstallPath(String installRecordInstallPath) {
        this.installRecordInstallPath = installRecordInstallPath;
    }

    /**
     * 获取installrecordrollback版本。
     * @return 获取installrecordrollback版本的结果
     */
    public String getInstallRecordRollbackVersion() {
        return installRecordRollbackVersion;
    }

    /**
     * 设置installrecordrollback版本。
     * @param installRecordRollbackVersion installrecordrollback版本
     */
    public void setInstallRecordRollbackVersion(String installRecordRollbackVersion) {
        this.installRecordRollbackVersion = installRecordRollbackVersion;
    }

    /**
     * 获取installrecordextra。
     * @return 获取installrecordextra的结果
     */
    public String getInstallRecordExtra() {
        return installRecordExtra;
    }

    /**
     * 设置installrecordextra。
     * @param installRecordExtra installrecordextra
     */
    public void setInstallRecordExtra(String installRecordExtra) {
        this.installRecordExtra = installRecordExtra;
    }

    /**
     * 获取installrecord创建时间。
     * @return 获取installrecord创建时间的结果
     */
    public LocalDateTime getInstallRecordCreateTime() {
        return installRecordCreateTime;
    }

    /**
     * 设置installrecord创建时间。
     * @param installRecordCreateTime installrecord创建时间
     */
    public void setInstallRecordCreateTime(LocalDateTime installRecordCreateTime) {
        this.installRecordCreateTime = installRecordCreateTime;
    }

    /**
     * 获取installrecord更新时间。
     * @return 获取installrecord更新时间的结果
     */
    public LocalDateTime getInstallRecordUpdateTime() {
        return installRecordUpdateTime;
    }

    /**
     * 设置installrecord更新时间。
     * @param installRecordUpdateTime installrecord更新时间
     */
    public void setInstallRecordUpdateTime(LocalDateTime installRecordUpdateTime) {
        this.installRecordUpdateTime = installRecordUpdateTime;
    }
}

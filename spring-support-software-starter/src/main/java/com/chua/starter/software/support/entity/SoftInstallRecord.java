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

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /** install_record_id - 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    /** InstallrecordID */
    private String installRecordId;

    /** install_record_software_id - 软件ID */
    private String installRecordSoftwareId;

    /** install_record_user_id - 用户ID */
    private String installRecordUserId;

    /** install_record_device_id - 设备ID */
    private String installRecordDeviceId;

    /** install_record_source_id - 软件源ID */
    private String installRecordSourceId;

    /** install_record_version - 操作版本 */
    private String installRecordVersion;

    /** install_record_target_version - 目标版本(升级用) */
    private String installRecordTargetVersion;

    /** install_record_operation - 操作类型: install/uninstall/update/rollback */
    private String installRecordOperation;

    /** install_record_status - 状态: 0未安装 1安装中 2已安装 3安装失败 4卸载中 5已卸载 6更新中 7更新失败 */
    private Integer installRecordStatus;

    /** install_record_progress - 进度(0-100) */
    private Integer installRecordProgress;

    /** install_record_start_time - 开始时间 */
    private LocalDateTime installRecordStartTime;

    /** install_record_end_time - 结束时间 */
    private LocalDateTime installRecordEndTime;

    /** install_record_duration - 耗时(毫秒) */
    private Long installRecordDuration;

    /** install_record_error_message - 错误信息 */
    private String installRecordErrorMessage;

    /** install_record_error_detail - 错误详情 */
    private String installRecordErrorDetail;

    /** install_record_log_path - 日志路径 */
    private String installRecordLogPath;

    /** install_record_download_path - 下载文件路径 */
    private String installRecordDownloadPath;

    /** install_record_install_path - 安装路径 */
    private String installRecordInstallPath;

    /** install_record_rollback_version - 回滚版本 */
    private String installRecordRollbackVersion;

    /** install_record_extra - 扩展信息JSON */
    private String installRecordExtra;

    /** install_record_create_time - 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    /** Installrecordcreate时间 */
    private LocalDateTime installRecordCreateTime;

    /** install_record_update_time - 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** Installrecordupdate时间 */
    private LocalDateTime installRecordUpdateTime;

    /** 获取InstallRecordId */
    public String getInstallRecordId() {
        return installRecordId;
    }

    /** 设置InstallRecordId */
    public void setInstallRecordId(String installRecordId) {
        this.installRecordId = installRecordId;
    }

    /** 获取InstallRecordSoftwareId */
    public String getInstallRecordSoftwareId() {
        return installRecordSoftwareId;
    }

    /** 设置InstallRecordSoftwareId */
    public void setInstallRecordSoftwareId(String installRecordSoftwareId) {
        this.installRecordSoftwareId = installRecordSoftwareId;
    }

    /** 获取InstallRecordUserId */
    public String getInstallRecordUserId() {
        return installRecordUserId;
    }

    /** 设置InstallRecordUserId */
    public void setInstallRecordUserId(String installRecordUserId) {
        this.installRecordUserId = installRecordUserId;
    }

    /** 获取InstallRecordDeviceId */
    public String getInstallRecordDeviceId() {
        return installRecordDeviceId;
    }

    /** 设置InstallRecordDeviceId */
    public void setInstallRecordDeviceId(String installRecordDeviceId) {
        this.installRecordDeviceId = installRecordDeviceId;
    }

    /** 获取InstallRecordSourceId */
    public String getInstallRecordSourceId() {
        return installRecordSourceId;
    }

    /** 设置InstallRecordSourceId */
    public void setInstallRecordSourceId(String installRecordSourceId) {
        this.installRecordSourceId = installRecordSourceId;
    }

    /** 获取InstallRecordVersion */
    public String getInstallRecordVersion() {
        return installRecordVersion;
    }

    /** 设置InstallRecordVersion */
    public void setInstallRecordVersion(String installRecordVersion) {
        this.installRecordVersion = installRecordVersion;
    }

    /** 获取InstallRecordTargetVersion */
    public String getInstallRecordTargetVersion() {
        return installRecordTargetVersion;
    }

    /** 设置InstallRecordTargetVersion */
    public void setInstallRecordTargetVersion(String installRecordTargetVersion) {
        this.installRecordTargetVersion = installRecordTargetVersion;
    }

    /** 获取InstallRecordOperation */
    public String getInstallRecordOperation() {
        return installRecordOperation;
    }

    /** 设置InstallRecordOperation */
    public void setInstallRecordOperation(String installRecordOperation) {
        this.installRecordOperation = installRecordOperation;
    }

    /** 获取InstallRecordStatus */
    public Integer getInstallRecordStatus() {
        return installRecordStatus;
    }

    /** 设置InstallRecordStatus */
    public void setInstallRecordStatus(Integer installRecordStatus) {
        this.installRecordStatus = installRecordStatus;
    }

    /** 获取InstallRecordProgress */
    public Integer getInstallRecordProgress() {
        return installRecordProgress;
    }

    /** 设置InstallRecordProgress */
    public void setInstallRecordProgress(Integer installRecordProgress) {
        this.installRecordProgress = installRecordProgress;
    }

    /** 获取InstallRecord开始Time */
    public LocalDateTime getInstallRecordStartTime() {
        return installRecordStartTime;
    }

    /** 设置InstallRecord开始Time */
    public void setInstallRecordStartTime(LocalDateTime installRecordStartTime) {
        this.installRecordStartTime = installRecordStartTime;
    }

    /** 获取InstallRecordEndTime */
    public LocalDateTime getInstallRecordEndTime() {
        return installRecordEndTime;
    }

    /** 设置InstallRecordEndTime */
    public void setInstallRecordEndTime(LocalDateTime installRecordEndTime) {
        this.installRecordEndTime = installRecordEndTime;
    }

    /** 获取InstallRecordDuration */
    public Long getInstallRecordDuration() {
        return installRecordDuration;
    }

    /** 设置InstallRecordDuration */
    public void setInstallRecordDuration(Long installRecordDuration) {
        this.installRecordDuration = installRecordDuration;
    }

    /** 获取InstallRecord记录错误Message */
    public String getInstallRecordErrorMessage() {
        return installRecordErrorMessage;
    }

    /** 设置InstallRecord记录错误Message */
    public void setInstallRecordErrorMessage(String installRecordErrorMessage) {
        this.installRecordErrorMessage = installRecordErrorMessage;
    }

    /** 获取InstallRecord记录错误Detail */
    public String getInstallRecordErrorDetail() {
        return installRecordErrorDetail;
    }

    /** 设置InstallRecord记录错误Detail */
    public void setInstallRecordErrorDetail(String installRecordErrorDetail) {
        this.installRecordErrorDetail = installRecordErrorDetail;
    }

    /** 获取InstallRecord记录日志Path */
    public String getInstallRecordLogPath() {
        return installRecordLogPath;
    }

    /** 设置InstallRecord记录日志Path */
    public void setInstallRecordLogPath(String installRecordLogPath) {
        this.installRecordLogPath = installRecordLogPath;
    }

    /** 获取InstallRecordDownloadPath */
    public String getInstallRecordDownloadPath() {
        return installRecordDownloadPath;
    }

    /** 设置InstallRecordDownloadPath */
    public void setInstallRecordDownloadPath(String installRecordDownloadPath) {
        this.installRecordDownloadPath = installRecordDownloadPath;
    }

    /** 获取InstallRecordInstallPath */
    public String getInstallRecordInstallPath() {
        return installRecordInstallPath;
    }

    /** 设置InstallRecordInstallPath */
    public void setInstallRecordInstallPath(String installRecordInstallPath) {
        this.installRecordInstallPath = installRecordInstallPath;
    }

    /** 获取InstallRecord回滚Version */
    public String getInstallRecordRollbackVersion() {
        return installRecordRollbackVersion;
    }

    /** 设置InstallRecord回滚Version */
    public void setInstallRecordRollbackVersion(String installRecordRollbackVersion) {
        this.installRecordRollbackVersion = installRecordRollbackVersion;
    }

    /** 获取InstallRecordExtra */
    public String getInstallRecordExtra() {
        return installRecordExtra;
    }

    /** 设置InstallRecordExtra */
    public void setInstallRecordExtra(String installRecordExtra) {
        this.installRecordExtra = installRecordExtra;
    }

    /** 获取InstallRecord创建Time */
    public LocalDateTime getInstallRecordCreateTime() {
        return installRecordCreateTime;
    }

    /** 设置InstallRecord创建Time */
    public void setInstallRecordCreateTime(LocalDateTime installRecordCreateTime) {
        this.installRecordCreateTime = installRecordCreateTime;
    }

    /** 获取InstallRecord更新Time */
    public LocalDateTime getInstallRecordUpdateTime() {
        return installRecordUpdateTime;
    }

    /** 设置InstallRecord更新Time */
    public void setInstallRecordUpdateTime(LocalDateTime installRecordUpdateTime) {
        this.installRecordUpdateTime = installRecordUpdateTime;
    }
}

package com.chua.starter.software.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * soft_install_record 安装记录表
 *
 * @author CH
 * @since 2026/7/25
 */
@Data
@TableName("soft_install_record")
public class SoftInstallRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** install_record_id - 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
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
    private LocalDateTime installRecordCreateTime;

    /** install_record_update_time - 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime installRecordUpdateTime;

    public String getInstallRecordId() {
        return installRecordId;
    }

    public void setInstallRecordId(String installRecordId) {
        this.installRecordId = installRecordId;
    }

    public String getInstallRecordSoftwareId() {
        return installRecordSoftwareId;
    }

    public void setInstallRecordSoftwareId(String installRecordSoftwareId) {
        this.installRecordSoftwareId = installRecordSoftwareId;
    }

    public String getInstallRecordUserId() {
        return installRecordUserId;
    }

    public void setInstallRecordUserId(String installRecordUserId) {
        this.installRecordUserId = installRecordUserId;
    }

    public String getInstallRecordDeviceId() {
        return installRecordDeviceId;
    }

    public void setInstallRecordDeviceId(String installRecordDeviceId) {
        this.installRecordDeviceId = installRecordDeviceId;
    }

    public String getInstallRecordSourceId() {
        return installRecordSourceId;
    }

    public void setInstallRecordSourceId(String installRecordSourceId) {
        this.installRecordSourceId = installRecordSourceId;
    }

    public String getInstallRecordVersion() {
        return installRecordVersion;
    }

    public void setInstallRecordVersion(String installRecordVersion) {
        this.installRecordVersion = installRecordVersion;
    }

    public String getInstallRecordTargetVersion() {
        return installRecordTargetVersion;
    }

    public void setInstallRecordTargetVersion(String installRecordTargetVersion) {
        this.installRecordTargetVersion = installRecordTargetVersion;
    }

    public String getInstallRecordOperation() {
        return installRecordOperation;
    }

    public void setInstallRecordOperation(String installRecordOperation) {
        this.installRecordOperation = installRecordOperation;
    }

    public Integer getInstallRecordStatus() {
        return installRecordStatus;
    }

    public void setInstallRecordStatus(Integer installRecordStatus) {
        this.installRecordStatus = installRecordStatus;
    }

    public Integer getInstallRecordProgress() {
        return installRecordProgress;
    }

    public void setInstallRecordProgress(Integer installRecordProgress) {
        this.installRecordProgress = installRecordProgress;
    }

    public LocalDateTime getInstallRecordStartTime() {
        return installRecordStartTime;
    }

    public void setInstallRecordStartTime(LocalDateTime installRecordStartTime) {
        this.installRecordStartTime = installRecordStartTime;
    }

    public LocalDateTime getInstallRecordEndTime() {
        return installRecordEndTime;
    }

    public void setInstallRecordEndTime(LocalDateTime installRecordEndTime) {
        this.installRecordEndTime = installRecordEndTime;
    }

    public Long getInstallRecordDuration() {
        return installRecordDuration;
    }

    public void setInstallRecordDuration(Long installRecordDuration) {
        this.installRecordDuration = installRecordDuration;
    }

    public String getInstallRecordErrorMessage() {
        return installRecordErrorMessage;
    }

    public void setInstallRecordErrorMessage(String installRecordErrorMessage) {
        this.installRecordErrorMessage = installRecordErrorMessage;
    }

    public String getInstallRecordErrorDetail() {
        return installRecordErrorDetail;
    }

    public void setInstallRecordErrorDetail(String installRecordErrorDetail) {
        this.installRecordErrorDetail = installRecordErrorDetail;
    }

    public String getInstallRecordLogPath() {
        return installRecordLogPath;
    }

    public void setInstallRecordLogPath(String installRecordLogPath) {
        this.installRecordLogPath = installRecordLogPath;
    }

    public String getInstallRecordDownloadPath() {
        return installRecordDownloadPath;
    }

    public void setInstallRecordDownloadPath(String installRecordDownloadPath) {
        this.installRecordDownloadPath = installRecordDownloadPath;
    }

    public String getInstallRecordInstallPath() {
        return installRecordInstallPath;
    }

    public void setInstallRecordInstallPath(String installRecordInstallPath) {
        this.installRecordInstallPath = installRecordInstallPath;
    }

    public String getInstallRecordRollbackVersion() {
        return installRecordRollbackVersion;
    }

    public void setInstallRecordRollbackVersion(String installRecordRollbackVersion) {
        this.installRecordRollbackVersion = installRecordRollbackVersion;
    }

    public String getInstallRecordExtra() {
        return installRecordExtra;
    }

    public void setInstallRecordExtra(String installRecordExtra) {
        this.installRecordExtra = installRecordExtra;
    }

    public LocalDateTime getInstallRecordCreateTime() {
        return installRecordCreateTime;
    }

    public void setInstallRecordCreateTime(LocalDateTime installRecordCreateTime) {
        this.installRecordCreateTime = installRecordCreateTime;
    }

    public LocalDateTime getInstallRecordUpdateTime() {
        return installRecordUpdateTime;
    }

    public void setInstallRecordUpdateTime(LocalDateTime installRecordUpdateTime) {
        this.installRecordUpdateTime = installRecordUpdateTime;
    }
}

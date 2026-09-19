package com.chua.starter.filesystem.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件系统上传 对象存储 记录表（审计）。
 *
 * <p>每次上传尝试无论成败都写入一条记录，用于排查与追溯。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@TableName(value = "filesystem_upload_upload_log")
public class SysFileSystemUploadUploadLog extends SysBase implements Serializable {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_upload_log_id", type = IdType.AUTO)
    /**
     * Filesystem上传上传日志标识
    */
    private Long filesystemUploadUploadLogId;

    /**
     * 上传任务标识
    */
    @TableField("filesystem_upload_upload_log_upload_id")
    /**
     * Filesystem上传上传日志上传标识
    */
    private String filesystemUploadUploadLogUploadId;

    /**
     * 阶段标识: 2-上传对象存储
    */
    @TableField("filesystem_upload_upload_log_stage")
    /**
     * Filesystem上传上传日志阶段
    */
    private Integer filesystemUploadUploadLogStage;

    /**
     * 结果状态: 1-成功, 0-失败
    */
    @TableField("filesystem_upload_upload_log_status")
    /**
     * Filesystem上传上传日志状态
    */
    private Integer filesystemUploadUploadLogStatus;

    /**
     * 记录信息（成功/失败原因）
    */
    @TableField("filesystem_upload_upload_log_message")
    /**
     * Filesystem上传上传日志消息
    */
    private String filesystemUploadUploadLogMessage;

    /**
     * 第几次尝试
    */
    @TableField("filesystem_upload_upload_log_retry_count")
    /**
     * Filesystem上传上传日志重试数量
    */
    private Integer filesystemUploadUploadLogRetryCount;

    /**
     * 开始时间
    */
    @TableField("filesystem_upload_upload_log_start_time")
    /**
     * Filesystem上传上传日志开始时间
    */
    private LocalDateTime filesystemUploadUploadLogStartTime;

    /**
     * 结束时间
    */
    @TableField("filesystem_upload_upload_log_end_time")
    /**
     * Filesystem上传上传日志结束时间
    */
    private LocalDateTime filesystemUploadUploadLogEndTime;

    /**
     * 创建 sys文件系统uploadupload日志 实例
    */
    public SysFileSystemUploadUploadLog() {}

    /**
     * 获取filesystemuploadupload记录日志标识
     *
     * @return 获取filesystemuploadupload日志id的结果
     */
    public Long getFilesystemUploadUploadLogId() { return filesystemUploadUploadLogId; }
    /**
     * 设置filesystemuploadupload记录日志标识
     *
     * @param v v
     */
    public void setFilesystemUploadUploadLogId(Long v) { this.filesystemUploadUploadLogId = v; }

    /**
     * 获取filesystemuploadupload记录日志uploadid
     *
     * @return 获取filesystemuploadupload日志uploadid的结果
     */
    public String getFilesystemUploadUploadLogUploadId() { return filesystemUploadUploadLogUploadId; }
    /**
     * 设置filesystemuploadupload记录日志uploadid
     *
     * @param v v
     */
    public void setFilesystemUploadUploadLogUploadId(String v) { this.filesystemUploadUploadLogUploadId = v; }

    /**
     * 获取filesystemuploadupload记录日志Stage
     *
     * @return 获取filesystemuploadupload日志Stage的结果
     */
    public Integer getFilesystemUploadUploadLogStage() { return filesystemUploadUploadLogStage; }
    /**
     * 设置filesystemuploadupload记录日志Stage
     *
     * @param v v
     */
    public void setFilesystemUploadUploadLogStage(Integer v) { this.filesystemUploadUploadLogStage = v; }

    /**
     * 获取filesystemuploadupload记录日志状态
     *
     * @return 获取filesystemuploadupload日志状态的结果
     */
    public Integer getFilesystemUploadUploadLogStatus() { return filesystemUploadUploadLogStatus; }
    /**
     * 设置filesystemuploadupload记录日志状态
     *
     * @param v v
     */
    public void setFilesystemUploadUploadLogStatus(Integer v) { this.filesystemUploadUploadLogStatus = v; }

    /**
     * 获取filesystemuploadupload记录日志消息
     *
     * @return 获取filesystemuploadupload日志消息的结果
     */
    public String getFilesystemUploadUploadLogMessage() { return filesystemUploadUploadLogMessage; }
    /**
     * 设置filesystemuploadupload记录日志消息
     *
     * @param v v
     */
    public void setFilesystemUploadUploadLogMessage(String v) { this.filesystemUploadUploadLogMessage = v; }

    /**
     * 获取filesystemuploadupload记录日志重试计算数量
     *
     * @return 获取filesystemuploadupload日志重试数量的结果
     */
    public Integer getFilesystemUploadUploadLogRetryCount() { return filesystemUploadUploadLogRetryCount; }
    /**
     * 设置filesystemuploadupload记录日志重试计算数量
     *
     * @param v v
     */
    public void setFilesystemUploadUploadLogRetryCount(Integer v) { this.filesystemUploadUploadLogRetryCount = v; }

    /**
     * 获取filesystemuploadupload记录日志开始时间
     *
     * @return 获取filesystemuploadupload日志启动时间的结果
     */
    public LocalDateTime getFilesystemUploadUploadLogStartTime() { return filesystemUploadUploadLogStartTime; }
    /**
     * 设置filesystemuploadupload记录日志开始时间
     *
     * @param v v
     */
    public void setFilesystemUploadUploadLogStartTime(LocalDateTime v) { this.filesystemUploadUploadLogStartTime = v; }

    /**
     * 获取filesystemuploadupload记录日志结束时间
     *
     * @return 获取filesystemuploadupload日志结束时间的结果
     */
    public LocalDateTime getFilesystemUploadUploadLogEndTime() { return filesystemUploadUploadLogEndTime; }
    /**
     * 设置filesystemuploadupload记录日志结束时间
     *
     * @param v v
     */
    public void setFilesystemUploadUploadLogEndTime(LocalDateTime v) { this.filesystemUploadUploadLogEndTime = v; }

    /**
     * 上传结果状态常量。
     * @author CH
     * @since 4.0.0
     */
    public static class Result {
        /**
         * 成功
        */
        public static final int SUCCESS = 1;
        /**
         * 失败
        */
        public static final int FAILED = 0;
    }

    /**
     * 阶段标识常量。
     * @author CH
     * @since 4.0.0
     */
    public static class Stage {
        /**
         * 上传
        */
        public static final int UPLOAD = 2;
    }
}

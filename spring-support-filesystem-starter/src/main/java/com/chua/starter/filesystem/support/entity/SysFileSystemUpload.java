package com.chua.starter.filesystem.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分片上传任务实体。
 *
 * <p>对应数据库表 {@code filesystem_upload_task}，记录一次文件上传任务的全生命周期状态。
 *
 * @author CH
 * @since 4.0.0
 */
@Schema(description = "文件系统分片上传任务表")
@TableName(value = "filesystem_upload_task")
public class SysFileSystemUpload extends SysBase implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 主键 标识，自增
    */
    @TableId(value = "filesystem_upload_task_id", type = IdType.AUTO)
    @Schema(description = "主键 ID")
    private Long filesystemUploadTaskId; // filesystemupload任务标识

    /**
     * 上传任务唯一标识（ULID）
    */
    @TableField(value = "filesystem_upload_task_upload_id")
    @Schema(description = "上传任务 ID（ULID）")
    private String filesystemUploadTaskUploadId; // filesystemupload任务uploadid

    /**
     * 文件原始名称
    */
    @TableField("filesystem_upload_task_file_name")
    @Schema(description = "文件名称")
    private String filesystemUploadTaskFileName; // filesystemupload任务文件名称

    /**
     * 文件原始名称
    */
    @TableField("filesystem_upload_task_original_name")
    @Schema(description = "文件原始名称")
    private String filesystemUploadTaskOriginalName; // filesystemupload任务原始名称

    /**
     * 文件大小（字节）
    */
    @TableField("filesystem_upload_task_file_size")
    @Schema(description = "文件大小(字节)")
    private Long filesystemUploadTaskFileSize; // filesystemupload任务文件大小

    /**
     * 单个分片大小（字节）
    */
    @TableField("filesystem_upload_task_chunk_size")
    @Schema(description = "分片大小(字节)")
    private Long filesystemUploadTaskChunkSize; // filesystemupload任务chunk大小

    /**
     * 总分片数量
    */
    @TableField("filesystem_upload_task_total_chunks")
    @Schema(description = "总分片数")
    private Integer filesystemUploadTaskTotalChunks; // filesystemupload任务totalchunks

    /**
     * 已上传分片数量
    */
    @TableField("filesystem_upload_task_uploaded_chunks")
    @Schema(description = "已上传分片数")
    private Integer filesystemUploadTaskUploadedChunks; // filesystemupload任务uploadedchunks

    /**
     * 存储类型（本地 / 对象存储 / s3 等）
    */
    @TableField("filesystem_upload_task_storage_type")
    @Schema(description = "存储类型")
    private String filesystemUploadTaskStorageType; // filesystemupload任务storage类型

    /**
     * 存储桶名称
    */
    @TableField("filesystem_upload_task_bucket")
    @Schema(description = "存储桶")
    private String filesystemUploadTaskBucket; // filesystemupload任务bucket

    /**
     * 文件在存储中的 Key
    */
    @TableField("filesystem_upload_task_key")
    @Schema(description = "文件Key")
    private String filesystemUploadTaskKey; // filesystemupload任务Key

    /**
     * 内容类型（MIME 类型）
    */
    @TableField("filesystem_upload_task_content_type")
    @Schema(description = "内容类型")
    private String filesystemUploadTaskContentType; // filesystemupload任务内容类型

    /**
     * 任务状态：
     * PENDING=0, UPLOADING=1, PENDING_合并=2, 合并=3, 完成=4,
     * PENDING_UPLOAD=5, UPLOADING_转为_对象存储=6, 失败=-1, CANCELLED=-2
     */
    @TableField("filesystem_upload_task_status")
    @Schema(description = "状态: 0-待上传, 1-上传中, 2-待合并, 3-合并中, 4-已完成, 5-待上传OSS, 6-上传OSS中, -1-失败, -2-已取消")
    private Integer filesystemUploadTaskStatus; // filesystemupload任务状态

    /**
     * 处理阶段：
     * CHUNK_UPLOAD=0, 合并=1, UPLOAD_转为_对象存储=2
     */
    @TableField("filesystem_upload_task_stage")
    @Schema(description = "阶段: 0-分片上传, 1-合并, 2-上传OSS")
    private Integer filesystemUploadTaskStage; // filesystemupload任务Stage

    /**
     * 失败原因
    */
    @TableField("filesystem_upload_task_error_message")
    @Schema(description = "错误信息")
    private String filesystemUploadTaskErrorMessage; // filesystemupload任务错误消息

    /**
     * 最终文件 URL
    */
    @TableField("filesystem_upload_task_file_url")
    @Schema(description = "最终文件URL")
    private String filesystemUploadTaskFileUrl; // filesystemupload任务文件url

    /**
     * 上传开始时间
    */
    @TableField("filesystem_upload_task_start_time")
    @Schema(description = "上传开始时间")
    private LocalDateTime filesystemUploadTaskStartTime; // filesystemupload任务启动时间

    /**
     * 上传完成时间
    */
    @TableField("filesystem_upload_task_complete_time")
    @Schema(description = "上传完成时间")
    private LocalDateTime filesystemUploadTaskCompleteTime; // filesystemupload任务完成时间

    /**
     * 重试次数
    */
    @TableField("filesystem_upload_task_retry_count")
    @Schema(description = "合并重试次数")
    private Integer filesystemUploadTaskRetryCount; // filesystemupload任务重试数量

    /**
     * sys文件系统upload。
     */
    public SysFileSystemUpload() {
    }

    /**
     * 获取filesystemupload任务标识。
     * @return 获取filesystemupload任务id的结果
     */
    public Long getFilesystemUploadTaskId() {
        return filesystemUploadTaskId;
    }

    /**
     * 设置filesystemupload任务标识。
     * @param filesystemUploadTaskId filesystemupload任务标识
     */
    public void setFilesystemUploadTaskId(Long filesystemUploadTaskId) {
        this.filesystemUploadTaskId = filesystemUploadTaskId;
    }

    /**
     * 获取filesystemupload任务uploadid。
     * @return 获取filesystemupload任务uploadid的结果
     */
    public String getFilesystemUploadTaskUploadId() {
        return filesystemUploadTaskUploadId;
    }

    /**
     * 设置filesystemupload任务uploadid。
     * @param filesystemUploadTaskUploadId filesystemupload任务uploadid
     */
    public void setFilesystemUploadTaskUploadId(String filesystemUploadTaskUploadId) {
        this.filesystemUploadTaskUploadId = filesystemUploadTaskUploadId;
    }

    /**
     * 获取filesystemupload任务文件名称。
     * @return 获取filesystemupload任务文件名称的结果
     */
    public String getFilesystemUploadTaskFileName() {
        return filesystemUploadTaskFileName;
    }

    /**
     * 设置filesystemupload任务文件名称。
     * @param filesystemUploadTaskFileName filesystemupload任务文件名称
     */
    public void setFilesystemUploadTaskFileName(String filesystemUploadTaskFileName) {
        this.filesystemUploadTaskFileName = filesystemUploadTaskFileName;
    }

    /**
     * 获取filesystemupload任务原始名称。
     * @return 获取filesystemupload任务原始名称的结果
     */
    public String getFilesystemUploadTaskOriginalName() {
        return filesystemUploadTaskOriginalName;
    }

    /**
     * 设置filesystemupload任务原始名称。
     * @param filesystemUploadTaskOriginalName filesystemupload任务原始名称
     */
    public void setFilesystemUploadTaskOriginalName(String filesystemUploadTaskOriginalName) {
        this.filesystemUploadTaskOriginalName = filesystemUploadTaskOriginalName;
    }

    /**
     * 获取filesystemupload任务文件大小。
     * @return 获取filesystemupload任务文件大小的结果
     */
    public Long getFilesystemUploadTaskFileSize() {
        return filesystemUploadTaskFileSize;
    }

    /**
     * 设置filesystemupload任务文件大小。
     * @param filesystemUploadTaskFileSize filesystemupload任务文件大小
     */
    public void setFilesystemUploadTaskFileSize(Long filesystemUploadTaskFileSize) {
        this.filesystemUploadTaskFileSize = filesystemUploadTaskFileSize;
    }

    /**
     * 获取filesystemupload任务chunk大小。
     * @return 获取filesystemupload任务chunk大小的结果
     */
    public Long getFilesystemUploadTaskChunkSize() {
        return filesystemUploadTaskChunkSize;
    }

    /**
     * 设置filesystemupload任务chunk大小。
     * @param filesystemUploadTaskChunkSize filesystemupload任务chunk大小
     */
    public void setFilesystemUploadTaskChunkSize(Long filesystemUploadTaskChunkSize) {
        this.filesystemUploadTaskChunkSize = filesystemUploadTaskChunkSize;
    }

    /**
     * 获取filesystemupload任务totalchunks。
     * @return 获取filesystemupload任务totalchunks的结果
     */
    public Integer getFilesystemUploadTaskTotalChunks() {
        return filesystemUploadTaskTotalChunks;
    }

    /**
     * 设置filesystemupload任务totalchunks。
     * @param filesystemUploadTaskTotalChunks filesystemupload任务totalchunks
     */
    public void setFilesystemUploadTaskTotalChunks(Integer filesystemUploadTaskTotalChunks) {
        this.filesystemUploadTaskTotalChunks = filesystemUploadTaskTotalChunks;
    }

    /**
     * 获取filesystemupload任务uploadedchunks。
     * @return 获取filesystemupload任务uploadedchunks的结果
     */
    public Integer getFilesystemUploadTaskUploadedChunks() {
        return filesystemUploadTaskUploadedChunks;
    }

    /**
     * 设置filesystemupload任务uploadedchunks。
     * @param filesystemUploadTaskUploadedChunks filesystemupload任务uploadedchunks
     */
    public void setFilesystemUploadTaskUploadedChunks(Integer filesystemUploadTaskUploadedChunks) {
        this.filesystemUploadTaskUploadedChunks = filesystemUploadTaskUploadedChunks;
    }

    /**
     * 获取filesystemupload任务storage类型。
     * @return 获取filesystemupload任务storage类型的结果
     */
    public String getFilesystemUploadTaskStorageType() {
        return filesystemUploadTaskStorageType;
    }

    /**
     * 设置filesystemupload任务storage类型。
     * @param filesystemUploadTaskStorageType filesystemupload任务storage类型
     */
    public void setFilesystemUploadTaskStorageType(String filesystemUploadTaskStorageType) {
        this.filesystemUploadTaskStorageType = filesystemUploadTaskStorageType;
    }

    /**
     * 获取filesystemupload任务bucket。
     * @return 获取filesystemupload任务bucket的结果
     */
    public String getFilesystemUploadTaskBucket() {
        return filesystemUploadTaskBucket;
    }

    /**
     * 设置filesystemupload任务bucket。
     * @param filesystemUploadTaskBucket filesystemupload任务bucket
     */
    public void setFilesystemUploadTaskBucket(String filesystemUploadTaskBucket) {
        this.filesystemUploadTaskBucket = filesystemUploadTaskBucket;
    }

    /**
     * 获取filesystemupload任务Key。
     * @return 获取filesystemupload任务Key的结果
     */
    public String getFilesystemUploadTaskKey() {
        return filesystemUploadTaskKey;
    }

    /**
     * 设置filesystemupload任务Key。
     * @param filesystemUploadTaskKey filesystemupload任务Key
     */
    public void setFilesystemUploadTaskKey(String filesystemUploadTaskKey) {
        this.filesystemUploadTaskKey = filesystemUploadTaskKey;
    }

    /**
     * 获取filesystemupload任务内容类型。
     * @return 获取filesystemupload任务内容类型的结果
     */
    public String getFilesystemUploadTaskContentType() {
        return filesystemUploadTaskContentType;
    }

    /**
     * 设置filesystemupload任务内容类型。
     * @param filesystemUploadTaskContentType filesystemupload任务内容类型
     */
    public void setFilesystemUploadTaskContentType(String filesystemUploadTaskContentType) {
        this.filesystemUploadTaskContentType = filesystemUploadTaskContentType;
    }

    /**
     * 获取filesystemupload任务状态。
     * @return 获取filesystemupload任务状态的结果
     */
    public Integer getFilesystemUploadTaskStatus() {
        return filesystemUploadTaskStatus;
    }

    /**
     * 设置filesystemupload任务状态。
     * @param filesystemUploadTaskStatus filesystemupload任务状态
     */
    public void setFilesystemUploadTaskStatus(Integer filesystemUploadTaskStatus) {
        this.filesystemUploadTaskStatus = filesystemUploadTaskStatus;
    }

    /**
     * 获取filesystemupload任务Stage。
     * @return 获取filesystemupload任务Stage的结果
     */
    public Integer getFilesystemUploadTaskStage() {
        return filesystemUploadTaskStage;
    }

    /**
     * 设置filesystemupload任务Stage。
     * @param filesystemUploadTaskStage filesystemupload任务Stage
     */
    public void setFilesystemUploadTaskStage(Integer filesystemUploadTaskStage) {
        this.filesystemUploadTaskStage = filesystemUploadTaskStage;
    }

    /**
     * 获取filesystemupload任务错误消息。
     * @return 获取filesystemupload任务错误消息的结果
     */
    public String getFilesystemUploadTaskErrorMessage() {
        return filesystemUploadTaskErrorMessage;
    }

    /**
     * 设置filesystemupload任务错误消息。
     * @param filesystemUploadTaskErrorMessage filesystemupload任务错误消息
     */
    public void setFilesystemUploadTaskErrorMessage(String filesystemUploadTaskErrorMessage) {
        this.filesystemUploadTaskErrorMessage = filesystemUploadTaskErrorMessage;
    }

    /**
     * 获取filesystemupload任务文件url。
     * @return 获取filesystemupload任务文件url的结果
     */
    public String getFilesystemUploadTaskFileUrl() {
        return filesystemUploadTaskFileUrl;
    }

    /**
     * 设置filesystemupload任务文件url。
     * @param filesystemUploadTaskFileUrl filesystemupload任务文件url
     */
    public void setFilesystemUploadTaskFileUrl(String filesystemUploadTaskFileUrl) {
        this.filesystemUploadTaskFileUrl = filesystemUploadTaskFileUrl;
    }

    /**
     * 获取filesystemupload任务启动时间。
     * @return 获取filesystemupload任务启动时间的结果
     */
    public LocalDateTime getFilesystemUploadTaskStartTime() {
        return filesystemUploadTaskStartTime;
    }

    /**
     * 设置filesystemupload任务启动时间。
     * @param filesystemUploadTaskStartTime filesystemupload任务启动时间
     */
    public void setFilesystemUploadTaskStartTime(LocalDateTime filesystemUploadTaskStartTime) {
        this.filesystemUploadTaskStartTime = filesystemUploadTaskStartTime;
    }

    /**
     * 获取filesystemupload任务完成时间。
     * @return 获取filesystemupload任务完成时间的结果
     */
    public LocalDateTime getFilesystemUploadTaskCompleteTime() {
        return filesystemUploadTaskCompleteTime;
    }

    /**
     * 设置filesystemupload任务完成时间。
     * @param filesystemUploadTaskCompleteTime filesystemupload任务完成时间
     */
    public void setFilesystemUploadTaskCompleteTime(LocalDateTime filesystemUploadTaskCompleteTime) {
        this.filesystemUploadTaskCompleteTime = filesystemUploadTaskCompleteTime;
    }

    /**
     * 获取filesystemupload任务重试数量。
     * @return 获取filesystemupload任务重试数量的结果
     */
    public Integer getFilesystemUploadTaskRetryCount() {
        return filesystemUploadTaskRetryCount;
    }

    /**
     * 设置filesystemupload任务重试数量。
     * @param filesystemUploadTaskRetryCount filesystemupload任务重试数量
     */
    public void setFilesystemUploadTaskRetryCount(Integer filesystemUploadTaskRetryCount) {
        this.filesystemUploadTaskRetryCount = filesystemUploadTaskRetryCount;
    }

    /**
     * 任务状态常量。
     * @author CH
     * @since 4.0.0
     */
    public static class Status {
        public static final int PENDING = 0; // PENDING
        public static final int UPLOADING = 1; // UPLOADING
        public static final int PENDING_MERGE = 2; // pending合并
        public static final int MERGING = 3; // 合并
        public static final int COMPLETED = 4; // 完成
        public static final int PENDING_UPLOAD = 5; // PENDING_UPLOAD
        public static final int UPLOADING_TO_OSS = 6; // uploading转为对象存储
        public static final int FAILED = -1; // 失败
        public static final int CANCELLED = -2; // CANCELLED
    }

    /**
     * 处理阶段常量。
     * @author CH
     * @since 4.0.0
     */
    public static class Stage {
        public static final int CHUNK_UPLOAD = 0; // CHUNK_UPLOAD
        public static final int MERGE = 1; // 合并
        public static final int UPLOAD_TO_OSS = 2; // upload转为对象存储
    }
}

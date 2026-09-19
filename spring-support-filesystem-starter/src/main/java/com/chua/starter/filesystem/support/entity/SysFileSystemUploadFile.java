package com.chua.starter.filesystem.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件系统上传完成主体表（分片合并后的最终文件记录）。
 *
 * @since 4.0.0.42
 * @author CH
 */
@TableName(value = "filesystem_upload_file")
public class SysFileSystemUploadFile extends SysBase implements Serializable {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_file_id", type = IdType.AUTO)
    /**
     * Filesystem上传文件标识
    */
    private Long filesystemUploadFileId;

    /**
     * 上传任务标识，关联 filesystem_upload_任务.filesystem_upload_任务_upload_标识
    */
    @TableField("filesystem_upload_file_upload_id")
    /**
     * Filesystem上传文件上传标识
    */
    private String filesystemUploadFileUploadId;

    /**
     * 文件名称
    */
    @TableField("filesystem_upload_file_name")
    /**
     * Filesystem上传文件名称
    */
    private String filesystemUploadFileName;

    /**
     * 文件原始名称
    */
    @TableField("filesystem_upload_file_original_name")
    /**
     * Filesystem上传文件原始名称
    */
    private String filesystemUploadFileOriginalName;

    /**
     * 文件大小(字节)
    */
    @TableField("filesystem_upload_file_size")
    /**
     * Filesystem上传文件尺寸
    */
    private Long filesystemUploadFileSize;

    /**
     * 存储类型 (本地/minio/对象存储/COS/...)
    */
    @TableField("filesystem_upload_file_storage_type")
    /**
     * Filesystem上传文件存储类型
    */
    private String filesystemUploadFileStorageType;

    /**
     * 存储桶
    */
    @TableField("filesystem_upload_file_bucket")
    /**
     * Filesystem上传文件存储桶
    */
    private String filesystemUploadFileBucket;

    /**
     * 文件Key
    */
    @TableField("filesystem_upload_file_key")
    /**
     * Filesystem上传文件密钥
    */
    private String filesystemUploadFileKey;

    /**
     * 文件访问URL
    */
    @TableField("filesystem_upload_file_url")
    /**
     * Filesystem上传文件URL
    */
    private String filesystemUploadFileUrl;

    /**
     * 内容类型(MIME)
    */
    @TableField("filesystem_upload_file_content_type")
    /**
     * Filesystem上传文件内容类型
    */
    private String filesystemUploadFileContentType;

    /**
     * 文件e标签
    */
    @TableField("filesystem_upload_file_etag")
    /**
     * Filesystem上传文件etag
    */
    private String filesystemUploadFileEtag;

    /**
     * 合并完成时间
    */
    @TableField("filesystem_upload_file_complete_time")
    /**
     * Filesystem上传文件完成时间
    */
    private LocalDateTime filesystemUploadFileCompleteTime;

    /**
     * 创建 sys文件系统upload文件 实例
    */
    public SysFileSystemUploadFile() {}

    /**
     * 获取filesystemupload文件标识
     *
     * @return 获取filesystemupload文件id的结果
     */
    public Long getFilesystemUploadFileId() { return filesystemUploadFileId; }
    /**
     * 设置filesystemupload文件标识
     *
     * @param v v
     */
    public void setFilesystemUploadFileId(Long v) { this.filesystemUploadFileId = v; }

    /**
     * 获取filesystemupload文件uploadid
     *
     * @return 获取filesystemupload文件uploadid的结果
     */
    public String getFilesystemUploadFileUploadId() { return filesystemUploadFileUploadId; }
    /**
     * 设置filesystemupload文件uploadid
     *
     * @param v v
     */
    public void setFilesystemUploadFileUploadId(String v) { this.filesystemUploadFileUploadId = v; }

    /**
     * 获取filesystemupload文件名称
     *
     * @return 获取filesystemupload文件名称的结果
     */
    public String getFilesystemUploadFileName() { return filesystemUploadFileName; }
    /**
     * 设置filesystemupload文件名称
     *
     * @param v v
     */
    public void setFilesystemUploadFileName(String v) { this.filesystemUploadFileName = v; }

    /**
     * 获取filesystemupload文件原始名称
     *
     * @return 获取filesystemupload文件原始名称的结果
     */
    public String getFilesystemUploadFileOriginalName() { return filesystemUploadFileOriginalName; }
    /**
     * 设置filesystemupload文件原始名称
     *
     * @param v v
     */
    public void setFilesystemUploadFileOriginalName(String v) { this.filesystemUploadFileOriginalName = v; }

    /**
     * 获取filesystemupload文件获取大小
     *
     * @return 获取filesystemupload文件大小的结果
     */
    public Long getFilesystemUploadFileSize() { return filesystemUploadFileSize; }
    /**
     * 设置filesystemupload文件获取大小
     *
     * @param v v
     */
    public void setFilesystemUploadFileSize(Long v) { this.filesystemUploadFileSize = v; }

    /**
     * 获取filesystemupload文件storage类型
     *
     * @return 获取filesystemupload文件storage类型的结果
     */
    public String getFilesystemUploadFileStorageType() { return filesystemUploadFileStorageType; }
    /**
     * 设置filesystemupload文件storage类型
     *
     * @param v v
     */
    public void setFilesystemUploadFileStorageType(String v) { this.filesystemUploadFileStorageType = v; }

    /**
     * 获取filesystemupload文件bucket
     *
     * @return 获取filesystemupload文件bucket的结果
     */
    public String getFilesystemUploadFileBucket() { return filesystemUploadFileBucket; }
    /**
     * 设置filesystemupload文件bucket
     *
     * @param v v
     */
    public void setFilesystemUploadFileBucket(String v) { this.filesystemUploadFileBucket = v; }

    /**
     * 获取filesystemupload文件Key
     *
     * @return 获取filesystemupload文件Key的结果
     */
    public String getFilesystemUploadFileKey() { return filesystemUploadFileKey; }
    /**
     * 设置filesystemupload文件Key
     *
     * @param v v
     */
    public void setFilesystemUploadFileKey(String v) { this.filesystemUploadFileKey = v; }

    /**
     * 获取filesystemupload文件url
     *
     * @return 获取filesystemupload文件url的结果
     */
    public String getFilesystemUploadFileUrl() { return filesystemUploadFileUrl; }
    /**
     * 设置filesystemupload文件url
     *
     * @param v v
     */
    public void setFilesystemUploadFileUrl(String v) { this.filesystemUploadFileUrl = v; }

    /**
     * 获取filesystemupload文件内容类型
     *
     * @return 获取filesystemupload文件内容类型的结果
     */
    public String getFilesystemUploadFileContentType() { return filesystemUploadFileContentType; }
    /**
     * 设置filesystemupload文件内容类型
     *
     * @param v v
     */
    public void setFilesystemUploadFileContentType(String v) { this.filesystemUploadFileContentType = v; }

    /**
     * 获取filesystemupload文件etag
     *
     * @return 获取filesystemupload文件etag的结果
     */
    public String getFilesystemUploadFileEtag() { return filesystemUploadFileEtag; }
    /**
     * 设置filesystemupload文件etag
     *
     * @param v v
     */
    public void setFilesystemUploadFileEtag(String v) { this.filesystemUploadFileEtag = v; }

    /**
     * 获取filesystemupload文件完成时间
     *
     * @return 获取filesystemupload文件完成时间的结果
     */
    public LocalDateTime getFilesystemUploadFileCompleteTime() { return filesystemUploadFileCompleteTime; }
    /**
     * 设置filesystemupload文件完成时间
     *
     * @param v v
     */
    public void setFilesystemUploadFileCompleteTime(LocalDateTime v) { this.filesystemUploadFileCompleteTime = v; }
}

package com.chua.starter.filesystem.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件系统分片上传详情表。
 *
 * @since 4.0.0.42
 * @author CH
 */
@TableName(value = "filesystem_upload_chunk")
public class SysFileSystemUploadChunk extends SysBase implements Serializable {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_chunk_id", type = IdType.AUTO)
    /**
     * Filesystem上传chunkid
    */
    private Long filesystemUploadChunkId;

    /**
     * 上传任务标识，关联 filesystem_upload_任务.filesystem_upload_任务_upload_标识
    */
    @TableField("filesystem_upload_chunk_upload_id")
    /**
     * Filesystem上传chunk上传标识
    */
    private String filesystemUploadChunkUploadId;

    /**
     * 分片序号(1-based)
    */
    @TableField("filesystem_upload_chunk_part_number")
    /**
     * Filesystem上传chunkpart数字
    */
    private Integer filesystemUploadChunkPartNumber;

    /**
     * 分片大小(字节)
    */
    @TableField("filesystem_upload_chunk_size")
    /**
     * Filesystem上传chunk尺寸
    */
    private Long filesystemUploadChunkSize;

    /**
     * 分片e标签
    */
    @TableField("filesystem_upload_chunk_etag")
    /**
     * Filesystem上传chunketag
    */
    private String filesystemUploadChunkEtag;

    /**
     * 分片存储路径（本地文件路径或对象存储Key）
    */
    @TableField("filesystem_upload_chunk_storage_path")
    /**
     * Filesystem上传chunk存储路径
    */
    private String filesystemUploadChunkStoragePath;

    /**
     * 分片状态: 0-已上传, 1-已合并, -1-失败
    */
    @TableField("filesystem_upload_chunk_status")
    /**
     * Filesystem上传chunk状态
    */
    private Integer filesystemUploadChunkStatus;

    /**
     * 上传时间
    */
    @TableField("filesystem_upload_chunk_upload_time")
    /**
     * Filesystem上传chunk上传时间
    */
    private LocalDateTime filesystemUploadChunkUploadTime;

    /**
     * 创建 sys文件系统uploadchunk 实例
    */
    public SysFileSystemUploadChunk() {}

    /**
     * 获取filesystemuploadchunkid
     *
     * @return 获取filesystemuploadchunkid的结果
     */
    public Long getFilesystemUploadChunkId() { return filesystemUploadChunkId; }
    /**
     * 设置filesystemuploadchunkid
     *
     * @param v v
     */
    public void setFilesystemUploadChunkId(Long v) { this.filesystemUploadChunkId = v; }

    /**
     * 获取filesystemuploadchunkuploadid
     *
     * @return 获取filesystemuploadchunkuploadid的结果
     */
    public String getFilesystemUploadChunkUploadId() { return filesystemUploadChunkUploadId; }
    /**
     * 设置filesystemuploadchunkuploadid
     *
     * @param v v
     */
    public void setFilesystemUploadChunkUploadId(String v) { this.filesystemUploadChunkUploadId = v; }

    /**
     * 获取filesystemuploadchunkpart数字
     *
     * @return 获取filesystemuploadchunkpart数字的结果
     */
    public Integer getFilesystemUploadChunkPartNumber() { return filesystemUploadChunkPartNumber; }
    /**
     * 设置filesystemuploadchunkpart数字
     *
     * @param v v
     */
    public void setFilesystemUploadChunkPartNumber(Integer v) { this.filesystemUploadChunkPartNumber = v; }

    /**
     * 获取filesystemuploadchunk获取大小
     *
     * @return 获取filesystemuploadchunk大小的结果
     */
    public Long getFilesystemUploadChunkSize() { return filesystemUploadChunkSize; }
    /**
     * 设置filesystemuploadchunk获取大小
     *
     * @param v v
     */
    public void setFilesystemUploadChunkSize(Long v) { this.filesystemUploadChunkSize = v; }

    /**
     * 获取filesystemuploadchunketag
     *
     * @return 获取filesystemuploadchunketag的结果
     */
    public String getFilesystemUploadChunkEtag() { return filesystemUploadChunkEtag; }
    /**
     * 设置filesystemuploadchunketag
     *
     * @param v v
     */
    public void setFilesystemUploadChunkEtag(String v) { this.filesystemUploadChunkEtag = v; }

    /**
     * 获取filesystemuploadchunkstorage路径
     *
     * @return 获取filesystemuploadchunkstorage路径的结果
     */
    public String getFilesystemUploadChunkStoragePath() { return filesystemUploadChunkStoragePath; }
    /**
     * 设置filesystemuploadchunkstorage路径
     *
     * @param v v
     */
    public void setFilesystemUploadChunkStoragePath(String v) { this.filesystemUploadChunkStoragePath = v; }

    /**
     * 获取filesystemuploadchunk状态
     *
     * @return 获取filesystemuploadchunk状态的结果
     */
    public Integer getFilesystemUploadChunkStatus() { return filesystemUploadChunkStatus; }
    /**
     * 设置filesystemuploadchunk状态
     *
     * @param v v
     */
    public void setFilesystemUploadChunkStatus(Integer v) { this.filesystemUploadChunkStatus = v; }

    /**
     * 获取filesystemuploadchunkupload时间
     *
     * @return 获取filesystemuploadchunkupload时间的结果
     */
    public LocalDateTime getFilesystemUploadChunkUploadTime() { return filesystemUploadChunkUploadTime; }
    /**
     * 设置filesystemuploadchunkupload时间
     *
     * @param v v
     */
    public void setFilesystemUploadChunkUploadTime(LocalDateTime v) { this.filesystemUploadChunkUploadTime = v; }

    /**
     * 状态类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Status {
        /**
         * Uploaded
        */
        public static final int UPLOADED = 0;
        /**
         * Merged
        */
        public static final int MERGED = 1;
        /**
         * 失败
        */
        public static final int FAILED = -1;
    }
}

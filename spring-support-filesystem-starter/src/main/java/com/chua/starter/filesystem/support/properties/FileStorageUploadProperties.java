package com.chua.starter.filesystem.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件系统分片上传配置
 *
 * @author CH
 * @since 1.0
 */
@Data
@ConfigurationProperties(prefix = FileStorageUploadProperties.PREFIX)
public class FileStorageUploadProperties {

    public static final String PREFIX = "plugin.filesystem.upload";

    /**
     * 分片大小（字节），默认 5MB
     */
    private long chunkSize = 5 * 1024 * 1024;

    /**
     * 临时目录路径
     */
    private String tempDir = System.getProperty("java.io.tmpdir") + "/filesystem-upload";

    /**
     * 终态任务保留天数，超过后定时清理（含分片/记录/文件表级联删除），默认 30 天
     */
    private int retentionDays = 30;

    /**
     * 合并调度间隔（毫秒），默认 10 秒
     */
    private long mergeIntervalMs = 10_000L;

    /**
     * 上传 OSS 调度间隔（毫秒），默认 10 秒
     */
    private long uploadIntervalMs = 10_000L;

    /**
     * 失败任务重试调度间隔（毫秒），默认 60 秒
     */
    private long retryIntervalMs = 60_000L;

    /**
     * 过期数据清理调度间隔（毫秒），默认 24 小时
     */
    private long cleanupIntervalMs = 24 * 60 * 60 * 1000L;
}
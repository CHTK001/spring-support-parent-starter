package com.chua.starter.filesystem.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件系统上传统一配置（大小上限、扩展名白名单、分片与调度参数）
 *
 * @since 1.0
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = FileStorageUploadProperties.PREFIX)
public class FileStorageUploadProperties {

    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.filesystem.upload";

    /**
     * 单文件大小上限（MB）
     */
    private long maxSizeMb = 10;

    /**
     * 允许上传的扩展名（小写，不含点）；为 null 表示不限制扩展名。
     * <p>默认仅开放图片类型，避免存储并回显可执行/可脚本内容。</p>
     */
    private List<String> allowedExtensions = new ArrayList<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp"));

    /**
     * 分片大小（字节），默认 5MB
     */
    private long chunkSize = 5 * 1024 * 1024;

    /**
     * 临时目录路径
     */
    private String tempDir = System.getProperty("java.io.tmpdir") + "/filesystem-upload";

    /**
     * 文件回显端点前缀（应用侧提供该路径的读取接口）。
     * <p>上传阶段以 {@code viewPrefix + 提供者名称 + / + 对象键} 作为访问地址落库，
     * 避免把存储后端的内部 URI（如 file:）当作浏览器地址存库。
     * 宿主应用有上下文路径时需一并写入，例如 {@code /system/api/v2/file/view/}。</p>
     */
    private String viewPrefix = "/v2/file/view/";

    /**
     * 终态任务保留天数，超过后定时清理（含分片/记录/文件表级联删除），默认 30 天
     */
    private int retentionDays = 30;

    /**
     * 合并调度间隔（毫秒），默认 10 秒
     */
    private long mergeIntervalMs = 10_000L;

    /**
     * 上传 对象存储 调度间隔（毫秒），默认 10 秒
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

package com.chua.starter.filesystem.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件系统上传统一配置（大小上限、扩展名白名单、分片与调度参数）。
 *
 * <p>对应配置前缀 {@code plugin.filesystem.upload}。分片上传任务的合并、对象存储
 * 转存、失败重试与过期清理由后台调度器按下列间隔参数驱动。</p>
 *
 * <pre>{@code
 * plugin:
 *   filesystem:
 *     upload:
 *       max-size-mb: 10
 *       allowed-extensions: [jpg, jpeg, png, gif, bmp, webp]
 *       chunk-size: 5242880
 *       view-prefix: /v2/file/view/
 *       retention-days: 30
 *       merge-interval-ms: 10000
 *       upload-interval-ms: 10000
 *       retry-interval-ms: 60000
 *       cleanup-interval-ms: 86400000
 * }</pre>
 *
 * @since 1.0
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = FileStorageUploadProperties.PREFIX)
public class FileStorageUploadProperties {

    /**
     * 配置前缀：{@code plugin.filesystem.upload}。
    */
    public static final String PREFIX = "plugin.filesystem.upload";

    /**
     * 单文件大小上限（MB）。
     *
     * <p>默认值：{@code 10}（MB）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     upload:
     *       max-size-mb: 10
     * }</pre>
     */
    // [P3C A20 豁免]
    private long maxSizeMb = 10;

    /**
     * 允许上传的扩展名（小写，不含点）；为 null 表示不限制扩展名。
     *
     * <p>默认值：{@code [jpg, jpeg, png, gif, bmp, webp]}（仅图片类型）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     upload:
     *       allowed-extensions: [jpg, jpeg, png, gif, bmp, webp]
     * }</pre>
     * <p>默认仅开放图片类型，避免存储并回显可执行/可脚本内容。</p>
     */
    private List<String> allowedExtensions = new ArrayList<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp"));

    /**
     * 分片大小（字节）。
     *
     * <p>默认值：5MB（{@code 5 * 1024 * 1024} = 5242880）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     upload:
     *       chunk-size: 5242880
     * }</pre>
     */
    // [P3C A20 豁免]
    private long chunkSize = 5 * 1024 * 1024;

    /**
     * 临时目录路径。
     *
     * <p>默认值：{@code ${java.io.tmpdir}/filesystem-upload}。
     * <b>注意：当前实现未读取该配置</b>——分片临时目录实际由任务管理器在系统临时目录下
     * 创建（{@code upload-task-chunks} / {@code upload-task-merge}），修改本项不影响行为。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     upload:
     *       temp-dir: /data/fs-upload-tmp
     * }</pre>
     */
    private String tempDir = System.getProperty("java.io.tmpdir") + "/filesystem-upload";

    /**
     * 文件回显端点前缀（应用侧提供该路径的读取接口）。
     *
     * <p>默认值：{@code "/v2/file/view/"}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     upload:
     *       view-prefix: /v2/file/view/
     * }</pre>
     * <p>上传阶段以 {@code viewPrefix + 提供者名称 + / + 对象键} 作为访问地址落库，
     * 避免把存储后端的内部 URI（如 file:）当作浏览器地址存库。
     * 宿主应用有上下文路径时需一并写入，例如 {@code /system/api/v2/file/view/}。</p>
     */
    private String viewPrefix = "/v2/file/view/";

    /**
     * 终态任务保留天数，超过后定时清理（含分片/记录/文件表级联删除）。
     *
     * <p>默认值：{@code 30}（天）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     upload:
     *       retention-days: 30
     * }</pre>
     */
    // [P3C A20 豁免]
    private int retentionDays = 30;

    /**
     * 合并调度间隔（毫秒）。
     *
     * <p>默认值：{@code 10000}（10 秒）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     upload:
     *       merge-interval-ms: 10000
     * }</pre>
     */
    private long mergeIntervalMs = 10_000L;

    /**
     * 上传对象存储调度间隔（毫秒）。
     *
     * <p>默认值：{@code 10000}（10 秒）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     upload:
     *       upload-interval-ms: 10000
     * }</pre>
     */
    private long uploadIntervalMs = 10_000L;

    /**
     * 失败任务重试调度间隔（毫秒）。
     *
     * <p>默认值：{@code 60000}（60 秒）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     upload:
     *       retry-interval-ms: 60000
     * }</pre>
     */
    private long retryIntervalMs = 60_000L;

    /**
     * 过期数据清理调度间隔（毫秒）。
     *
     * <p>默认值：{@code 86400000}（24 小时）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     upload:
     *       cleanup-interval-ms: 86400000
     * }</pre>
     */
    // [P3C A20 豁免]
    private long cleanupIntervalMs = 24 * 60 * 60 * 1000L;
}

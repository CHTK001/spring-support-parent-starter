package com.chua.report.client.starter.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文件操作响应
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileOperationResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 操作是否成功
     */
    private Boolean success;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 错误代码
     */
    private String errorCode;

    /**
     * 操作类型
     */
    private String operation;

    /**
     * 操作路径
     */
    private String path;

    /**
     * 文件信息列表
     */
    private List<FileInfo> files;

    /**
     * 单个文件信息
     */
    private FileInfo fileInfo;

    /**
     * 文件树结构
     */
    private FileInfo fileTree;

    /**
     * 文件内容（预览时使用）
     */
    private String content;

    /**
     * 文件内容类型
     */
    private String contentType;

    /**
     * 文件编码
     */
    private String encoding;

    /**
     * 文件内容字节数组（用于下载等操作）
     */
    private byte[] data;

    /**
     * 处理的文件数量
     */
    private Integer processedCount;

    /**
     * 总文件数量
     */
    private Integer totalCount;

    /**
     * 跳过的文件数量
     */
    private Integer skippedCount;

    /**
     * 失败的文件数量
     */
    private Integer failedCount;

    /**
     * 操作开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 操作结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 操作耗时（毫秒）
     */
    private Long duration;

    /**
     * 详细结果信息
     */
    private List<String> details;

    /**
     * 警告信息
     */
    private List<String> warnings;

    /**
     * 额外数据
     */
    private Map<String, Object> extraData;


    /**
     * 下载URL（下载操作时使用）
     */
    private String downloadUrl;

    /**
     * 上传进度（上传操作时使用）
     */
    private Double uploadProgress;

    /**
     * 任务ID（异步操作时使用）
     */
    private String taskId;

    /**
     * 操作状态
     */
    private String status;

    /**
     * 文件系统信息
     */
    private FileSystemInfo fileSystemInfo;

    /**
     * 获取操作耗时（秒）
     */
    public Double getDurationInSeconds() {
        return duration != null ? duration / 1000.0 : null;
    }

    /**
     * 获取成功率
     */
    public Double getSuccessRate() {
        if (totalCount == null || totalCount == 0) {
            return null;
        }
        int successCount = totalCount - (failedCount != null ? failedCount : 0);
        return (double) successCount / totalCount * 100;
    }

    /**
     * 判断是否有警告
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * 判断是否有详细信息
     */
    public boolean hasDetails() {
        return details != null && !details.isEmpty();
    }

    /**
     * 判断是否为部分成功
     */
    public boolean isPartialSuccess() {
        return success != null && success &&
               failedCount != null && failedCount > 0;
    }

    /**
     * 获取格式化的处理结果
     */
    public String getFormattedResult() {
        if (totalCount == null || totalCount == 0) {
            return "无文件处理";
        }

        StringBuilder result = new StringBuilder();
        result.append("总计: ").append(totalCount);

        if (processedCount != null) {
            result.append(", 处理: ").append(processedCount);
        }
        if (skippedCount != null && skippedCount > 0) {
            result.append(", 跳过: ").append(skippedCount);
        }
        if (failedCount != null && failedCount > 0) {
            result.append(", 失败: ").append(failedCount);
        }

        return result.toString();
    }

    /**
     * 创建成功响应
     */
    public static FileOperationResponse success(String operation, String message) {
        return FileOperationResponse.builder()
                .success(true)
                .operation(operation)
                .message(message)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建成功响应（带文件列表）
     */
    public static FileOperationResponse success(String operation, String message, List<FileInfo> files) {
        return FileOperationResponse.builder()
                .success(true)
                .operation(operation)
                .message(message)
                .files(files)
                .totalCount(files != null ? files.size() : 0)
                .processedCount(files != null ? files.size() : 0)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建成功响应（带单个文件）
     */
    public static FileOperationResponse success(String operation, String message, FileInfo fileInfo) {
        return FileOperationResponse.builder()
                .success(true)
                .operation(operation)
                .message(message)
                .fileInfo(fileInfo)
                .totalCount(1)
                .processedCount(1)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建成功响应（带文件列表和路径）
     */
    public static FileOperationResponse success(String operation, List<FileInfo> files, String path) {
        return FileOperationResponse.builder()
                .success(true)
                .operation(operation)
                .message(operation + "操作成功")
                .files(files)
                .path(path)
                .totalCount(files != null ? files.size() : 0)
                .processedCount(files != null ? files.size() : 0)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建成功响应（带单个文件和路径）
     */
    public static FileOperationResponse success(String operation, FileInfo fileInfo, String path) {
        return FileOperationResponse.builder()
                .success(true)
                .operation(operation)
                .message(operation + "操作成功")
                .fileInfo(fileInfo)
                .path(path)
                .totalCount(1)
                .processedCount(1)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建成功响应（带文件树）
     */
    public static FileOperationResponse successWithTree(String operation, String message, FileInfo fileTree) {
        return FileOperationResponse.builder()
                .success(true)
                .operation(operation)
                .message(message)
                .fileTree(fileTree)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建成功响应（带内容）
     */
    public static FileOperationResponse successWithContent(String operation, String message, String content, String contentType) {
        return FileOperationResponse.builder()
                .success(true)
                .operation(operation)
                .message(message)
                .content(content)
                .contentType(contentType)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建成功响应（带数据）
     */
    public static FileOperationResponse success(String operation, String message, Map<String, Object> data) {
        return FileOperationResponse.builder()
                .success(true)
                .operation(operation)
                .message(message)
                .extraData(data)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败响应
     */
    public static FileOperationResponse error(String operation, String message) {
        return FileOperationResponse.builder()
                .success(false)
                .operation(operation)
                .message(message)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败响应（带错误代码）
     */
    public static FileOperationResponse error(String operation, String message, String errorCode) {
        return FileOperationResponse.builder()
                .success(false)
                .operation(operation)
                .message(message)
                .errorCode(errorCode)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建部分成功响应
     */
    public static FileOperationResponse partialSuccess(String operation, String message,
                                                      int totalCount, int processedCount, int failedCount) {
        return FileOperationResponse.builder()
                .success(true)
                .operation(operation)
                .message(message)
                .totalCount(totalCount)
                .processedCount(processedCount)
                .failedCount(failedCount)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建进行中响应
     */
    public static FileOperationResponse inProgress(String operation, String taskId, String message) {
        return FileOperationResponse.builder()
                .success(true)
                .operation(operation)
                .taskId(taskId)
                .message(message)
                .status("IN_PROGRESS")
                .startTime(LocalDateTime.now())
                .build();
    }

    /**
     * 添加警告信息
     */
    public FileOperationResponse addWarning(String warning) {
        if (warnings == null) {
            warnings = new java.util.ArrayList<>();
        }
        warnings.add(warning);
        return this;
    }

    /**
     * 添加详细信息
     */
    public FileOperationResponse addDetail(String detail) {
        if (details == null) {
            details = new java.util.ArrayList<>();
        }
        details.add(detail);
        return this;
    }

    /**
     * 设置耗时
     */
    public FileOperationResponse withDuration(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        if (startTime != null && endTime != null) {
            this.duration = java.time.Duration.between(startTime, endTime).toMillis();
        }
        return this;
    }

    /**
     * 设置耗时（从开始时间戳到当前时间）
     */
    public FileOperationResponse withDuration(long startTimeMillis) {
        LocalDateTime startTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(startTimeMillis),
            java.time.ZoneId.systemDefault()
        );
        LocalDateTime endTime = LocalDateTime.now();
        return withDuration(startTime, endTime);
    }

    /**
     * 文件系统信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileSystemInfo {
        /**
         * 总空间（字节）
         */
        private Long totalSpace;

        /**
         * 可用空间（字节）
         */
        private Long freeSpace;

        /**
         * 已用空间（字节）
         */
        private Long usedSpace;

        /**
         * 使用率（百分比）
         */
        private Double usagePercentage;

        /**
         * 文件系统类型
         */
        private String fileSystemType;

        /**
         * 根路径
         */
        private String rootPath;
    }

    @Override
    public String toString() {
        return String.format("FileOperationResponse{operation='%s', success=%s, message='%s'}",
                operation, success, message);
    }
}

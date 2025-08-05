package com.chua.report.client.starter.pojo;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

/**
 * 文件操作请求
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileOperationRequest {

    /**
     * 操作类型
     */
    @NotBlank(message = "操作类型不能为空")
    private String operation;

    /**
     * 文件路径
     */
    private String path;

    /**
     * 目标路径（用于复制、移动等操作）
     */
    private String targetPath;

    /**
     * 新文件名（用于重命名操作）
     */
    private String newName;

    /**
     * 是否递归操作（用于删除目录等）
     */
    private Boolean recursive;

    /**
     * 是否覆盖已存在的文件
     */
    private Boolean overwrite;

    /**
     * 文件权限（Unix格式，如 "755"）
     */
    private String permissions;

    /**
     * 文件所有者
     */
    private String owner;

    /**
     * 文件组
     */
    private String group;

    /**
     * 搜索模式（用于搜索操作）
     */
    private String pattern;

    /**
     * 是否包含内容搜索
     */
    private Boolean includeContent;

    /**
     * 最大搜索结果数
     */
    private Integer maxResults;

    /**
     * 文件编码（用于预览操作）
     */
    private String encoding;

    /**
     * 最大预览大小
     */
    private Long maxSize;

    /**
     * 文件路径列表（用于批量操作）
     */
    private List<String> paths;

    /**
     * 压缩文件路径
     */
    private String archivePath;

    /**
     * 压缩格式
     */
    private String format;

    /**
     * 解压目标路径
     */
    private String extractPath;

    /**
     * 是否包含隐藏文件
     */
    private Boolean includeHidden;

    /**
     * 排序字段
     */
    private String sortBy;

    /**
     * 排序顺序
     */
    private String sortOrder;

    /**
     * 最大深度（用于文件树操作）
     */
    private Integer maxDepth;

    /**
     * 获取操作类型枚举
     */
    public OperationType getOperationTypeEnum() {
        return OperationType.fromCode(operation);
    }

    /**
     * 设置操作类型枚举
     */
    public void setOperationTypeEnum(OperationType operationType) {
        this.operation = operationType.getCode();
    }

    /**
     * 获取递归操作标志（带默认值）
     */
    public boolean getRecursiveOrDefault() {
        return recursive != null ? recursive : false;
    }

    /**
     * 获取覆盖标志（带默认值）
     */
    public boolean getOverwriteOrDefault() {
        return overwrite != null ? overwrite : false;
    }

    /**
     * 获取包含隐藏文件标志（带默认值）
     */
    public boolean getIncludeHiddenOrDefault() {
        return includeHidden != null ? includeHidden : false;
    }

    /**
     * 获取排序字段（带默认值）
     */
    public String getSortByOrDefault() {
        return sortBy != null ? sortBy : "name";
    }

    /**
     * 获取排序顺序（带默认值）
     */
    public String getSortOrderOrDefault() {
        return sortOrder != null ? sortOrder : "asc";
    }

    /**
     * 获取最大深度（带默认值）
     */
    public int getMaxDepthOrDefault() {
        return maxDepth != null ? maxDepth : 3;
    }

    /**
     * 获取包含内容搜索标志（带默认值）
     */
    public boolean getIncludeContentOrDefault() {
        return includeContent != null ? includeContent : false;
    }

    /**
     * 获取最大结果数量（带默认值）
     */
    public int getMaxResultsOrDefault() {
        return maxResults != null ? maxResults : 100;
    }

    /**
     * 获取文件编码（带默认值）
     */
    public String getEncodingOrDefault() {
        return encoding != null ? encoding : "UTF-8";
    }

    /**
     * 获取最大文件大小（带默认值）
     */
    public long getMaxSizeOrDefault() {
        return maxSize != null ? maxSize : 1024 * 1024; // 1MB
    }

    /**
     * 获取压缩格式（带默认值）
     */
    public String getFormatOrDefault() {
        return format != null ? format : "zip";
    }

    /**
     * 验证请求参数
     */
    public boolean isValid() {
        if (operation == null || operation.trim().isEmpty()) {
            return false;
        }

        try {
            OperationType operationType = getOperationTypeEnum();

            switch (operationType) {
                case LIST:
                case TREE:
                case DELETE:
                case MKDIR:
                case INFO:
                case PREVIEW:
                    return path != null && !path.trim().isEmpty();
                case RENAME:
                    return path != null && !path.trim().isEmpty() &&
                            newName != null && !newName.trim().isEmpty();
                case COPY:
                case MOVE:
                    return path != null && !path.trim().isEmpty() &&
                            targetPath != null && !targetPath.trim().isEmpty();
                case SEARCH:
                    return path != null && !path.trim().isEmpty() &&
                            pattern != null && !pattern.trim().isEmpty();
                case COMPRESS:
                    return paths != null && !paths.isEmpty() &&
                            archivePath != null && !archivePath.trim().isEmpty();
                case EXTRACT:
                    return path != null && !path.trim().isEmpty() &&
                            extractPath != null && !extractPath.trim().isEmpty();
                case CHMOD:
                    return path != null && !path.trim().isEmpty() &&
                            permissions != null && !permissions.trim().isEmpty();
                case CHOWN:
                    return path != null && !path.trim().isEmpty() &&
                            (owner != null || group != null);
                default:
                    return true;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 获取验证错误信息
     */
    public String getValidationError() {
        if (operation == null || operation.trim().isEmpty()) {
            return "操作类型不能为空";
        }

        try {
            OperationType operationType = getOperationTypeEnum();

            switch (operationType) {
                case LIST:
                case TREE:
                case DELETE:
                case MKDIR:
                case INFO:
                case PREVIEW:
                    if (path == null || path.trim().isEmpty()) {
                        return "文件路径不能为空";
                    }
                    break;
                case RENAME:
                    if (path == null || path.trim().isEmpty()) {
                        return "文件路径不能为空";
                    }
                    if (newName == null || newName.trim().isEmpty()) {
                        return "新名称不能为空";
                    }
                    break;
                case COPY:
                case MOVE:
                    if (path == null || path.trim().isEmpty()) {
                        return "源路径不能为空";
                    }
                    if (targetPath == null || targetPath.trim().isEmpty()) {
                        return "目标路径不能为空";
                    }
                    break;
                case SEARCH:
                    if (path == null || path.trim().isEmpty()) {
                        return "搜索路径不能为空";
                    }
                    if (pattern == null || pattern.trim().isEmpty()) {
                        return "搜索模式不能为空";
                    }
                    break;
                case COMPRESS:
                    if (paths == null || paths.isEmpty()) {
                        return "压缩文件列表不能为空";
                    }
                    if (archivePath == null || archivePath.trim().isEmpty()) {
                        return "归档路径不能为空";
                    }
                    break;
                case EXTRACT:
                    if (path == null || path.trim().isEmpty()) {
                        return "压缩文件路径不能为空";
                    }
                    if (extractPath == null || extractPath.trim().isEmpty()) {
                        return "解压路径不能为空";
                    }
                    break;
                case CHMOD:
                    if (path == null || path.trim().isEmpty()) {
                        return "文件路径不能为空";
                    }
                    if (permissions == null || permissions.trim().isEmpty()) {
                        return "文件权限不能为空";
                    }
                    break;
                case CHOWN:
                    if (path == null || path.trim().isEmpty()) {
                        return "文件路径不能为空";
                    }
                    if (owner == null && group == null) {
                        return "所有者或组不能都为空";
                    }
                    break;
            }

            return null;
        } catch (IllegalArgumentException e) {
            return "未知的操作类型: " + operation;
        }
    }

    /**
     * 验证请求参数，如果无效则抛出异常
     */
    public void validate() {
        String error = getValidationError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    /**
     * 操作类型枚举
     */
    @Getter
    public enum OperationType {
        LIST("LIST", "列出文件"),
        TREE("TREE", "获取文件树"),
        UPLOAD("UPLOAD", "上传文件"),
        DOWNLOAD("DOWNLOAD", "下载文件"),
        DELETE("DELETE", "删除文件"),
        RENAME("RENAME", "重命名文件"),
        MKDIR("MKDIR", "创建目录"),
        COPY("COPY", "复制文件"),
        MOVE("MOVE", "移动文件"),
        CHMOD("CHMOD", "修改权限"),
        CHOWN("CHOWN", "修改所有者"),
        SEARCH("SEARCH", "搜索文件"),
        PREVIEW("PREVIEW", "预览文件"),
        INFO("INFO", "获取文件信息"),
        COMPRESS("COMPRESS", "压缩文件"),
        EXTRACT("EXTRACT", "解压文件");

        private final String code;
        private final String desc;

        OperationType(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public static OperationType fromCode(String code) {
            for (OperationType type : values()) {
                if (type.getCode().equals(code)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("未知的操作类型: " + code);
        }
    }

    /**
     * 创建列表操作请求
     */
    public static FileOperationRequest createListRequest(String path) {
        return FileOperationRequest.builder()
                .operation("LIST")
                .path(path)
                .build();
    }

    /**
     * 创建删除操作请求
     */
    public static FileOperationRequest createDeleteRequest(String path, boolean recursive) {
        return FileOperationRequest.builder()
                .operation("DELETE")
                .path(path)
                .recursive(recursive)
                .build();
    }

    /**
     * 创建重命名操作请求
     */
    public static FileOperationRequest createRenameRequest(String path, String newName) {
        return FileOperationRequest.builder()
                .operation("RENAME")
                .path(path)
                .newName(newName)
                .build();
    }
}

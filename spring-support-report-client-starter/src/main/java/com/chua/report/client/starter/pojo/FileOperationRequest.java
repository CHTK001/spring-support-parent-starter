package com.chua.report.client.starter.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
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
     * 获取递归标志，默认为false
     */
    public Boolean getRecursiveOrDefault() {
        return recursive != null ? recursive : false;
    }

    /**
     * 获取覆盖标志，默认为false
     */
    public Boolean getOverwriteOrDefault() {
        return overwrite != null ? overwrite : false;
    }

    /**
     * 获取包含内容标志，默认为false
     */
    public Boolean getIncludeContentOrDefault() {
        return includeContent != null ? includeContent : false;
    }

    /**
     * 获取最大结果数，默认为100
     */
    public Integer getMaxResultsOrDefault() {
        return maxResults != null ? maxResults : 100;
    }

    /**
     * 获取包含隐藏文件标志，默认为false
     */
    public Boolean getIncludeHiddenOrDefault() {
        return includeHidden != null ? includeHidden : false;
    }

    /**
     * 获取最大深度，默认为10
     */
    public Integer getMaxDepthOrDefault() {
        return maxDepth != null ? maxDepth : 10;
    }

    /**
     * 操作类型枚举
     */
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

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
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

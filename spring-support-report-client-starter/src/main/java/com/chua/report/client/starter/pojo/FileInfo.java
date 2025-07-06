package com.chua.report.client.starter.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件信息
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    /**
     * 文件名
     */
    private String name;

    /**
     * 文件路径
     */
    private String path;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * 是否为目录
     */
    private Boolean isDirectory;

    /**
     * 是否为隐藏文件
     */
    private Boolean isHidden;

    /**
     * 是否可读
     */
    private Boolean canRead;

    /**
     * 是否可写
     */
    private Boolean canWrite;

    /**
     * 是否可执行
     */
    private Boolean canExecute;

    /**
     * 最后修改时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastModified;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件扩展名
     */
    private String extension;

    /**
     * 文件权限（Unix格式）
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
     * 子文件列表（仅目录）
     */
    private List<FileInfo> children;

    /**
     * 获取格式化的文件大小
     */
    public String getFormattedSize() {
        if (size == null || isDirectory) {
            return "-";
        }
        
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取完整路径
     */
    public String getFullPath() {
        if (path == null) {
            return name;
        }
        if (path.endsWith("/") || path.endsWith("\\")) {
            return path + name;
        } else {
            return path + "/" + name;
        }
    }

    /**
     * 创建简单文件信息
     */
    public static FileInfo createSimple(String name, String path, boolean isDirectory) {
        return FileInfo.builder()
                .name(name)
                .path(path)
                .isDirectory(isDirectory)
                .build();
    }

    /**
     * 创建目录信息
     */
    public static FileInfo createDirectory(String name, String path) {
        return FileInfo.builder()
                .name(name)
                .path(path)
                .isDirectory(true)
                .size(0L)
                .build();
    }

    /**
     * 创建文件信息
     */
    public static FileInfo createFile(String name, String path, long size) {
        return FileInfo.builder()
                .name(name)
                .path(path)
                .isDirectory(false)
                .size(size)
                .build();
    }
}

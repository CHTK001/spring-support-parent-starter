package com.chua.report.client.starter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 文件管理配置属性
 * @author CH
 * @since 2024/12/19
 */
@Data
@ConfigurationProperties(prefix = FileManagementProperties.PREFIX)
public class FileManagementProperties {

    public static final String PREFIX = "plugin.report.client.file-management";

    /**
     * 是否启用文件管理功能
     */
    private boolean enable = false;

    /**
     * 文件操作根目录
     * 所有文件操作都限制在此目录下
     */
    private String rootDirectory = System.getProperty("login.home");

    /**
     * 允许的文件扩展名列表
     * 空列表表示允许所有文件类型
     */
    private List<String> allowedExtensions = Arrays.asList();

    /**
     * 禁止的文件扩展名列表
     */
    private List<String> forbiddenExtensions = Arrays.asList(
        "exe", "bat", "cmd", "sh", "ps1", "vbs", "jar", "war", "ear"
    );

    /**
     * 单个文件最大大小（字节）
     * 默认100MB
     */
    private long maxFileSize = 100 * 1024 * 1024L;

    /**
     * 单次上传最大文件数量
     */
    private int maxFileCount = 10;

    /**
     * 是否允许创建目录
     */
    private boolean allowCreateDirectory = true;

    /**
     * 是否允许删除文件
     */
    private boolean allowDeleteFile = true;

    /**
     * 是否允许删除目录
     */
    private boolean allowDeleteDirectory = false;

    /**
     * 是否允许重命名文件
     */
    private boolean allowRenameFile = true;

    /**
     * 是否允许移动文件
     */
    private boolean allowMoveFile = true;

    /**
     * 是否允许修改文件权限
     */
    private boolean allowChangePermissions = false;

    /**
     * 预览文件最大大小（字节）
     * 默认1MB
     */
    private long maxPreviewSize = 1024 * 1024L;

    /**
     * 预览文件支持的编码列表
     */
    private List<String> previewEncodings = Arrays.asList(
        "UTF-8", "GBK", "GB2312", "ISO-8859-1"
    );

    /**
     * 文件树最大深度
     */
    private int maxTreeDepth = 10;

    /**
     * 是否显示隐藏文件
     */
    private boolean showHiddenFiles = false;

    /**
     * 临时文件目录
     */
    private String tempDirectory = System.getProperty("java.io.tmpdir");

    /**
     * 文件操作超时时间（毫秒）
     */
    private long operationTimeout = 30000L;

    /**
     * 是否启用文件操作日志
     */
    private boolean enableOperationLog = true;

    /**
     * 健康检查有效期（秒）
     * 客户端健康状态的有效期，超过此时间文件管理接口将不可用
     */
    private long healthValidityPeriod = 300L; // 5分钟

    /**
     * 检查文件扩展名是否被允许
     */
    public boolean isExtensionAllowed(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        
        String extension = getFileExtension(filename).toLowerCase();
        
        // 检查是否在禁止列表中
        if (forbiddenExtensions.contains(extension)) {
            return false;
        }
        
        // 如果允许列表为空，则允许所有不在禁止列表中的文件
        if (allowedExtensions.isEmpty()) {
            return true;
        }
        
        // 检查是否在允许列表中
        return allowedExtensions.contains(extension);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 检查文件大小是否超过限制
     */
    public boolean isFileSizeAllowed(long fileSize) {
        return fileSize <= maxFileSize;
    }

    /**
     * 检查路径是否在根目录下
     */
    public boolean isPathAllowed(String path) {
        if (path == null) {
            return false;
        }
        
        try {
            String normalizedPath = java.nio.file.Paths.get(path).normalize().toString();
            String normalizedRoot = java.nio.file.Paths.get(rootDirectory).normalize().toString();
            
            return normalizedPath.startsWith(normalizedRoot);
        } catch (Exception e) {
            return false;
        }
    }
}

package com.chua.report.client.starter.service;

import com.chua.report.client.starter.pojo.FileInfo;
import com.chua.report.client.starter.pojo.FileOperationRequest;
import com.chua.report.client.starter.pojo.FileOperationResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理服务接口
 * 
 * @author CH
 * @since 2024/12/19
 */
public interface FileManagementService {

    /**
     * 列出目录文件
     * 
     * @param path          目录路径
     * @param includeHidden 是否包含隐藏文件
     * @param sortBy        排序字段
     * @param sortOrder     排序顺序
     * @return 文件列表
     */
    FileOperationResponse listFiles(String path, Boolean includeHidden, String sortBy, String sortOrder);

    /**
     * 获取文件树结构
     * 
     * @param path          根路径
     * @param maxDepth      最大深度
     * @param includeHidden 是否包含隐藏文件
     * @return 文件树
     */
    FileOperationResponse getFileTree(String path, Integer maxDepth, Boolean includeHidden);

    /**
     * 获取文件树结构（支持懒加载）
     * 
     * @param path          根路径
     * @param maxDepth      最大深度
     * @param includeHidden 是否包含隐藏文件
     * @param lazyLoad      是否启用懒加载模式
     * @param pageSize      每页文件数量限制
     * @param pageIndex     页码（从0开始）
     * @return 文件树
     */
    FileOperationResponse getFileTree(String path, Integer maxDepth, Boolean includeHidden, Boolean lazyLoad,
            Integer pageSize, Integer pageIndex);

    /**
     * 上传文件
     * 
     * @param targetPath 目标路径
     * @param file       上传的文件
     * @param overwrite  是否覆盖已存在的文件
     * @return 操作结果
     */
    FileOperationResponse uploadFile(String targetPath, MultipartFile file, Boolean overwrite);

    /**
     * 下载文件
     * 
     * @param filePath 文件路径
     * @return 文件内容
     */
    FileOperationResponse downloadFile(String filePath);

    /**
     * 删除文件或目录
     * 
     * @param path      文件路径
     * @param recursive 是否递归删除
     * @return 操作结果
     */
    FileOperationResponse deleteFile(String path, Boolean recursive);

    /**
     * 重命名文件
     * 
     * @param path    原文件路径
     * @param newName 新文件名
     * @return 操作结果
     */
    FileOperationResponse renameFile(String path, String newName);

    /**
     * 创建目录
     * 
     * @param path 目录路径
     * @return 操作结果
     */
    FileOperationResponse createDirectory(String path);

    /**
     * 移动文件
     * 
     * @param sourcePath 源路径
     * @param targetPath 目标路径
     * @param overwrite  是否覆盖
     * @return 操作结果
     */
    FileOperationResponse moveFile(String sourcePath, String targetPath, Boolean overwrite);

    /**
     * 复制文件
     * 
     * @param sourcePath 源路径
     * @param targetPath 目标路径
     * @param overwrite  是否覆盖
     * @return 操作结果
     */
    FileOperationResponse copyFile(String sourcePath, String targetPath, Boolean overwrite);

    /**
     * 修改文件权限
     * 
     * @param path        文件路径
     * @param permissions 权限字符串
     * @return 操作结果
     */
    FileOperationResponse changePermissions(String path, String permissions);

    /**
     * 预览文件内容
     * 
     * @param filePath 文件路径
     * @param encoding 文件编码
     * @param maxSize  最大预览大小
     * @return 文件内容
     */
    FileOperationResponse previewFile(String filePath, String encoding, Long maxSize);

    /**
     * 获取文件信息
     * 
     * @param path 文件路径
     * @return 文件信息
     */
    FileOperationResponse getFileInfo(String path);

    /**
     * 搜索文件
     * 
     * @param path           搜索路径
     * @param pattern        搜索模式
     * @param includeContent 是否搜索内容
     * @param maxResults     最大结果数
     * @return 搜索结果
     */
    FileOperationResponse searchFiles(String path, String pattern, Boolean includeContent, Integer maxResults);

    /**
     * 获取文件系统信息
     * 
     * @param path 路径
     * @return 文件系统信息
     */
    FileOperationResponse getFileSystemInfo(String path);

    /**
     * 执行文件操作
     * 
     * @param request 操作请求
     * @return 操作结果
     */
    FileOperationResponse executeOperation(FileOperationRequest request);

    /**
     * 验证路径是否安全
     * 
     * @param path 文件路径
     * @return 是否安全
     */
    boolean isPathSafe(String path);

    /**
     * 验证文件是否允许操作
     * 
     * @param filename 文件名
     * @param fileSize 文件大小
     * @return 是否允许
     */
    boolean isFileAllowed(String filename, Long fileSize);
}

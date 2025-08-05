package com.chua.report.client.starter.service.impl;

import com.chua.report.client.starter.pojo.FileInfo;
import com.chua.report.client.starter.pojo.FileOperationRequest;
import com.chua.report.client.starter.pojo.FileOperationResponse;
import com.chua.report.client.starter.properties.FileManagementProperties;
import com.chua.report.client.starter.service.FileManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件管理服务实现
 * 
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileManagementServiceImpl implements FileManagementService {

    private final FileManagementProperties properties;

    @Override
    public FileOperationResponse listFiles(String path, Boolean includeHidden, String sortBy, String sortOrder) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(path)) {
                return FileOperationResponse.error("LIST", "路径不安全或超出允许范围", "INVALID_PATH");
            }

            Path dirPath = Paths.get(path);
            if (!Files.exists(dirPath)) {
                return FileOperationResponse.error("LIST", "目录不存在", "DIR_NOT_FOUND");
            }

            if (!Files.isDirectory(dirPath)) {
                return FileOperationResponse.error("LIST", "路径不是目录", "NOT_DIRECTORY");
            }

            boolean showHidden = includeHidden != null ? includeHidden : properties.isShowHiddenFiles();

            List<FileInfo> files = new ArrayList<>();
            try (Stream<Path> stream = Files.list(dirPath)) {
                files = stream.filter(p -> showHidden || !isHidden(p)).map(this::convertToFileInfo)
                        .filter(Objects::nonNull).collect(Collectors.toList());
            }

            // 排序
            sortFiles(files, sortBy, sortOrder);

            FileOperationResponse response = FileOperationResponse.success("LIST", "列出文件成功", files);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.debug("列出目录文件成功: path={}, count={}, duration={}ms", path, files.size(), response.getDuration());

            return response;

        } catch (Exception e) {
            log.error("列出目录文件失败: path={}", path, e);
            return FileOperationResponse.error("LIST", "列出文件失败: " + e.getMessage(), "LIST_ERROR");
        }
    }

    @Override
    public FileOperationResponse getFileTree(String path, Integer maxDepth, Boolean includeHidden) {
        return getFileTree(path, maxDepth, includeHidden, false, 1000, 0);
    }

    @Override
    public FileOperationResponse getFileTree(String path, Integer maxDepth, Boolean includeHidden, Boolean lazyLoad,
            Integer pageSize, Integer pageIndex) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("客户端文件管理: 获取文件树 - path={}, maxDepth={}, lazyLoad={}, pageSize={}, pageIndex={}", path, maxDepth,
                    lazyLoad, pageSize, pageIndex);

            if (!isPathSafe(path)) {
                return FileOperationResponse.error("TREE", "路径不安全或超出允许范围", "INVALID_PATH");
            }

            int depth = maxDepth != null ? maxDepth : properties.getMaxTreeDepth();
            boolean showHidden = includeHidden != null ? includeHidden : properties.isShowHiddenFiles();

            FileInfo rootInfo;

            // 特殊处理根目录请求
            if ("/".equals(path)) {
                log.info("处理根目录请求，检测系统类型");
                rootInfo = buildSystemRootTree(depth, showHidden, lazyLoad, pageSize != null ? pageSize : 1000,
                        pageIndex != null ? pageIndex : 0);
            } else {
                Path rootPath = Paths.get(path);
                if (!Files.exists(rootPath)) {
                    return FileOperationResponse.error("TREE", "路径不存在", "PATH_NOT_FOUND");
                }

                if (Boolean.TRUE.equals(lazyLoad)) {
                    // 懒加载模式：只加载当前层级的文件，支持分页
                    rootInfo = buildFileTreeLazy(rootPath, depth, showHidden, pageSize != null ? pageSize : 1000,
                            pageIndex != null ? pageIndex : 0);
                } else {
                    // 传统模式：递归加载所有层级
                    rootInfo = buildFileTree(rootPath, depth, showHidden, 0);
                }
            }

            FileOperationResponse response = FileOperationResponse.successWithTree("TREE", "获取文件树成功", rootInfo);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.debug("获取文件树成功: path={}, maxDepth={}, lazyLoad={}, duration={}ms", path, depth, lazyLoad,
                    response.getDuration());

            return response;

        } catch (Exception e) {
            log.error("获取文件树失败: path={}", path, e);
            return FileOperationResponse.error("TREE", "获取文件树失败: " + e.getMessage(), "TREE_ERROR");
        }
    }

    @Override
    public FileOperationResponse uploadFile(String targetPath, MultipartFile file, Boolean overwrite) {
        long startTime = System.currentTimeMillis();

        try {
            if (file == null || file.isEmpty()) {
                return FileOperationResponse.error("UPLOAD", "文件为空", "EMPTY_FILE");
            }

            if (!isFileAllowed(file.getOriginalFilename(), file.getSize())) {
                return FileOperationResponse.error("UPLOAD", "文件不被允许", "FILE_NOT_ALLOWED");
            }

            if (!isPathSafe(targetPath)) {
                return FileOperationResponse.error("UPLOAD", "目标路径不安全", "INVALID_PATH");
            }

            Path target = Paths.get(targetPath, file.getOriginalFilename());

            if (Files.exists(target) && !(overwrite != null && overwrite)) {
                return FileOperationResponse.error("UPLOAD", "文件已存在", "FILE_EXISTS");
            }

            // 确保目标目录存在
            Files.createDirectories(target.getParent());

            // 保存文件
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }

            FileInfo fileInfo = convertToFileInfo(target);

            FileOperationResponse response = FileOperationResponse.success("UPLOAD", "文件上传成功", fileInfo);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.info("文件上传成功: target={}, size={}, duration={}ms", target, file.getSize(), response.getDuration());

            return response;

        } catch (Exception e) {
            log.error("文件上传失败: targetPath={}, filename={}", targetPath,
                    file != null ? file.getOriginalFilename() : "null", e);
            return FileOperationResponse.error("UPLOAD", "文件上传失败: " + e.getMessage(), "UPLOAD_ERROR");
        }
    }

    @Override
    public FileOperationResponse downloadFile(String filePath) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(filePath)) {
                return FileOperationResponse.error("DOWNLOAD", "文件路径不安全", "INVALID_PATH");
            }

            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return FileOperationResponse.error("DOWNLOAD", "文件不存在", "FILE_NOT_FOUND");
            }

            if (Files.isDirectory(path)) {
                return FileOperationResponse.error("DOWNLOAD", "不能下载目录", "IS_DIRECTORY");
            }

            byte[] data = Files.readAllBytes(path);
            FileInfo fileInfo = convertToFileInfo(path);

            FileOperationResponse response = FileOperationResponse.success("DOWNLOAD", "文件下载成功", fileInfo);
            response.setContentBytes(data);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.debug("文件下载成功: path={}, size={}, duration={}ms", filePath, data.length, response.getDuration());

            return response;

        } catch (Exception e) {
            log.error("文件下载失败: path={}", filePath, e);
            return FileOperationResponse.error("DOWNLOAD", "文件下载失败: " + e.getMessage(), "DOWNLOAD_ERROR");
        }
    }

    @Override
    public FileOperationResponse deleteFile(String path, Boolean recursive) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(path)) {
                return FileOperationResponse.error("DELETE", "文件路径不安全", "INVALID_PATH");
            }

            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return FileOperationResponse.error("DELETE", "文件不存在", "FILE_NOT_FOUND");
            }

            boolean isDirectory = Files.isDirectory(filePath);

            // 检查权限
            if (isDirectory && !properties.isAllowDeleteDirectory()) {
                return FileOperationResponse.error("DELETE", "不允许删除目录", "DELETE_DIR_FORBIDDEN");
            }

            if (!isDirectory && !properties.isAllowDeleteFile()) {
                return FileOperationResponse.error("DELETE", "不允许删除文件", "DELETE_FILE_FORBIDDEN");
            }

            if (isDirectory && !(recursive != null && recursive)) {
                try (Stream<Path> stream = Files.list(filePath)) {
                    if (stream.findAny().isPresent()) {
                        return FileOperationResponse.error("DELETE", "目录不为空，需要递归删除", "DIR_NOT_EMPTY");
                    }
                }
            }

            // 执行删除
            if (isDirectory && recursive != null && recursive) {
                deleteDirectoryRecursively(filePath);
            } else {
                Files.delete(filePath);
            }

            FileOperationResponse response = FileOperationResponse.success("DELETE", "删除成功");
            response.setDuration(System.currentTimeMillis() - startTime);

            log.info("文件删除成功: path={}, isDirectory={}, recursive={}, duration={}ms", path, isDirectory, recursive,
                    response.getDuration());

            return response;

        } catch (Exception e) {
            log.error("文件删除失败: path={}", path, e);
            return FileOperationResponse.error("DELETE", "文件删除失败: " + e.getMessage(), "DELETE_ERROR");
        }
    }

    // 辅助方法
    private FileInfo convertToFileInfo(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

            return FileInfo.builder().name(path.getFileName().toString())
                    .path(path.getParent() != null ? path.getParent().toString() : "")
                    .size(attrs.isDirectory() ? 0L : attrs.size()).isDirectory(attrs.isDirectory())
                    .isHidden(isHidden(path)).canRead(Files.isReadable(path)).canWrite(Files.isWritable(path))
                    .canExecute(Files.isExecutable(path))
                    .lastModified(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()))
                    .createTime(LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault()))
                    .extension(getFileExtension(path.getFileName().toString())).permissions(getPermissions(path))
                    .leaf(!attrs.isDirectory()) // 设置 leaf 属性：文件为叶子节点，目录不是叶子节点
                    .build();

        } catch (Exception e) {
            log.warn("转换文件信息失败: path={}", path, e);
            return null;
        }
    }

    private boolean isHidden(Path path) {
        try {
            return Files.isHidden(path);
        } catch (Exception e) {
            return path.getFileName().toString().startsWith(".");
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    private String getPermissions(Path path) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            return PosixFilePermissions.toString(permissions);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public boolean isPathSafe(String path) {
        return properties.isPathAllowed(path);
    }

    @Override
    public boolean isFileAllowed(String filename, Long fileSize) {
        if (filename == null) {
            return false;
        }

        if (!properties.isExtensionAllowed(filename)) {
            return false;
        }

        return fileSize == null || properties.isFileSizeAllowed(fileSize);
    }

    @Override
    public FileOperationResponse renameFile(String path, String newName) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(path)) {
                return FileOperationResponse.error("RENAME", "文件路径不安全", "INVALID_PATH");
            }

            if (!properties.isAllowRenameFile()) {
                return FileOperationResponse.error("RENAME", "不允许重命名文件", "RENAME_FORBIDDEN");
            }

            Path sourcePath = Paths.get(path);
            if (!Files.exists(sourcePath)) {
                return FileOperationResponse.error("RENAME", "文件不存在", "FILE_NOT_FOUND");
            }

            Path targetPath = sourcePath.getParent().resolve(newName);
            if (Files.exists(targetPath)) {
                return FileOperationResponse.error("RENAME", "目标文件已存在", "TARGET_EXISTS");
            }

            Files.move(sourcePath, targetPath);

            FileInfo fileInfo = convertToFileInfo(targetPath);
            FileOperationResponse response = FileOperationResponse.success("RENAME", "文件重命名成功", fileInfo);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.info("文件重命名成功: {} -> {}, duration={}ms", path, newName, response.getDuration());
            return response;

        } catch (Exception e) {
            log.error("文件重命名失败: path={}, newName={}", path, newName, e);
            return FileOperationResponse.error("RENAME", "文件重命名失败: " + e.getMessage(), "RENAME_ERROR");
        }
    }

    @Override
    public FileOperationResponse createDirectory(String path) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(path)) {
                return FileOperationResponse.error("MKDIR", "目录路径不安全", "INVALID_PATH");
            }

            if (!properties.isAllowCreateDirectory()) {
                return FileOperationResponse.error("MKDIR", "不允许创建目录", "MKDIR_FORBIDDEN");
            }

            Path dirPath = Paths.get(path);
            if (Files.exists(dirPath)) {
                return FileOperationResponse.error("MKDIR", "目录已存在", "DIR_EXISTS");
            }

            Files.createDirectories(dirPath);

            FileInfo dirInfo = convertToFileInfo(dirPath);
            FileOperationResponse response = FileOperationResponse.success("MKDIR", "目录创建成功", dirInfo);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.info("目录创建成功: path={}, duration={}ms", path, response.getDuration());
            return response;

        } catch (Exception e) {
            log.error("目录创建失败: path={}", path, e);
            return FileOperationResponse.error("MKDIR", "目录创建失败: " + e.getMessage(), "MKDIR_ERROR");
        }
    }

    @Override
    public FileOperationResponse moveFile(String sourcePath, String targetPath, Boolean overwrite) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(sourcePath) || !isPathSafe(targetPath)) {
                return FileOperationResponse.error("MOVE", "文件路径不安全", "INVALID_PATH");
            }

            if (!properties.isAllowMoveFile()) {
                return FileOperationResponse.error("MOVE", "不允许移动文件", "MOVE_FORBIDDEN");
            }

            Path source = Paths.get(sourcePath);
            Path target = Paths.get(targetPath);

            if (!Files.exists(source)) {
                return FileOperationResponse.error("MOVE", "源文件不存在", "SOURCE_NOT_FOUND");
            }

            if (Files.exists(target) && !(overwrite != null && overwrite)) {
                return FileOperationResponse.error("MOVE", "目标文件已存在", "TARGET_EXISTS");
            }

            // 确保目标目录存在
            Files.createDirectories(target.getParent());

            StandardCopyOption[] options = overwrite != null && overwrite
                    ? new StandardCopyOption[] { StandardCopyOption.REPLACE_EXISTING }
                    : new StandardCopyOption[0];

            Files.move(source, target, options);

            FileInfo fileInfo = convertToFileInfo(target);
            FileOperationResponse response = FileOperationResponse.success("MOVE", "文件移动成功", fileInfo);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.info("文件移动成功: {} -> {}, duration={}ms", sourcePath, targetPath, response.getDuration());
            return response;

        } catch (Exception e) {
            log.error("文件移动失败: sourcePath={}, targetPath={}", sourcePath, targetPath, e);
            return FileOperationResponse.error("MOVE", "文件移动失败: " + e.getMessage(), "MOVE_ERROR");
        }
    }

    @Override
    public FileOperationResponse copyFile(String sourcePath, String targetPath, Boolean overwrite) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(sourcePath) || !isPathSafe(targetPath)) {
                return FileOperationResponse.error("COPY", "文件路径不安全", "INVALID_PATH");
            }

            Path source = Paths.get(sourcePath);
            Path target = Paths.get(targetPath);

            if (!Files.exists(source)) {
                return FileOperationResponse.error("COPY", "源文件不存在", "SOURCE_NOT_FOUND");
            }

            if (Files.exists(target) && !(overwrite != null && overwrite)) {
                return FileOperationResponse.error("COPY", "目标文件已存在", "TARGET_EXISTS");
            }

            // 确保目标目录存在
            Files.createDirectories(target.getParent());

            if (Files.isDirectory(source)) {
                copyDirectoryRecursively(source, target, overwrite);
            } else {
                StandardCopyOption[] options = overwrite != null && overwrite
                        ? new StandardCopyOption[] { StandardCopyOption.REPLACE_EXISTING }
                        : new StandardCopyOption[0];
                Files.copy(source, target, options);
            }

            FileInfo fileInfo = convertToFileInfo(target);
            FileOperationResponse response = FileOperationResponse.success("COPY", "文件复制成功", fileInfo);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.info("文件复制成功: {} -> {}, duration={}ms", sourcePath, targetPath, response.getDuration());
            return response;

        } catch (Exception e) {
            log.error("文件复制失败: sourcePath={}, targetPath={}", sourcePath, targetPath, e);
            return FileOperationResponse.error("COPY", "文件复制失败: " + e.getMessage(), "COPY_ERROR");
        }
    }

    @Override
    public FileOperationResponse changePermissions(String path, String permissions) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(path)) {
                return FileOperationResponse.error("CHMOD", "文件路径不安全", "INVALID_PATH");
            }

            if (!properties.isAllowChangePermissions()) {
                return FileOperationResponse.error("CHMOD", "不允许修改文件权限", "CHMOD_FORBIDDEN");
            }

            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return FileOperationResponse.error("CHMOD", "文件不存在", "FILE_NOT_FOUND");
            }

            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permissions);
                Files.setPosixFilePermissions(filePath, perms);
            } catch (UnsupportedOperationException e) {
                return FileOperationResponse.error("CHMOD", "当前系统不支持POSIX权限", "UNSUPPORTED_OPERATION");
            }

            FileInfo fileInfo = convertToFileInfo(filePath);
            FileOperationResponse response = FileOperationResponse.success("CHMOD", "文件权限修改成功", fileInfo);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.info("文件权限修改成功: path={}, permissions={}, duration={}ms", path, permissions, response.getDuration());
            return response;

        } catch (Exception e) {
            log.error("文件权限修改失败: path={}, permissions={}", path, permissions, e);
            return FileOperationResponse.error("CHMOD", "文件权限修改失败: " + e.getMessage(), "CHMOD_ERROR");
        }
    }

    @Override
    public FileOperationResponse previewFile(String filePath, String encoding, Long maxSize) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(filePath)) {
                return FileOperationResponse.error("PREVIEW", "文件路径不安全", "INVALID_PATH");
            }

            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return FileOperationResponse.error("PREVIEW", "文件不存在", "FILE_NOT_FOUND");
            }

            if (Files.isDirectory(path)) {
                return FileOperationResponse.error("PREVIEW", "不能预览目录", "IS_DIRECTORY");
            }

            long fileSize = Files.size(path);
            long maxPreviewSize = maxSize != null ? maxSize : properties.getMaxPreviewSize();

            if (fileSize > maxPreviewSize) {
                return FileOperationResponse.error("PREVIEW", "文件太大，无法预览", "FILE_TOO_LARGE");
            }

            String fileEncoding = encoding != null ? encoding : "UTF-8";
            String content = new String(Files.readAllBytes(path), fileEncoding);

            FileOperationResponse response = FileOperationResponse.successWithContent("PREVIEW", "文件预览成功", content,
                    "text/plain");
            response.setDuration(System.currentTimeMillis() - startTime);

            log.debug("文件预览成功: path={}, size={}, encoding={}, duration={}ms", filePath, fileSize, fileEncoding,
                    response.getDuration());
            return response;

        } catch (Exception e) {
            log.error("文件预览失败: path={}", filePath, e);
            return FileOperationResponse.error("PREVIEW", "文件预览失败: " + e.getMessage(), "PREVIEW_ERROR");
        }
    }

    @Override
    public FileOperationResponse getFileInfo(String path) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(path)) {
                return FileOperationResponse.error("INFO", "文件路径不安全", "INVALID_PATH");
            }

            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return FileOperationResponse.error("INFO", "文件不存在", "FILE_NOT_FOUND");
            }

            FileInfo fileInfo = convertToFileInfo(filePath);

            FileOperationResponse response = FileOperationResponse.success("INFO", "获取文件信息成功", fileInfo);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.debug("获取文件信息成功: path={}, duration={}ms", path, response.getDuration());
            return response;

        } catch (Exception e) {
            log.error("获取文件信息失败: path={}", path, e);
            return FileOperationResponse.error("INFO", "获取文件信息失败: " + e.getMessage(), "INFO_ERROR");
        }
    }

    @Override
    public FileOperationResponse searchFiles(String path, String pattern, Boolean includeContent, Integer maxResults) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(path)) {
                return FileOperationResponse.error("SEARCH", "搜索路径不安全", "INVALID_PATH");
            }

            Path searchPath = Paths.get(path);
            if (!Files.exists(searchPath)) {
                return FileOperationResponse.error("SEARCH", "搜索路径不存在", "PATH_NOT_FOUND");
            }

            int maxRes = maxResults != null ? maxResults : 100;
            boolean searchContent = includeContent != null ? includeContent : false;

            List<FileInfo> results = new ArrayList<>();

            Files.walkFileTree(searchPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= maxRes) {
                        return FileVisitResult.TERMINATE;
                    }

                    String fileName = file.getFileName().toString();
                    boolean matches = pattern == null || fileName.contains(pattern)
                            || fileName.matches(pattern.replace("*", ".*"));

                    if (!matches && searchContent) {
                        try {
                            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                            matches = content.contains(pattern);
                        } catch (Exception e) {
                            // 忽略无法读取的文件
                        }
                    }

                    if (matches) {
                        FileInfo fileInfo = convertToFileInfo(file);
                        if (fileInfo != null) {
                            results.add(fileInfo);
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            FileOperationResponse response = FileOperationResponse.success("SEARCH", "文件搜索成功", results);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.debug("文件搜索完成: path={}, pattern={}, results={}, duration={}ms", path, pattern, results.size(),
                    response.getDuration());
            return response;

        } catch (Exception e) {
            log.error("文件搜索失败: path={}, pattern={}", path, pattern, e);
            return FileOperationResponse.error("SEARCH", "文件搜索失败: " + e.getMessage(), "SEARCH_ERROR");
        }
    }

    @Override
    public FileOperationResponse getFileSystemInfo(String path) {
        long startTime = System.currentTimeMillis();

        try {
            if (!isPathSafe(path)) {
                return FileOperationResponse.error("FSINFO", "路径不安全", "INVALID_PATH");
            }

            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return FileOperationResponse.error("FSINFO", "路径不存在", "PATH_NOT_FOUND");
            }

            FileStore store = Files.getFileStore(filePath);

            long totalSpace = store.getTotalSpace();
            long freeSpace = store.getUsableSpace();
            long usedSpace = totalSpace - freeSpace;
            double usagePercentage = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;

            FileOperationResponse.FileSystemInfo fsInfo = FileOperationResponse.FileSystemInfo.builder()
                    .totalSpace(totalSpace).freeSpace(freeSpace).usedSpace(usedSpace).usagePercentage(usagePercentage)
                    .fileSystemType(store.type()).rootPath(filePath.getRoot().toString()).build();

            FileOperationResponse response = FileOperationResponse.success("FSINFO", "获取文件系统信息成功");
            response.setFileSystemInfo(fsInfo);
            response.setDuration(System.currentTimeMillis() - startTime);

            log.debug("获取文件系统信息成功: path={}, totalSpace={}, freeSpace={}, duration={}ms", path, totalSpace, freeSpace,
                    response.getDuration());
            return response;

        } catch (Exception e) {
            log.error("获取文件系统信息失败: path={}", path, e);
            return FileOperationResponse.error("FSINFO", "获取文件系统信息失败: " + e.getMessage(), "FSINFO_ERROR");
        }
    }

    @Override
    public FileOperationResponse executeOperation(FileOperationRequest request) {
        if (request == null || request.getOperation() == null) {
            return FileOperationResponse.error("UNKNOWN", "操作请求无效", "INVALID_REQUEST");
        }

        String operation = request.getOperation().toUpperCase();

        switch (operation) {
        case "LIST":
            return listFiles(request.getPath(), request.getIncludeHiddenOrDefault(), request.getSortBy(),
                    request.getSortOrder());
        case "TREE":
            return getFileTree(request.getPath(), request.getMaxDepthOrDefault(), request.getIncludeHiddenOrDefault());
        case "DELETE":
            return deleteFile(request.getPath(), request.getRecursiveOrDefault());
        case "RENAME":
            return renameFile(request.getPath(), request.getNewName());
        case "MKDIR":
            return createDirectory(request.getPath());
        case "MOVE":
            return moveFile(request.getPath(), request.getTargetPath(), request.getOverwriteOrDefault());
        case "COPY":
            return copyFile(request.getPath(), request.getTargetPath(), request.getOverwriteOrDefault());
        case "CHMOD":
            return changePermissions(request.getPath(), request.getPermissions());
        case "PREVIEW":
            return previewFile(request.getPath(), request.getEncoding(), request.getMaxSize());
        case "INFO":
            return getFileInfo(request.getPath());
        case "SEARCH":
            return searchFiles(request.getPath(), request.getPattern(), request.getIncludeContentOrDefault(),
                    request.getMaxResultsOrDefault());
        case "FSINFO":
            return getFileSystemInfo(request.getPath());
        default:
            return FileOperationResponse.error(operation, "不支持的操作类型", "UNSUPPORTED_OPERATION");
        }
    }

    // 辅助方法
    private void sortFiles(List<FileInfo> files, String sortBy, String sortOrder) {
        if (files == null || files.isEmpty() || sortBy == null) {
            return;
        }

        boolean ascending = !"desc".equalsIgnoreCase(sortOrder);

        Comparator<FileInfo> comparator = null;
        switch (sortBy.toLowerCase()) {
        case "name":
            comparator = Comparator.comparing(FileInfo::getName, String.CASE_INSENSITIVE_ORDER);
            break;
        case "size":
            comparator = Comparator.comparing(f -> f.getSize() != null ? f.getSize() : 0L);
            break;
        case "modified":
            comparator = Comparator
                    .comparing(f -> f.getLastModified() != null ? f.getLastModified() : LocalDateTime.MIN);
            break;
        case "type":
            comparator = Comparator
                    .comparing((FileInfo f) -> f.getIsDirectory() != null && f.getIsDirectory() ? "0" : "1")
                    .thenComparing((FileInfo f) -> f.getExtension() != null ? f.getExtension() : "");
            break;
        default:
            comparator = Comparator.comparing(FileInfo::getName, String.CASE_INSENSITIVE_ORDER);
            break;
        }

        if (comparator != null && !ascending) {
            comparator = comparator.reversed();
        }

        // 目录总是排在前面
        files.sort(Comparator.comparing((FileInfo f) -> f.getIsDirectory() != null && f.getIsDirectory() ? 0 : 1)
                .thenComparing(comparator));
    }

    private FileInfo buildFileTree(Path path, int maxDepth, boolean includeHidden, int currentDepth) {
        if (currentDepth >= maxDepth) {
            return null;
        }

        FileInfo fileInfo = convertToFileInfo(path);
        if (fileInfo == null) {
            return null;
        }

        if (fileInfo.getIsDirectory() != null && fileInfo.getIsDirectory()) {
            List<FileInfo> children = new ArrayList<>();
            try (Stream<Path> stream = Files.list(path)) {
                children = stream.filter(p -> includeHidden || !isHidden(p))
                        .map(p -> buildFileTree(p, maxDepth, includeHidden, currentDepth + 1)).filter(Objects::nonNull)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("构建文件树失败: path={}", path, e);
            }
            fileInfo.setChildren(children);
        }

        return fileInfo;
    }

    /**
     * 构建文件树（懒加载模式） 只加载当前层级的文件夹，不递归加载子目录
     */
    private FileInfo buildFileTreeLazy(Path path, int maxDepth, boolean includeHidden, int pageSize, int pageIndex) {
        FileInfo fileInfo = convertToFileInfo(path);
        if (fileInfo == null) {
            return null;
        }

        if (fileInfo.getIsDirectory() != null && fileInfo.getIsDirectory()) {
            List<FileInfo> children = new ArrayList<>();
            List<Path> allDirectories = new ArrayList<>();

            try (Stream<Path> stream = Files.list(path)) {
                // 先收集所有目录
                allDirectories = stream.filter(p -> includeHidden || !isHidden(p)).filter(Files::isDirectory).sorted()
                        .collect(Collectors.toList());

                // 实现分页
                int totalEntries = allDirectories.size();
                int startIndex = pageIndex * pageSize;
                int endIndex = Math.min(startIndex + pageSize, totalEntries);

                if (startIndex < totalEntries) {
                    for (int i = startIndex; i < endIndex; i++) {
                        Path childPath = allDirectories.get(i);
                        // 创建目录节点，但不递归加载其子目录
                        FileInfo childInfo = createDirectoryNodeLazy(childPath, includeHidden);
                        if (childInfo != null) {
                            children.add(childInfo);
                        }
                    }
                }

                // 如果有更多数据，设置hasMore标志
                if (endIndex < totalEntries) {
                    fileInfo.setExtraProperties("{\"hasMore\": true, \"totalCount\": " + totalEntries + "}");
                }

            } catch (Exception e) {
                log.warn("构建懒加载文件树失败: path={}", path, e);
            }

            fileInfo.setChildren(children);
        }

        return fileInfo;
    }

    /**
     * 创建目录节点（懒加载模式） 只创建节点信息，不加载子目录
     */
    private FileInfo createDirectoryNodeLazy(Path path, boolean includeHidden) {
        FileInfo fileInfo = convertToFileInfo(path);
        if (fileInfo == null) {
            return null;
        }

        // 检查是否有子目录，用于前端显示展开图标
        boolean hasChildren = false;
        try (Stream<Path> stream = Files.list(path)) {
            hasChildren = stream.filter(p -> includeHidden || !isHidden(p)).filter(Files::isDirectory).findFirst()
                    .isPresent();
        } catch (Exception e) {
            log.debug("检查子目录失败: {}", path, e);
        }

        // 如果有子目录，设置一个空的children列表，让前端知道这个节点可以展开
        if (hasChildren) {
            fileInfo.setChildren(new ArrayList<>());
            fileInfo.setExtraProperties("{\"hasChildren\": true}");
        }

        return fileInfo;
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copyDirectoryRecursively(Path source, Path target, Boolean overwrite) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                StandardCopyOption[] options = overwrite != null && overwrite
                        ? new StandardCopyOption[] { StandardCopyOption.REPLACE_EXISTING }
                        : new StandardCopyOption[0];
                Files.copy(file, targetFile, options);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 构建系统根目录树（处理"/"路径请求）
     */
    private FileInfo buildSystemRootTree(int maxDepth, boolean includeHidden, Boolean lazyLoad, int pageSize,
            int pageIndex) throws IOException {
        String osName = System.getProperty("os.name").toLowerCase();
        log.info("检测到操作系统: {}", osName);

        if (osName.contains("win")) {
            // Windows系统：返回所有可用磁盘驱动器
            return buildWindowsRootTree(maxDepth, includeHidden, lazyLoad, pageSize, pageIndex);
        } else {
            // Linux/Unix系统：返回真正的根目录"/"
            return buildUnixRootTree(maxDepth, includeHidden, lazyLoad, pageSize, pageIndex);
        }
    }

    /**
     * 构建Windows系统根目录树（磁盘驱动器列表）
     */
    private FileInfo buildWindowsRootTree(int maxDepth, boolean includeHidden, Boolean lazyLoad, int pageSize,
            int pageIndex) throws IOException {
        log.info("构建Windows系统根目录树");

        // 创建虚拟根节点
        FileInfo rootNode = new FileInfo();
        rootNode.setName("我的电脑");
        rootNode.setPath("/");
        rootNode.setIsDirectory(true);
        rootNode.setSize(0L);
        rootNode.setCreateTime(LocalDateTime.now());
        rootNode.setLastModified(LocalDateTime.now());

        List<FileInfo> drives = new ArrayList<>();

        // 获取所有可用的文件系统根目录（磁盘驱动器）
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                try {
                    FileInfo driveInfo = new FileInfo();
                    String drivePath = root.getAbsolutePath();
                    String driveName = drivePath.replace("\\", "");

                    driveInfo.setName(driveName);
                    driveInfo.setPath(drivePath);
                    driveInfo.setIsDirectory(true);
                    driveInfo.setSize(0L);
                    driveInfo.setCreateTime(LocalDateTime.now());
                    driveInfo.setLastModified(LocalDateTime.now());

                    // 检查驱动器是否可访问
                    if (root.exists() && root.canRead()) {
                        // 检查是否有子目录，用于懒加载
                        boolean hasChildren = hasSubDirectories(root.toPath(), includeHidden);
                        if (hasChildren) {
                            driveInfo.setChildren(new ArrayList<>());
                            driveInfo.setExtraProperties("{\"hasChildren\": true}");
                        }

                        drives.add(driveInfo);
                        log.debug("添加可用驱动器: {}", driveName);
                    }
                } catch (Exception e) {
                    log.warn("处理驱动器失败: {}", root.getAbsolutePath(), e);
                }
            }
        }

        rootNode.setChildren(drives);
        return rootNode;
    }

    /**
     * 构建Unix/Linux系统根目录树
     */
    private FileInfo buildUnixRootTree(int maxDepth, boolean includeHidden, Boolean lazyLoad, int pageSize,
            int pageIndex) throws IOException {
        log.info("构建Unix/Linux系统根目录树");

        Path rootPath = Paths.get("/");
        if (Boolean.TRUE.equals(lazyLoad)) {
            return buildFileTreeLazy(rootPath, maxDepth, includeHidden, pageSize, pageIndex);
        } else {
            return buildFileTree(rootPath, maxDepth, includeHidden, 0);
        }
    }

    /**
     * 检查目录是否有子目录
     */
    private boolean hasSubDirectories(Path path, boolean includeHidden) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String entryName = entry.getFileName().toString();
                    if (includeHidden || !entryName.startsWith(".")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("检查子目录失败: {}", path, e);
        }
        return false;
    }
}

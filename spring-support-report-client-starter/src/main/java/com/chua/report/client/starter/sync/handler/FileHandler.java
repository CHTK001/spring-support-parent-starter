package com.chua.report.client.starter.sync.handler;

import com.chua.common.support.spi.Spi;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件操作处理器
 * <p>
 * 处理服务端发送的文件操作请求
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
@Slf4j
@Spi("fileHandler")
public class FileHandler implements SyncMessageHandler {

    @Override
    public String getName() {
        return "fileHandler";
    }

    @Override
    public boolean supports(String topic) {
        return MonitorTopics.FILE_REQUEST.equals(topic);
    }

    @Override
    public Object handle(String topic, String sessionId, Map<String, Object> data) {
        String operation = getString(data, "operation");
        String requestId = getString(data, "requestId");

        log.debug("[FileHandler] 收到文件操作请求: operation={}, requestId={}", operation, requestId);

        try {
            Object result = switch (operation) {
                case "list" -> handleList(data);
                case "tree" -> handleTree(data);
                case "info" -> handleInfo(data);
                case "read" -> handleRead(data);
                case "write" -> handleWrite(data);
                case "delete" -> handleDelete(data);
                case "mkdir" -> handleMkdir(data);
                case "rename" -> handleRename(data);
                case "copy" -> handleCopy(data);
                case "move" -> handleMove(data);
                case "exists" -> handleExists(data);
                case "diskUsage" -> handleDiskUsage(data);
                default -> Map.of("success", false, "message", "Unknown operation: " + operation);
            };

            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                resultMap.put("requestId", requestId);
                return resultMap;
            }
            return Map.of("success", true, "data", result, "requestId", requestId);

        } catch (Exception e) {
            log.error("[FileHandler] 处理文件操作失败: operation={}", operation, e);
            return Map.of("success", false, "message", e.getMessage(), "requestId", requestId);
        }
    }

    /**
     * 列出目录内容
     */
    private Object handleList(Map<String, Object> data) throws IOException {
        String path = getString(data, "path");
        boolean includeHidden = getBoolean(data, "includeHidden", false);

        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            return Map.of("success", false, "message", "Directory not found: " + path);
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return Map.of("success", true, "files", Collections.emptyList());
        }

        List<Map<String, Object>> fileList = Arrays.stream(files)
                .filter(f -> includeHidden || !f.isHidden())
                .map(this::fileToMap)
                .collect(Collectors.toList());

        return Map.of("success", true, "files", fileList, "path", path);
    }

    /**
     * 获取文件树
     */
    private Object handleTree(Map<String, Object> data) throws IOException {
        String path = getString(data, "path");
        int maxDepth = getInt(data, "maxDepth", 3);
        boolean includeHidden = getBoolean(data, "includeHidden", false);

        File root = new File(path);
        if (!root.exists()) {
            return Map.of("success", false, "message", "Path not found: " + path);
        }

        Map<String, Object> tree = buildFileTree(root, maxDepth, includeHidden, 0);
        return Map.of("success", true, "tree", tree, "path", path);
    }

    /**
     * 获取文件信息
     */
    private Object handleInfo(Map<String, Object> data) throws IOException {
        String path = getString(data, "path");
        File file = new File(path);

        if (!file.exists()) {
            return Map.of("success", false, "message", "File not found: " + path);
        }

        return Map.of("success", true, "file", fileToMap(file));
    }

    /**
     * 读取文件内容
     */
    private Object handleRead(Map<String, Object> data) throws IOException {
        String path = getString(data, "path");
        long offset = getLong(data, "offset", 0);
        int length = getInt(data, "length", -1);

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return Map.of("success", false, "message", "File not found: " + path);
        }

        byte[] content;
        if (length > 0) {
            content = new byte[length];
            try (var raf = new java.io.RandomAccessFile(file, "r")) {
                raf.seek(offset);
                int read = raf.read(content);
                if (read < length) {
                    content = Arrays.copyOf(content, read);
                }
            }
        } else {
            content = Files.readAllBytes(file.toPath());
        }

        return Map.of(
                "success", true,
                "content", Base64.getEncoder().encodeToString(content),
                "size", file.length(),
                "path", path
        );
    }

    /**
     * 写入文件内容
     */
    private Object handleWrite(Map<String, Object> data) throws IOException {
        String path = getString(data, "path");
        String contentBase64 = getString(data, "content");
        boolean append = getBoolean(data, "append", false);
        boolean overwrite = getBoolean(data, "overwrite", true);

        File file = new File(path);
        if (file.exists() && !overwrite && !append) {
            return Map.of("success", false, "message", "File already exists: " + path);
        }

        // 确保父目录存在
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        byte[] content = Base64.getDecoder().decode(contentBase64);
        if (append) {
            Files.write(file.toPath(), content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            Files.write(file.toPath(), content);
        }

        return Map.of("success", true, "path", path, "size", content.length);
    }

    /**
     * 删除文件或目录
     */
    private Object handleDelete(Map<String, Object> data) throws IOException {
        String path = getString(data, "path");
        boolean recursive = getBoolean(data, "recursive", false);

        File file = new File(path);
        if (!file.exists()) {
            return Map.of("success", false, "message", "File not found: " + path);
        }

        boolean deleted;
        if (file.isDirectory() && recursive) {
            deleted = deleteRecursively(file);
        } else {
            deleted = file.delete();
        }

        return Map.of("success", deleted, "path", path);
    }

    /**
     * 创建目录
     */
    private Object handleMkdir(Map<String, Object> data) throws IOException {
        String path = getString(data, "path");
        boolean recursive = getBoolean(data, "recursive", true);

        File dir = new File(path);
        boolean created = recursive ? dir.mkdirs() : dir.mkdir();

        return Map.of("success", created || dir.exists(), "path", path);
    }

    /**
     * 重命名文件
     */
    private Object handleRename(Map<String, Object> data) throws IOException {
        String oldPath = getString(data, "oldPath");
        String newName = getString(data, "newName");

        File oldFile = new File(oldPath);
        if (!oldFile.exists()) {
            return Map.of("success", false, "message", "File not found: " + oldPath);
        }

        File newFile = new File(oldFile.getParent(), newName);
        boolean renamed = oldFile.renameTo(newFile);

        return Map.of("success", renamed, "oldPath", oldPath, "newPath", newFile.getAbsolutePath());
    }

    /**
     * 复制文件
     */
    private Object handleCopy(Map<String, Object> data) throws IOException {
        String sourcePath = getString(data, "sourcePath");
        String targetPath = getString(data, "targetPath");
        boolean overwrite = getBoolean(data, "overwrite", false);

        File source = new File(sourcePath);
        File target = new File(targetPath);

        if (!source.exists()) {
            return Map.of("success", false, "message", "Source not found: " + sourcePath);
        }

        if (target.exists() && !overwrite) {
            return Map.of("success", false, "message", "Target already exists: " + targetPath);
        }

        if (source.isDirectory()) {
            copyDirectory(source, target);
        } else {
            Files.copy(source.toPath(), target.toPath(),
                    overwrite ? StandardCopyOption.REPLACE_EXISTING : StandardCopyOption.COPY_ATTRIBUTES);
        }

        return Map.of("success", true, "sourcePath", sourcePath, "targetPath", targetPath);
    }

    /**
     * 移动文件
     */
    private Object handleMove(Map<String, Object> data) throws IOException {
        String sourcePath = getString(data, "sourcePath");
        String targetPath = getString(data, "targetPath");
        boolean overwrite = getBoolean(data, "overwrite", false);

        File source = new File(sourcePath);
        File target = new File(targetPath);

        if (!source.exists()) {
            return Map.of("success", false, "message", "Source not found: " + sourcePath);
        }

        CopyOption[] options = overwrite ?
                new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} :
                new CopyOption[]{};
        Files.move(source.toPath(), target.toPath(), options);

        return Map.of("success", true, "sourcePath", sourcePath, "targetPath", targetPath);
    }

    /**
     * 检查文件是否存在
     */
    private Object handleExists(Map<String, Object> data) {
        String path = getString(data, "path");
        File file = new File(path);
        return Map.of("success", true, "exists", file.exists(), "path", path);
    }

    /**
     * 获取磁盘使用情况
     */
    private Object handleDiskUsage(Map<String, Object> data) {
        String path = getString(data, "path");
        File file = new File(path);

        if (!file.exists()) {
            return Map.of("success", false, "message", "Path not found: " + path);
        }

        return Map.of(
                "success", true,
                "totalSpace", file.getTotalSpace(),
                "freeSpace", file.getFreeSpace(),
                "usableSpace", file.getUsableSpace(),
                "path", path
        );
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> fileToMap(File file) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", file.getName());
        map.put("path", file.getAbsolutePath());
        map.put("isDirectory", file.isDirectory());
        map.put("isFile", file.isFile());
        map.put("size", file.length());
        map.put("lastModified", file.lastModified());
        map.put("hidden", file.isHidden());
        map.put("readable", file.canRead());
        map.put("writable", file.canWrite());
        map.put("executable", file.canExecute());
        return map;
    }

    private Map<String, Object> buildFileTree(File file, int maxDepth, boolean includeHidden, int currentDepth) {
        Map<String, Object> node = fileToMap(file);

        if (file.isDirectory() && currentDepth < maxDepth) {
            File[] children = file.listFiles();
            if (children != null) {
                List<Map<String, Object>> childNodes = Arrays.stream(children)
                        .filter(f -> includeHidden || !f.isHidden())
                        .sorted(Comparator.comparing(File::isFile).thenComparing(File::getName))
                        .map(f -> buildFileTree(f, maxDepth, includeHidden, currentDepth + 1))
                        .collect(Collectors.toList());
                node.put("children", childNodes);
            }
        }

        return node;
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }

    private void copyDirectory(File source, File target) throws IOException {
        if (!target.exists()) {
            target.mkdirs();
        }
        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                File destFile = new File(target, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, destFile);
                } else {
                    Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String getString(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v != null ? v.toString() : null;
    }

    private int getInt(Map<String, Object> data, String key, int defaultValue) {
        Object v = data.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v != null) {
            try {
                return Integer.parseInt(v.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private long getLong(Map<String, Object> data, String key, long defaultValue) {
        Object v = data.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v != null) {
            try {
                return Long.parseLong(v.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        Object v = data.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v != null) return Boolean.parseBoolean(v.toString());
        return defaultValue;
    }
}

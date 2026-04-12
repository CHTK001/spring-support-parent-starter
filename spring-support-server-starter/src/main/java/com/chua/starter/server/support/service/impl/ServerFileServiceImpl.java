package com.chua.starter.server.support.service.impl;

import com.chua.common.support.io.file.FileInfo;
import com.chua.common.support.network.protocol.client.FileClient;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerFileContent;
import com.chua.starter.server.support.model.ServerFileEntry;
import com.chua.starter.server.support.model.ServerFileOperationResult;
import com.chua.starter.server.support.service.ServerFileService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.spi.ServerHostProtocolManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerFileServiceImpl implements ServerFileService {

    private final ServerHostProtocolManager protocolManager = new ServerHostProtocolManager();
    private final ServerHostService serverHostService;

    @Override
    public List<ServerFileEntry> listFiles(Integer serverId, String path) throws Exception {
        ServerHost host = requireHost(serverId);
        if (isLocal(host)) {
            Path target = resolveLocalPath(host, path);
            if (!Files.exists(target)) {
                return List.of();
            }
            try (var stream = Files.list(target)) {
                return stream
                        .sorted(Comparator
                                .comparing((Path item) -> !Files.isDirectory(item))
                                .thenComparing(item -> item.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .map(this::toEntry)
                        .collect(Collectors.toList());
            }
        }
        FileClient client = createFileClient(host);
        try {
            client.connect();
            return client.listFiles(resolveRemotePath(host, path)).stream()
                    .map(this::toEntry)
                    .collect(Collectors.toList());
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public ServerFileContent readContent(Integer serverId, String path, Integer maxBytes) throws Exception {
        int limit = maxBytes == null || maxBytes <= 0 ? 262144 : maxBytes;
        ServerHost host = requireHost(serverId);
        String normalizedPath = isLocal(host)
                ? resolveLocalPath(host, path).toString()
                : resolveRemotePath(host, path);
        byte[] bytes = isLocal(host)
                ? readLocalBytes(resolveLocalPath(host, path), limit)
                : readRemoteBytes(host, normalizedPath, limit);
        return ServerFileContent.builder()
                .path(normalizedPath)
                .content(new String(bytes, StandardCharsets.UTF_8))
                .size(bytes.length)
                .truncated(isTruncated(host, normalizedPath, limit))
                .language(resolveLanguage(normalizedPath))
                .build();
    }

    @Override
    public byte[] download(Integer serverId, String path) throws Exception {
        ServerHost host = requireHost(serverId);
        if (isLocal(host)) {
            return Files.readAllBytes(resolveLocalPath(host, path));
        }
        return readRemoteBytes(host, resolveRemotePath(host, path), Integer.MAX_VALUE);
    }

    @Override
    public ServerFileOperationResult upload(Integer serverId, String directory, String fileName, InputStream inputStream) throws Exception {
        ServerHost host = requireHost(serverId);
        String sanitizedName = StringUtils.hasText(fileName) ? fileName.trim() : "upload.bin";
        if (isLocal(host)) {
            Path dir = resolveLocalPath(host, directory);
            Files.createDirectories(dir);
            Path target = dir.resolve(sanitizedName).normalize();
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return ServerFileOperationResult.builder()
                    .success(true)
                    .message("上传成功")
                    .path(target.toString())
                    .build();
        }
        FileClient client = createFileClient(host);
        try {
            client.connect();
            String targetPath = joinRemotePath(resolveRemotePath(host, directory), sanitizedName, isWindows(host));
            client.uploadFile(inputStream, targetPath);
            return ServerFileOperationResult.builder()
                    .success(true)
                    .message("上传成功")
                    .path(targetPath)
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public ServerFileOperationResult writeContent(Integer serverId, String path, String content) throws Exception {
        ServerHost host = requireHost(serverId);
        String value = content == null ? "" : content;
        if (isLocal(host)) {
            Path target = resolveLocalPath(host, path);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, value, StandardCharsets.UTF_8);
            return ServerFileOperationResult.builder()
                    .success(true)
                    .message("文件已更新")
                    .path(target.toString())
                    .build();
        }
        FileClient client = createFileClient(host);
        try {
            client.connect();
            String targetPath = resolveRemotePath(host, path);
            String parent = parentRemotePath(targetPath, isWindows(host));
            if (StringUtils.hasText(parent)) {
                client.createDirectory(parent, true);
            }
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))) {
                client.uploadFile(inputStream, targetPath);
            }
            return ServerFileOperationResult.builder()
                    .success(true)
                    .message("文件已更新")
                    .path(targetPath)
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public ServerFileOperationResult createDirectory(Integer serverId, String path) throws Exception {
        ServerHost host = requireHost(serverId);
        if (isLocal(host)) {
            Path target = resolveLocalPath(host, path);
            Files.createDirectories(target);
            return ServerFileOperationResult.builder()
                    .success(true)
                    .message("目录已创建")
                    .path(target.toString())
                    .build();
        }
        FileClient client = createFileClient(host);
        try {
            client.connect();
            String targetPath = resolveRemotePath(host, path);
            client.createDirectory(targetPath, true);
            return ServerFileOperationResult.builder()
                    .success(true)
                    .message("目录已创建")
                    .path(targetPath)
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public ServerFileOperationResult rename(Integer serverId, String path, String targetPath) throws Exception {
        ServerHost host = requireHost(serverId);
        if (isLocal(host)) {
            Path source = resolveLocalPath(host, path);
            Path target = resolveLocalPath(host, targetPath);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return ServerFileOperationResult.builder()
                    .success(true)
                    .message("重命名成功")
                    .path(source.toString())
                    .targetPath(target.toString())
                    .build();
        }
        FileClient client = createFileClient(host);
        try {
            client.connect();
            String source = resolveRemotePath(host, path);
            String target = resolveRemotePath(host, targetPath);
            client.rename(source, target);
            return ServerFileOperationResult.builder()
                    .success(true)
                    .message("重命名成功")
                    .path(source)
                    .targetPath(target)
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public ServerFileOperationResult delete(Integer serverId, String path, boolean recursive) throws Exception {
        ServerHost host = requireHost(serverId);
        if (isLocal(host)) {
            Path target = resolveLocalPath(host, path);
            if (Files.isDirectory(target)) {
                deleteLocalDirectory(target, recursive);
            } else {
                Files.deleteIfExists(target);
            }
            return ServerFileOperationResult.builder()
                    .success(true)
                    .message("删除成功")
                    .path(target.toString())
                    .build();
        }
        FileClient client = createFileClient(host);
        try {
            client.connect();
            String targetPath = resolveRemotePath(host, path);
            if (client.isDirectory(targetPath)) {
                client.deleteDirectory(targetPath, recursive);
            } else {
                client.deleteFile(targetPath);
            }
            return ServerFileOperationResult.builder()
                    .success(true)
                    .message("删除成功")
                    .path(targetPath)
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    private ServerHost requireHost(Integer serverId) {
        ServerHost host = serverHostService.getHost(serverId);
        if (host == null) {
            throw new IllegalStateException("服务器不存在");
        }
        return host;
    }

    private boolean isLocal(ServerHost host) {
        return protocolManager.isLocal(host);
    }

    private boolean isWindows(ServerHost host) {
        return protocolManager.isWindows(host);
    }

    private Path resolveLocalPath(ServerHost host, String requestedPath) {
        Path root = resolveLocalRoot(host);
        String normalized = StringUtils.hasText(requestedPath) ? requestedPath.trim() : root.toString();
        Path path = Paths.get(normalized);
        if (!path.isAbsolute()) {
            path = root.resolve(normalized);
        }
        Path resolved = path.normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalStateException("不允许访问基础目录之外的路径");
        }
        return resolved;
    }

    private String resolveRemotePath(ServerHost host, String requestedPath) {
        boolean windows = isWindows(host);
        String root = resolveRemoteRoot(host, windows);
        if (!StringUtils.hasText(requestedPath)) {
            return root;
        }
        String value = requestedPath.trim();
        String resolved = isAbsoluteRemotePath(value, windows)
                ? normalizeRemotePath(value, windows)
                : normalizeRemotePath(joinRemotePath(root, value, windows), windows);
        if (!isWithinRemoteRoot(root, resolved, windows)) {
            throw new IllegalStateException("不允许访问基础目录之外的路径");
        }
        return resolved;
    }

    private boolean isAbsoluteRemotePath(String value, boolean windows) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (windows) {
            return value.matches("^[A-Za-z]:.*") || value.startsWith("\\\\");
        }
        return value.startsWith("/");
    }

    private String joinRemotePath(String parent, String child, boolean windows) {
        String separator = windows ? "\\" : "/";
        String normalizedParent = parent.endsWith(separator) ? parent.substring(0, parent.length() - 1) : parent;
        String normalizedChild = child.startsWith(separator) ? child.substring(1) : child;
        return normalizedParent + separator + normalizedChild;
    }

    private String parentRemotePath(String path, boolean windows) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String separator = windows ? "\\" : "/";
        if (windows && path.matches("^[A-Za-z]:\\\\?[^\\\\/]*$")) {
            int index = path.lastIndexOf(separator);
            if (index <= 2) {
                return null;
            }
        }
        int index = path.lastIndexOf(separator);
        if (index <= 0) {
            return null;
        }
        return path.substring(0, index);
    }

    private Path resolveLocalRoot(ServerHost host) {
        String baseDirectory = StringUtils.hasText(host.getBaseDirectory())
                ? host.getBaseDirectory().trim()
                : System.getProperty("user.home");
        return Paths.get(baseDirectory).toAbsolutePath().normalize();
    }

    private String resolveRemoteRoot(ServerHost host, boolean windows) {
        String baseDirectory = StringUtils.hasText(host.getBaseDirectory())
                ? host.getBaseDirectory().trim()
                : protocolManager.resolveBaseDirectory(host);
        return normalizeRemotePath(baseDirectory, windows);
    }

    private String normalizeRemotePath(String path, boolean windows) {
        if (!StringUtils.hasText(path)) {
            return windows ? "C:\\" : "/";
        }
        String normalized = windows
                ? path.trim().replace('/', '\\')
                : path.trim().replace('\\', '/');
        normalized = windows
                ? normalized.replaceAll("\\\\{2,}", "\\\\")
                : normalized.replaceAll("/{2,}", "/");
        if (windows && normalized.matches("^[A-Za-z]:$")) {
            normalized = normalized + "\\";
        }
        if (!windows && !normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        String separator = windows ? "\\" : "/";
        while (normalized.length() > (windows ? 3 : 1) && normalized.endsWith(separator)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isWithinRemoteRoot(String root, String path, boolean windows) {
        String normalizedRoot = normalizeRemotePath(root, windows);
        String normalizedPath = normalizeRemotePath(path, windows);
        if (windows) {
            normalizedRoot = normalizedRoot.toLowerCase(Locale.ROOT);
            normalizedPath = normalizedPath.toLowerCase(Locale.ROOT);
        }
        if ("/".equals(normalizedRoot)) {
            return normalizedPath.startsWith("/");
        }
        if (windows && normalizedRoot.matches("^[a-z]:\\\\$")) {
            return normalizedPath.startsWith(normalizedRoot);
        }
        String separator = windows ? "\\" : "/";
        return normalizedPath.equals(normalizedRoot)
                || normalizedPath.startsWith(normalizedRoot + separator);
    }

    private FileClient createFileClient(ServerHost host) {
        FileClient client = protocolManager.createFileClient(host);
        if (client == null) {
            throw new IllegalStateException("当前协议不支持远程文件客户端: " + host.getServerType());
        }
        return client;
    }

    private byte[] readLocalBytes(Path path, int maxBytes) throws Exception {
        long size = Files.size(path);
        if (size <= maxBytes) {
            return Files.readAllBytes(path);
        }
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            byte[] bytes = new byte[maxBytes];
            file.seek(size - maxBytes);
            file.readFully(bytes);
            return bytes;
        }
    }

    private byte[] readRemoteBytes(ServerHost host, String path, int maxBytes) throws Exception {
        FileClient client = createFileClient(host);
        try {
            client.connect();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            client.downloadFile(path, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length <= maxBytes) {
                return bytes;
            }
            byte[] tail = new byte[maxBytes];
            System.arraycopy(bytes, bytes.length - maxBytes, tail, 0, maxBytes);
            return tail;
        } finally {
            client.closeQuietly();
        }
    }

    private boolean isTruncated(ServerHost host, String path, int maxBytes) throws Exception {
        if (isLocal(host)) {
            return Files.size(Paths.get(path)) > maxBytes;
        }
        return download(host.getServerId(), path).length > maxBytes;
    }

    private ServerFileEntry toEntry(Path path) {
        try {
            boolean directory = Files.isDirectory(path);
            return ServerFileEntry.builder()
                    .name(path.getFileName() == null ? path.toString() : path.getFileName().toString())
                    .path(path.toString())
                    .directory(directory)
                    .file(!directory)
                    .hidden(path.getFileName() != null && path.getFileName().toString().startsWith("."))
                    .size(directory ? 0L : Files.size(path))
                    .lastModified(Files.getLastModifiedTime(path).toMillis())
                    .extension(resolveExtension(path.getFileName() == null ? path.toString() : path.getFileName().toString()))
                    .build();
        } catch (Exception e) {
            return ServerFileEntry.builder()
                    .name(path.getFileName() == null ? path.toString() : path.getFileName().toString())
                    .path(path.toString())
                    .directory(Files.isDirectory(path))
                    .file(!Files.isDirectory(path))
                    .hidden(false)
                    .size(0L)
                    .lastModified(0L)
                    .extension(resolveExtension(path.toString()))
                    .build();
        }
    }

    private ServerFileEntry toEntry(FileInfo info) {
        return ServerFileEntry.builder()
                .name(info.getName())
                .path(info.getPath())
                .directory(info.isDirectory())
                .file(info.isFile() || !info.isDirectory())
                .hidden(info.isHidden())
                .size(info.getSize())
                .lastModified(info.getLastModified())
                .extension(resolveExtension(info.getName()))
                .build();
    }

    private String resolveLanguage(String path) {
        String extension = resolveExtension(path);
        return switch (extension) {
            case "json" -> "json";
            case "xml" -> "xml";
            case "yaml", "yml" -> "yaml";
            case "properties" -> "properties";
            case "sh" -> "shell";
            case "bat" -> "bat";
            case "ps1" -> "powershell";
            case "sql" -> "sql";
            case "java" -> "java";
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "vue" -> "vue";
            case "html" -> "html";
            case "css", "scss" -> "css";
            default -> "text";
        };
    }

    private String resolveExtension(String name) {
        if (!StringUtils.hasText(name) || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteLocalDirectory(Path path, boolean recursive) throws Exception {
        if (!recursive) {
            Files.deleteIfExists(path);
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}

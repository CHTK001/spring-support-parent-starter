package com.chua.starter.server.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.model.TempFile;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.SftpService;
import com.chua.ssh.support.client.SftpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* SFTP 文件管理服务实现
* <p>
* 每次操作按需建立 SFTP 连接（尝试-with-resources 保证关闭），
* 内容读取设置 1MB 截断上限以保护网关内存。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SftpServiceImpl implements SftpService {

    /** 文本内容读取上限（1MB），超出截断 */
    private static final int CONTENT_LIMIT_BYTES = 1024 * 1024;

    /** 连接超时（秒） */
    private static final int CONNECT_TIMEOUT_SECONDS = 8;

    /** 主机服务（凭据解析） */
    private final ServerHostService serverHostService;

    @Override
    public ReturnResult<List<Map<String, Object>>> list(Long hostId, Long identityId, String path) {
        ReturnResult<SshCredential> resolved = resolve(hostId, identityId);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        var host = requireHost(hostId);
        /**
        * 尝试。
        * @param openSftpClient(host.getServerHostAddress( 打开sftpclient(主机.获取服务端主机地址(
        */
        try (var client = openSftpClient(host.getServerHostAddress(), host.getServerHostPort(), resolved.getData())) {
            var entries = client.ls().path(normalizeDir(path)).exec();
 // 归一化为前端契约: 名称/路径/目录/文件/大小
            List<Map<String, Object>> normalized = new ArrayList<>(entries.size());
            for (Map<String, Object> entry : entries) {
                Map<String, Object> row = new LinkedHashMap<>();
                String name = String.valueOf(entry.get("name"));
                row.put("name", name);
                boolean isDir = isDirectory(entry);
                String parent = normalizeDir(path);
                row.put("path", parent.endsWith("/") ? parent + name : parent + "/" + name);
                row.put("directory", isDir);
                row.put("file", !isDir);
                Object attrs = entry.get("attributes");
                if (attrs instanceof org.apache.sshd.sftp.client.SftpClient.Attributes a) {
                    row.put("size", a.getSize());
                }
                normalized.add(row);
            }
            return ReturnResult.list(normalized);
        } catch (Exception e) {
            log.warn("[sftp] 目录浏览失败 hostId={} path={}: {}", hostId, path, e.getMessage());
            return ReturnResult.error("目录浏览失败: " + e.getMessage());
        }
    }

    /**
    * 从目录条目中判断是否目录（兼容 attributes 对象或 映射 两种形态）
    *
    * @param entry 目录条目
    * @return 是否目录
    */
    private boolean isDirectory(Map<String, Object> entry) {
        Object attrs = entry.get("attributes");
        if (attrs instanceof org.apache.sshd.sftp.client.SftpClient.Attributes a) {
            return a.isDirectory();
        }
        if (attrs instanceof Map<?, ?> m) {
            return Boolean.TRUE.equals(m.get("directory"));
        }
        return false;
    }

    @Override
    public ReturnResult<Map<String, Object>> readContent(Long hostId, Long identityId, String path) {
        if (path == null || path.isBlank()) {
            return ReturnResult.error("文件路径不能为空");
        }
        ReturnResult<SshCredential> resolved = resolve(hostId, identityId);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        var host = requireHost(hostId);
        /**
        * 尝试。
        * @param openSftpClient(host.getServerHostAddress( 打开sftpclient(主机.获取服务端主机地址(
        */
        try (var client = openSftpClient(host.getServerHostAddress(), host.getServerHostPort(), resolved.getData());
             TempFile temp = downloadToTemp(client, path)) {
            long size = Files.size(temp.path());
            boolean truncated = size > CONTENT_LIMIT_BYTES;
            // 限制读取长度，避免超大文件拖垮内存
            byte[] bytes = Files.readAllBytes(truncated
                    ? sliceFile(temp.path(), CONTENT_LIMIT_BYTES) : temp.path());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            result.put("content", new String(bytes, StandardCharsets.UTF_8));
            result.put("size", size);
            result.put("truncated", truncated);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.warn("[sftp] 读取内容失败 hostId={} path={}: {}", hostId, path, e.getMessage());
            return ReturnResult.error("读取失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Void> writeContent(Long hostId, Long identityId, String path, String content) {
        if (path == null || path.isBlank()) {
            return ReturnResult.error("文件路径不能为空");
        }
        ReturnResult<SshCredential> resolved = resolve(hostId, identityId);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        var host = requireHost(hostId);
        Path localTemp = null;
        /**
        * 尝试。
        * @param openSftpClient(host.getServerHostAddress( 打开sftpclient(主机.获取服务端主机地址(
        */
        try (var client = openSftpClient(host.getServerHostAddress(), host.getServerHostPort(), resolved.getData())) {
            localTemp = Files.createTempFile("ms-sftp-write-", ".tmp");
            Files.writeString(localTemp, content == null ? "" : content, StandardCharsets.UTF_8);
            client.upload().local(localTemp.toString()).remote(path).exec();
            return ReturnResult.success();
        } catch (Exception e) {
            log.warn("[sftp] 写入内容失败 hostId={} path={}: {}", hostId, path, e.getMessage());
            return ReturnResult.error("写入失败: " + e.getMessage());
        } finally {
            /**
            * cleanupquietly。
            * @param localTemp 本地temp
            */
            cleanupQuietly(localTemp);
        }
    }

    @Override
    public ReturnResult<String> upload(Long hostId, Long identityId, String directory, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        String dir = normalizeDir(directory);
        ReturnResult<SshCredential> resolved = resolve(hostId, identityId);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        var host = requireHost(hostId);
        String remotePath = dir.endsWith("/") ? dir + sanitizeFileName(file.getOriginalFilename())
                : dir + "/" + sanitizeFileName(file.getOriginalFilename());
        Path localTemp = null;
        /**
        * 尝试。
        * @param openSftpClient(host.getServerHostAddress( 打开sftpclient(主机.获取服务端主机地址(
        */
        try (var client = openSftpClient(host.getServerHostAddress(), host.getServerHostPort(), resolved.getData())) {
            localTemp = Files.createTempFile("ms-sftp-upload-", ".tmp");
            file.transferTo(localTemp.toFile());
            client.upload().local(localTemp.toString()).remote(remotePath).exec();
            return ReturnResult.ok(remotePath, "上传成功");
        } catch (Exception e) {
            log.warn("[sftp] 上传失败 hostId={} dir={}: {}", hostId, directory, e.getMessage());
            return ReturnResult.error("上传失败: " + e.getMessage());
        } finally {
            /**
            * cleanupquietly。
            * @param localTemp 本地temp
            */
            cleanupQuietly(localTemp);
        }
    }

    @Override
    public ReturnResult<TempFile> download(Long hostId, Long identityId, String path) {
        if (path == null || path.isBlank()) {
            return ReturnResult.error("文件路径不能为空");
        }
        ReturnResult<SshCredential> resolved = resolve(hostId, identityId);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        var host = requireHost(hostId);
        /**
        * 尝试。
        * @param openSftpClient(host.getServerHostAddress( 打开sftpclient(主机.获取服务端主机地址(
        */
        try (var client = openSftpClient(host.getServerHostAddress(), host.getServerHostPort(), resolved.getData())) {
            TempFile temp = downloadToTemp(client, path);
            return ReturnResult.ok(temp);
        } catch (Exception e) {
            log.warn("[sftp] 下载失败 hostId={} path={}: {}", hostId, path, e.getMessage());
            return ReturnResult.error("下载失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Void> mkdir(Long hostId, Long identityId, String path) {
        if (path == null || path.isBlank()) {
            return ReturnResult.error("目录路径不能为空");
        }
        ReturnResult<SshCredential> resolved = resolve(hostId, identityId);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        var host = requireHost(hostId);
        /**
        * 尝试。
        * @param openSftpClient(host.getServerHostAddress( 打开sftpclient(主机.获取服务端主机地址(
        */
        try (var client = openSftpClient(host.getServerHostAddress(), host.getServerHostPort(), resolved.getData())) {
            client.mkdir().path(path).recursive(true).exec();
            return ReturnResult.success();
        } catch (Exception e) {
            log.warn("[sftp] 创建目录失败 hostId={} path={}: {}", hostId, path, e.getMessage());
            return ReturnResult.error("创建目录失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Void> delete(Long hostId, Long identityId, String path, boolean recursive) {
        if (path == null || path.isBlank() || "/".equals(path.trim())) {
            return ReturnResult.error("非法删除目标");
        }
        ReturnResult<SshCredential> resolved = resolve(hostId, identityId);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        var host = requireHost(hostId);
        /**
        * 尝试。
        * @param openSftpClient(host.getServerHostAddress( 打开sftpclient(主机.获取服务端主机地址(
        */
        try (var client = openSftpClient(host.getServerHostAddress(), host.getServerHostPort(), resolved.getData())) {
            client.rm().path(path).recursive(recursive).exec();
            return ReturnResult.success();
        } catch (Exception e) {
            log.warn("[sftp] 删除失败 hostId={} path={}: {}", hostId, path, e.getMessage());
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Void> rename(Long hostId, Long identityId, String fromPath, String toPath) {
        if (isBlank(fromPath) || isBlank(toPath)) {
            return ReturnResult.error("原路径与新路径不能为空");
        }
        ReturnResult<SshCredential> resolved = resolve(hostId, identityId);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        var host = requireHost(hostId);
        /**
        * 尝试。
        * @param openSftpClient(host.getServerHostAddress( 打开sftpclient(主机.获取服务端主机地址(
        */
        try (var client = openSftpClient(host.getServerHostAddress(), host.getServerHostPort(), resolved.getData())) {
            client.rename().from(fromPath).to(toPath).exec();
            return ReturnResult.success();
        } catch (Exception e) {
            log.warn("[sftp] 重命名失败 hostId={} from={} to={}: {}", hostId, fromPath, toPath, e.getMessage());
            return ReturnResult.error("重命名失败: " + e.getMessage());
        }
    }

    // ==================== 内部工具 ====================

    /**
    * 构建并连接 SFTP 客户端
    *
    * @param address    主机地址
    * @param port       端口
    * @param credential 凭据
    * @return 已连接客户端
    */
    private SftpClient openSftpClient(String address, Integer port, SshCredential credential) {
        var builder = SftpClient.builder()
                .host(address)
                .port(port == null || port <= 0 ? 22 : port)
                .username(credential.username())
                .connectTimeout(CONNECT_TIMEOUT_SECONDS);
        java.nio.file.Path tempKey = null;
        try {
            if (credential.hasKey()) {
                // 底层库按路径加载私钥: 先落盘, 认证完成后再删除
                tempKey = java.nio.file.Files.createTempFile("ms-server-key-", ".pem");
                java.nio.file.Files.writeString(tempKey, credential.privateKey());
                builder.privateKey(tempKey.toString());
                if (notBlank(credential.password())) {
                    builder.password(credential.password());
                }
            } else {
                builder.password(credential.password() == null ? "" : credential.password());
            }
            SftpClient client = builder.build();
            client.connect();
            // 认证已完成, 私钥明文即刻从磁盘清除
            /**
            * cleanupquietly。
            * @param tempKey temp钥匙
            */
            cleanupQuietly(tempKey);
            return client;
        } catch (IOException e) {
            /**
            * cleanupquietly。
            * @param tempKey temp钥匙
            */
            cleanupQuietly(tempKey);
            /**
            * illegal状态异常。
            * @param "写入私钥临时文件失败" "写入私钥临时文件失败"
            * @param e e
            * @return illegal状态异常的结果
            */
            throw new IllegalStateException("写入私钥临时文件失败", e);
        } catch (RuntimeException e) {
            // 连接/认证失败同样清理临时私钥
            /**
            * cleanupquietly。
            * @param tempKey temp钥匙
            */
            cleanupQuietly(tempKey);
            throw e;
        }
    }

    /**
    * 下载远端文件到受管临时文件
    *
    * @param client SFTP 客户端（已连接）
    * @param path   远端路径
    * @return 临时文件句柄
    * @throws IOException 临时文件创建或下载失败时抛出
    */
    private TempFile downloadToTemp(SftpClient client, String path) throws IOException {
        Path temp = Files.createTempFile("ms-sftp-download-", ".tmp");
        try {
            client.download().remote(path).local(temp.toString()).exec();
        } catch (Exception e) {
            Files.deleteIfExists(temp);
            /**
            * io异常。
            * @param e.getMessage( e.获取消息(
            * @return io异常的结果
            */
            throw new IOException("远端下载失败: " + e.getMessage(), e);
        }
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        /**
        * temp文件。
        * @param temp temp
        * @param fileName 文件名称
        * @return temp文件的结果
        */
        return new TempFile(temp, fileName);
    }

    /**
    * 截取大文件前 N 字节为独立临时文件
    *
    * @param source 源文件
    * @param limit  字节上限
    * @return 新临时文件路径
    * @throws IOException 读取失败时抛出
    */
    private Path sliceFile(Path source, int limit) throws IOException {
        Path target = Files.createTempFile("ms-sftp-slice-", ".tmp");
        /**
        * 尝试。
        * @param Files.newInputStream(source 文件.新输入流(源
        */
        try (var in = Files.newInputStream(source)) {
 // 仅读取前 限制 字节，避免超大文件全量进入内存
            byte[] head = in.readNBytes(limit);
            Files.write(target, head);
        }
        return target;
    }

    /**
    * 解析主机与凭据
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识，可为空
    * @return 解析结果
    */
    private ReturnResult<SshCredential> resolve(Long hostId, Long identityId) {
        return serverHostService.resolveCredential(hostId, identityId);
    }

    /**
    * 加载主机实体，不存在时抛出业务异常
    *
    * @param hostId 主机 标识
    * @return 主机实体
    */
    private com.chua.starter.server.support.entity.MsServerHost requireHost(Long hostId) {
        var result = serverHostService.get(hostId);
        if (!result.isOk()) {
            /**
            * illegal参数异常。
            * @param result.getMsg( 结果.获取msg(
            * @return illegal参数异常的结果
            */
            throw new IllegalArgumentException(result.getMsg());
        }
        return result.getData();
    }

    /**
    * 规整目录路径：空值回退根目录，去除尾部斜杠歧义
    *
    * @param path 原始路径
    * @return 规整后路径
    */
    private String normalizeDir(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path;
    }

    /**
    * 清洗文件名：剥离路径分隔符防止越权写入
    *
    * @param original 原始文件名
    * @return 安全文件名
    */
    private String sanitizeFileName(String original) {
        String name = original == null ? "file" : original;
        name = name.replace("\\", "_").replace("/", "_");
        return name.isEmpty() ? "file" : name;
    }

    /**
    * 静默清理本地临时文件
    *
    * @param path 文件路径，可为空
    */
    private void cleanupQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }

    /**
    * 非空判断
    *
    * @param value 待判断字符串
    * @return 是否非空
    */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
    * 空串判断
    *
    * @param value 待判断字符串
    * @return 是否为空
    */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

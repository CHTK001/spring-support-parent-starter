package com.chua.starter.server.support.spi.client;

import com.chua.common.support.network.protocol.client.FileClient;
import com.chua.ssh.support.client.SftpClient;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 {@link SftpClient} 的 {@link FileClient} 适配器。
 *
 * @author CH
 * @since 2026/7/30
 */
@RequiredArgsConstructor
public class SftpFileClient implements FileClient {

    private final SftpClient delegate;

    public static SftpFileClient of(SftpClient delegate) {
        return new SftpFileClient(delegate);
    }

    @Override
    public void connect() throws IOException {
        delegate.connect();
    }

    @Override
    public void closeQuietly() {
        try {
            close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public List<String> listFiles(String path) {
        return delegate.ls().path(path).exec().stream()
                .map(item -> (String) item.getOrDefault("name", item.getOrDefault("path", "")))
                .toList();
    }

    @Override
    public void uploadFile(InputStream inputStream, String path) {
        java.io.File temp = createTempFile(inputStream);
        delegate.upload().local(temp.getAbsolutePath()).remote(path).exec();
        temp.delete();
    }

    @Override
    public void downloadFile(String path, OutputStream outputStream) throws IOException {
        String content = readFile(path);
        try {
            outputStream.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public String readFile(String path) throws IOException {
        java.io.File temp = java.io.File.createTempFile("sftp-read-", ".tmp");
        try {
            delegate.download().remote(path).local(temp.getAbsolutePath()).exec();
            return java.nio.file.Files.readString(temp.toPath());
        } catch (Exception e) {
            throw new IOException("读取文件失败: " + path, e);
        } finally {
            temp.delete();
        }
    }

    @Override
    public void createDirectory(String path, boolean recursive) {
        delegate.mkdir().path(path).recursive(recursive).exec();
    }

    @Override
    public void delete(String path) {
        delegate.rm().path(path).exec();
    }

    @Override
    public void rename(String oldPath, String newPath) {
        delegate.rename().from(oldPath).to(newPath).exec();
    }

    @Override
    public boolean exists(String path) {
        try {
            var stat = delegate.stat().path(path).exec();
            return stat != null && !stat.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isDirectory(String path) {
        try {
            var stat = delegate.stat().path(path).exec();
            return stat != null && Boolean.TRUE.equals(stat.get("isDirectory"));
        } catch (Exception e) {
            return false;
        }
    }

    public void close() {
        delegate.close();
    }

    private static java.io.File createTempFile(InputStream inputStream) {
        try {
            java.io.File temp = java.io.File.createTempFile("sftp-upload-", ".tmp");
            try (var out = new java.io.FileOutputStream(temp)) {
                inputStream.transferTo(out);
            }
            return temp;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

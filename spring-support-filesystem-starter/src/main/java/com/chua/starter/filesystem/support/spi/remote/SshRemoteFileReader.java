package com.chua.starter.filesystem.support.spi.remote;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.ssh.support.client.HostKeyPolicy;
import com.chua.ssh.support.client.SftpClient;
import com.chua.starter.filesystem.support.spi.FileStorageRemoteReader;
import com.chua.starter.filesystem.support.spi.RemoteFileRequest;
import com.chua.starter.filesystem.support.spi.RemoteFileResult;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SSH 远程文件读取实现。
 *
 * <p><b>安全模型：每次请求新建连接，用完即关</b></p>
 * <ul>
 *   <li>不池化连接：远程文件预览是用户触发的低频操作，池化收益远小于
 *       长连接带来的凭证泄露风险；</li>
 *   <li>MINA SSHD 的临时密钥交换：每次 SSH 会话生成新的 ephemeral key pair
 *       （Diffie-Hellman KEX），加密参数每次不同——满足「随机加密，每次不同」；</li>
 *   <li>连接在读取完成后立即 {@code close()}，凭证不落盘、不缓存；</li>
 *   <li>私钥内容写入临时文件后立即删除，不残留明文。</li>
 * </ul>
 *
 * <p>通过 {@code META-INF/extensions/} 注册为 SPI 扩展，
 * 文件服务过滤器在 {@code openRemoteFile=true} 时按 {@code ssh://} 前缀路由。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Spi("remote-reader-ssh")
public class SshRemoteFileReader implements FileStorageRemoteReader {

    /**
     * SSH 连接超时（秒）
     */
    private static final int CONNECT_TIMEOUT_SECONDS = 15;

    /**
     * SSH 认证超时（秒）
     */
    private static final int AUTH_TIMEOUT_SECONDS = 30;

    /**
     * SFTP 读取缓冲区大小
     */
    private static final int READ_BUFFER_SIZE = 8192;

    @Override
    public String getName() {
        return "SSH";
    }

    @Override
    public String getLabel() {
        return "SSH 远程";
    }

    @Override
    public String getDescription() {
        return "通过 SSH/SFTP 读取远程主机文件，每次请求新建连接，用完即关";
    }

    @Override
    public String protocol() {
        return "ssh://";
    }

    @Override
    public boolean supportsRemoteRead() {
        return true;
    }

    @Override
    public RemoteFileResult readRemote(RemoteFileRequest request) throws Exception {
        String host = request.host();
        int port = request.port() > 0 ? request.port() : 22;
        String username = request.username();
        String path = request.path();
        String fileName = StringUtils.isNotBlank(request.fileName())
                ? request.fileName()
                : path.substring(path.lastIndexOf('/') + 1);

        Path tempKey = null;
        try {
            SftpClient.Builder builder = SftpClient.builder()
                    .host(host)
                    .port(port)
                    .username(username)
                    .hostKeyPolicy(HostKeyPolicy.INSECURE)
                    .connectTimeout(CONNECT_TIMEOUT_SECONDS);

            if (StringUtils.isNotBlank(request.password())) {
                builder.password(request.password());
            } else if (StringUtils.isNotBlank(request.privateKey())) {
                // 私钥内容写入临时文件（SftpClient.Builder 只接受路径）
                tempKey = Files.createTempFile("fs-ssh-", ".pem");
                Files.writeString(tempKey, request.privateKey());
                builder.privateKey(tempKey.toString());
            } else {
                throw new IllegalArgumentException("SSH 读取需要密码或私钥");
            }

            SftpClient client = builder.build().connect();
            try {
                byte[] data = readFile(client, path);
                String contentType = URLConnection.guessContentTypeFromName(fileName);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                return new RemoteFileResult(fileName, contentType, data);
            } finally {
                client.close();
            }
        } finally {
            if (tempKey != null) {
                try {
                    Files.deleteIfExists(tempKey);
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
        }
    }

    /**
     * 通过 SFTP 读取远程文件到内存。
     *
     * @param client 已连接的 SFTP 客户端
     * @param path   远端文件绝对路径
     * @return 文件字节内容
     * @throws Exception 读取失败
     */
    private byte[] readFile(SftpClient client, String path) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[READ_BUFFER_SIZE];
        try (var handle = client.getSftp().open(path,
                org.apache.sshd.sftp.client.SftpClient.OpenMode.Read)) {
            int len;
            while ((len = client.getSftp().read(handle, baos.size(), buf)) > 0) {
                baos.write(buf, 0, len);
            }
        }
        return baos.toByteArray();
    }
}

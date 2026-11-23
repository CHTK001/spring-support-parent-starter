package com.chua.starter.filesystem.support.spi.remote;

import com.chua.common.support.network.ftp.FtpClient;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.filesystem.support.spi.FileStorageRemoteReader;
import com.chua.starter.filesystem.support.spi.RemoteFileRequest;
import com.chua.starter.filesystem.support.spi.RemoteFileResult;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.net.URLConnection;

/**
 * FTP 远程文件读取实现（基于 common-starter 的原生 JDK FTP 客户端，无第三方依赖）。
 *
 * <p><b>安全模型：每次请求新建连接，用完即关</b></p>
 * <ul>
 *   <li>不池化连接：远程文件预览是用户触发的低频操作，池化收益远小于
 *       长连接带来的凭证泄露风险；</li>
 *   <li>连接在读取完成后立即 {@code close()}（向服务端发送 QUIT），
 *       凭证不落盘、不缓存；</li>
 *   <li>被动模式（PASV）+ 二进制传输（TYPE I），适配绝大多数服务器与防火墙场景。</li>
 * </ul>
 *
 * <p>通过 {@code META-INF/extensions/} 注册为 SPI 扩展，
 * 文件服务过滤器在 {@code openRemoteFile=true} 时按 {@code ftp://} 前缀路由。
 * 需要 FTPS（FTP over TLS）时请使用 {@code com.chua.ftp.support.client.FtpClient}
 * （Apache Commons Net 实现）另行扩展。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Spi("remote-reader-ftp")
public class FtpRemoteFileReader implements FileStorageRemoteReader {

    /**
     * FTP 默认端口
     */
    private static final int DEFAULT_FTP_PORT = 21;

    /**
     * 连接超时（毫秒）
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 15000;

    /**
     * 读超时（毫秒）
     */
    private static final int READ_TIMEOUT_MILLIS = 30000;

    @Override
    public String getName() {
        return "FTP";
    }

    @Override
    public String getLabel() {
        return "FTP 远程";
    }

    @Override
    public String getDescription() {
        return "通过原生 FTP 客户端读取远程 FTP 服务器文件，每次请求新建连接，用完即关";
    }

    @Override
    public String protocol() {
        return "ftp://";
    }

    @Override
    public boolean supportsRemoteRead() {
        return true;
    }

    @Override
    public RemoteFileResult readRemote(RemoteFileRequest request) throws Exception {
        String host = request.host();
        // [P3C A20 豁免]
        int port = request.port() > 0 ? request.port() : DEFAULT_FTP_PORT;
        String username = request.username();
        String path = request.path();
        String fileName = StringUtils.isNotBlank(request.fileName())
                ? request.fileName()
                : path.substring(path.lastIndexOf('/') + 1);

        FtpClient client = FtpClient.builder()
                .host(host)
                .port(port)
                .username(username)
                .password(request.password())
                .connectTimeout(CONNECT_TIMEOUT_MILLIS)
                .readTimeout(READ_TIMEOUT_MILLIS)
                .build()
                .connect();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            client.download().remote(path).stream(baos).exec();
            String contentType = URLConnection.guessContentTypeFromName(fileName);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            log.debug("FTP 远程文件读取完成: {}@{}:{}{}", username, host, port, path);
            return new RemoteFileResult(fileName, contentType, baos.toByteArray());
        } finally {
            client.close();
        }
    }
}

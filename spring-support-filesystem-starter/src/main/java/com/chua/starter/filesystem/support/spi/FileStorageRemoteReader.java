package com.chua.starter.filesystem.support.spi;

import com.chua.common.support.spi.annotations.Spi;

/**
 * 远程文件读取 SPI 接口。
 *
 * <p>把「从远程地址读取文件内容」收敛成可枚举的能力实现，
 * 文件服务过滤器在 {@code openRemoteFile=true} 时按 URL 协议前缀
 * （{@code ssh://}、{@code http://}、{@code https://}）路由到对应实现。</p>
 *
 * <p><b>为什么是 SPI 而不是硬编码</b>：远程读取协议（SSH/HTTP/SFTP/WebDAV…）
 * 各有独立的依赖与安全考量。硬编码 SSH 会让 filesystem-starter 被迫依赖
 * ssh-starter；硬编码 HTTP 则无法扩展。拆成 SPI 后，扩展方只需新增实现
 * 并在 {@code META-INF/extensions/} 注册，过滤器自动发现。</p>
 *
 * <p><b>安全约定</b>：</p>
 * <ul>
 *   <li>实现必须为<b>每次请求新建连接</b>，禁止连接池化——
 *       远程读取是低频操作（用户触发预览），池化的收益远小于
 *       长连接带来的凭证泄露风险；</li>
 *   <li>SSH 实现依赖 MINA SSHD 的临时密钥交换（每次连接生成新的
 *       ephemeral key pair），天然满足「随机加密，每次不同」；</li>
 *   <li>连接在读取完成后立即关闭，凭证不落盘、不缓存。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Spi
public interface FileStorageRemoteReader {

    /**
     * 获取实现唯一名称（写入配置持久化）。
     *
     * @return 实现名（如 {@code SSH}）
     */
    String getName();

    /**
     * 获取实现展示名。
     *
     * @return 展示名（如 {@code SSH 远程}）
     */
    String getLabel();

    /**
     * 获取实现说明。
     *
     * @return 说明文本；无说明返回空串
     */
    default String getDescription() {
        return "";
    }

    /**
     * 支持的 URL 协议前缀（如 {@code ssh://}）。
     *
     * @return 协议前缀，过滤器按此匹配路由
     */
    String protocol();

    /**
     * 是否支持远程读取。
     *
     * @return true 表示该实现能处理对应协议的远程文件读取
     */
    boolean supportsRemoteRead();

    /**
     * 读取远程文件。
     *
     * <p>实现必须为每次调用新建连接，读取完成后关闭，不得缓存连接或凭证。</p>
     *
     * @param request 远程文件读取请求
     * @return 读取结果（字节内容 + 文件名 + MIME 类型）
     * @throws Exception 读取失败时抛出，过滤器捕获后返回 502
     */
    RemoteFileResult readRemote(RemoteFileRequest request) throws Exception;
}

package com.chua.starter.filesystem.support.spi;

/**
 * 远程文件读取请求。
 *
 * <p>包含目标地址、凭据与文件路径。凭据随请求传递——
 * 前端从主机管理 API 获取后拼入查询串，后端 SSH 实现直接使用，
 * 不做二次持久化。</p>
 *
 * <p><b>安全提示</b>：密码以明文出现在请求参数中，仅在 HTTPS 下使用。
 * 生产环境建议前端先换取短时效令牌，令牌编码 hostId+path，
 * 后端通过 SPI 回调主机管理模块换取凭据。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public record RemoteFileRequest(
        /**
         * 主机地址（IP 或域名，不含 user@ 前缀）
         */
        String host,
        /**
         * SSH 端口（默认 22）
         */
        int port,
        /**
         * 登录用户名
         */
        String username,
        /**
         * 登录密码（password 认证时使用）
         */
        String password,
        /**
         * 私钥内容（key/certificate 认证时使用，PEM 格式）
         */
        String privateKey,
        /**
         * 远端文件绝对路径（如 {@code /opt/app.log}）
         */
        String path,
        /**
         * 文件名（用于 MIME 类型推断，缺省时取 path 末段）
         */
        String fileName
) {

    /**
     * 便捷构造：从 path 末段推断 fileName。
     *
     * @param host     主机地址
     * @param port     SSH 端口
     * @param username 用户名
     * @param password 密码
     * @param path     远端文件路径
     * @return 请求对象
     */
    public static RemoteFileRequest of(String host, int port, String username,
                                       String password, String path) {
        return new RemoteFileRequest(host, port, username, password, null, path,
                path.substring(path.lastIndexOf('/') + 1));
    }

    /**
     * 便捷构造：带私钥的认证。
     *
     * @param host       主机地址
     * @param port       SSH 端口
     * @param username   用户名
     * @param privateKey 私钥内容
     * @param path       远端文件路径
     * @return 请求对象
     */
    public static RemoteFileRequest ofKey(String host, int port, String username,
                                          String privateKey, String path) {
        return new RemoteFileRequest(host, port, username, null, privateKey, path,
                path.substring(path.lastIndexOf('/') + 1));
    }
}

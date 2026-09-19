package com.chua.starter.server.support.model;

/**
 * SSH 连接凭据（解密后的运行时对象，禁止落库与日志输出）
 *
 * @param username   登录用户名
 * @param password   登录密码或私钥口令，可为空
 * @param privateKey 私钥内容，可为空
 * @author CH
 * @since 4.0.0.42
 * @return SshCredential的结果
 */
public record SshCredential(String username, String password, String privateKey) {

    /**
     * 判断是否为公钥认证
     *
     * @return 是否携带私钥
     */
    public boolean hasKey() {
        return privateKey != null && !privateKey.isEmpty();
    }
}

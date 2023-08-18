package com.chua.starter.rpc.support.secret;

import java.net.InetSocketAddress;

/**
 * 密钥解释器
 *
 * @author CH
 */
public interface SecretResolver {
    /**
     * 获取sk
     *
     * @param accessKey     ak
     * @param secretKey     sk
     * @param remoteAddress 客户端地址
     * @return sk
     */
    boolean resolve(String accessKey, String secretKey, InetSocketAddress remoteAddress);
}

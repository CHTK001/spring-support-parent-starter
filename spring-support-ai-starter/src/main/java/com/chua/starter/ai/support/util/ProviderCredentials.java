package com.chua.starter.ai.support.util;

import java.util.List;
import java.util.Map;

/**
 * 旧调用点的兼容入口，凭据解释由账号模块统一维护。
 * @author CH
 */
public final class ProviderCredentials {
    private ProviderCredentials() { }
    /** @param credentials 凭据 @return 全部密钥 */
    public static List<String> apiKeys(Map<String, Object> credentials) {
        return com.chua.starter.account.channel.support.ProviderCredentials.apiKeys(credentials);
    }
    /** @param credentials 凭据 @return 首个密钥 */
    public static String firstApiKey(Map<String, Object> credentials) {
        return com.chua.starter.account.channel.support.ProviderCredentials.firstApiKey(credentials);
    }
    /** @param credentials 凭据 @return 签名密钥 */
    public static String secretKey(Map<String, Object> credentials) {
        return com.chua.starter.account.channel.support.ProviderCredentials.secretKey(credentials);
    }
}

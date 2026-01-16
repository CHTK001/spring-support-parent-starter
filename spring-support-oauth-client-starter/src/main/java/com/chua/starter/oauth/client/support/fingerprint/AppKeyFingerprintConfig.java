package com.chua.starter.oauth.client.support.fingerprint;

import java.util.HashSet;
import java.util.Set;

/**
 * AppKey 指纹配置工具类
 * <p>
 * 用于从配置文件读取的 appKey 指纹配置信息
 * </p>
 *
 * @author CH
 */
public class AppKeyFingerprintConfig {
    
    /**
     * 检查指纹是否在白名单中
     *
     * @param enableFingerprint 是否启用指纹检测（0-不启用, 1-启用）
     * @param fingerprintWhitelist 指纹白名单（多个指纹用逗号分隔）
     * @param fingerprint 待检查的指纹
     * @return true 如果在白名单中或白名单为空，false 如果不在白名单中
     */
    public static boolean isFingerprintAllowed(Integer enableFingerprint, String fingerprintWhitelist, String fingerprint) {
        // 如果未启用指纹检测，允许所有指纹
        if (enableFingerprint == null || enableFingerprint != 1) {
            return true;
        }
        
        // 如果白名单为空，只验证指纹是否存在（不验证是否匹配）
        if (fingerprintWhitelist == null || fingerprintWhitelist.trim().isEmpty()) {
            return true;
        }
        
        // 如果指纹为空，不允许
        if (fingerprint == null || fingerprint.trim().isEmpty()) {
            return false;
        }
        
        // 检查指纹是否在白名单中
        String[] whitelistArray = fingerprintWhitelist.split(",");
        Set<String> whitelistSet = new HashSet<>();
        for (String item : whitelistArray) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                whitelistSet.add(trimmed);
            }
        }
        
        return whitelistSet.contains(fingerprint.trim());
    }
}


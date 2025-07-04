package com.chua.starter.oauth.sso.support.authenticate;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.utils.StringUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存AKSK认证器实现
 * <p>使用内存存储AKSK配置信息，适用于单机部署或测试环境</p>
 * 
 * @author CH
 * @since 2024/12/04
 */
@Slf4j
@SpiDefault
@Extension("memory")
public class MemoryAkSkAuthenticator implements AkSkAuthenticator {

    private final ConcurrentMap<String, AkSkConfig> akskStore = new ConcurrentHashMap<>();

    @Override
    public boolean authenticate(String accessKey, String secretKey) {
        if (StringUtils.isEmpty(accessKey) || StringUtils.isEmpty(secretKey)) {
            return false;
        }

        try {
            String storedSecretKey = getSecretKey(accessKey);
            return secretKey.equals(storedSecretKey);
        } catch (Exception e) {
            log.error("内存AKSK认证失败: accessKey={}", accessKey, e);
            return false;
        }
    }

    @Override
    public boolean authenticate(String accessKey, String secretKey, String clientIp) {
        if (!authenticate(accessKey, secretKey)) {
            return false;
        }

        return isIpAllowed(accessKey, clientIp);
    }

    @Override
    public String getSecretKey(String accessKey) {
        if (StringUtils.isEmpty(accessKey)) {
            return null;
        }

        AkSkConfig config = akskStore.get(accessKey);
        return config != null ? config.getSecretKey() : null;
    }

    @Override
    public List<String> getAllowedIps(String accessKey) {
        if (StringUtils.isEmpty(accessKey)) {
            return new ArrayList<>();
        }

        AkSkConfig config = akskStore.get(accessKey);
        return config != null && config.getAllowedIps() != null ? 
               new ArrayList<>(config.getAllowedIps()) : new ArrayList<>();
    }

    @Override
    public boolean isIpAllowed(String accessKey, String clientIp) {
        if (StringUtils.isEmpty(clientIp)) {
            return false;
        }

        List<String> allowedIps = getAllowedIps(accessKey);
        if (allowedIps.isEmpty()) {
            // 如果没有配置IP限制，则允许所有IP
            return true;
        }

        return allowedIps.stream().anyMatch(allowedIp -> isIpMatch(clientIp, allowedIp));
    }

    @Override
    public boolean saveAkSk(String accessKey, String secretKey, List<String> allowedIps) {
        if (StringUtils.isEmpty(accessKey) || StringUtils.isEmpty(secretKey)) {
            return false;
        }

        try {
            AkSkConfig config = new AkSkConfig();
            config.setSecretKey(secretKey);
            config.setAllowedIps(allowedIps != null ? new ArrayList<>(allowedIps) : new ArrayList<>());
            config.setCreateTime(System.currentTimeMillis());
            config.setUpdateTime(System.currentTimeMillis());

            akskStore.put(accessKey, config);
            log.info("保存AKSK配置成功: accessKey={}", accessKey);
            return true;
        } catch (Exception e) {
            log.error("保存AKSK配置失败: accessKey={}", accessKey, e);
            return false;
        }
    }

    @Override
    public boolean removeAkSk(String accessKey) {
        if (StringUtils.isEmpty(accessKey)) {
            return false;
        }

        try {
            AkSkConfig removed = akskStore.remove(accessKey);
            boolean result = removed != null;
            log.info("删除AKSK配置: accessKey={}, result={}", accessKey, result);
            return result;
        } catch (Exception e) {
            log.error("删除AKSK配置失败: accessKey={}", accessKey, e);
            return false;
        }
    }

    @Override
    public boolean exists(String accessKey) {
        if (StringUtils.isEmpty(accessKey)) {
            return false;
        }

        return akskStore.containsKey(accessKey);
    }

    /**
     * 检查IP是否匹配
     */
    private boolean isIpMatch(String clientIp, String allowedIp) {
        if (allowedIp.equals(clientIp)) {
            return true;
        }

        // 支持CIDR格式的IP段匹配（简单实现）
        if (allowedIp.contains("/")) {
            String[] parts = allowedIp.split("/");
            if (parts.length == 2) {
                String network = parts[0];
                // 简单的网段匹配，实际应用中可以使用更精确的CIDR匹配算法
                return clientIp.startsWith(network.substring(0, network.lastIndexOf(".")));
            }
        }

        return false;
    }

    /**
     * 获取所有AKSK配置（用于调试和管理）
     */
    public ConcurrentMap<String, AkSkConfig> getAllConfigs() {
        return new ConcurrentHashMap<>(akskStore);
    }

    /**
     * 清空所有AKSK配置
     */
    public void clearAll() {
        akskStore.clear();
        log.info("清空所有AKSK配置");
    }

    /**
     * 获取配置数量
     */
    public int size() {
        return akskStore.size();
    }

    /**
     * 初始化默认配置（可选）
     */
    public void initDefaultConfigs() {
        // 添加一些默认的测试配置
        saveAkSk("test_ak", "test_sk", List.of("127.0.0.1", "localhost"));
        saveAkSk("admin_ak", "admin_sk", new ArrayList<>()); // 不限制IP
        log.info("初始化默认AKSK配置完成");
    }

    /**
     * AKSK配置数据结构
     */
    @Data
    public static class AkSkConfig {
        /**
         * Secret Key
         */
        private String secretKey;
        
        /**
         * 允许的IP列表
         */
        private List<String> allowedIps = new ArrayList<>();
        
        /**
         * 创建时间
         */
        private Long createTime;
        
        /**
         * 更新时间
         */
        private Long updateTime;
    }
}

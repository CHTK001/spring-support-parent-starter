package com.chua.starter.oauth.sso.support.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 认证缓存工具类
 * <p>提供高性能的认证结果缓存，减少重复认证操作</p>
 * 
 * @author CH
 * @since 2024/12/04
 */
@Slf4j
public class AuthCache {

    /**
     * AKSK认证结果缓存
     * <p>缓存AKSK认证结果，避免重复验证</p>
     */
    private static final Cache<String, Boolean> AKSK_CACHE = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /**
     * IP白名单检查缓存
     * <p>缓存IP白名单检查结果</p>
     */
    private static final Cache<String, Boolean> IP_WHITELIST_CACHE = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /**
     * 用户认证结果缓存
     * <p>缓存用户认证结果，提高响应速度</p>
     */
    private static final Cache<String, Object> USER_AUTH_CACHE = Caffeine.newBuilder()
            .maximumSize(20000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /**
     * 获取或计算AKSK认证结果
     * 
     * @param accessKey Access Key
     * @param secretKey Secret Key
     * @param clientIp 客户端IP
     * @param authenticator 认证函数
     * @return 认证结果
     */
    public static boolean getOrComputeAkSkAuth(String accessKey, String secretKey, String clientIp, 
                                               Function<String, Boolean> authenticator) {
        String cacheKey = buildAkSkCacheKey(accessKey, secretKey, clientIp);
        return AKSK_CACHE.get(cacheKey, key -> {
            try {
                return authenticator.apply(key);
            } catch (Exception e) {
                log.warn("AKSK认证失败: {}", key, e);
                return false;
            }
        });
    }

    /**
     * 获取或计算IP白名单检查结果
     * 
     * @param accessKey Access Key
     * @param clientIp 客户端IP
     * @param checker IP检查函数
     * @return 检查结果
     */
    public static boolean getOrComputeIpWhitelist(String accessKey, String clientIp, 
                                                  Function<String, Boolean> checker) {
        String cacheKey = buildIpCacheKey(accessKey, clientIp);
        return IP_WHITELIST_CACHE.get(cacheKey, key -> {
            try {
                return checker.apply(key);
            } catch (Exception e) {
                log.warn("IP白名单检查失败: {}", key, e);
                return false;
            }
        });
    }

    /**
     * 获取或计算用户认证结果
     * 
     * @param token 用户令牌
     * @param resolver 认证解析函数
     * @return 认证结果
     */
    @SuppressWarnings("unchecked")
    public static <T> T getOrComputeUserAuth(String token, Function<String, T> resolver) {
        return (T) USER_AUTH_CACHE.get(token, key -> {
            try {
                return resolver.apply(key);
            } catch (Exception e) {
                log.warn("用户认证失败: {}", key, e);
                return null;
            }
        });
    }

    /**
     * 缓存AKSK认证结果
     * 
     * @param accessKey Access Key
     * @param secretKey Secret Key
     * @param clientIp 客户端IP
     * @param result 认证结果
     */
    public static void putAkSkAuth(String accessKey, String secretKey, String clientIp, boolean result) {
        String cacheKey = buildAkSkCacheKey(accessKey, secretKey, clientIp);
        AKSK_CACHE.put(cacheKey, result);
    }

    /**
     * 缓存IP白名单检查结果
     * 
     * @param accessKey Access Key
     * @param clientIp 客户端IP
     * @param result 检查结果
     */
    public static void putIpWhitelist(String accessKey, String clientIp, boolean result) {
        String cacheKey = buildIpCacheKey(accessKey, clientIp);
        IP_WHITELIST_CACHE.put(cacheKey, result);
    }

    /**
     * 缓存用户认证结果
     * 
     * @param token 用户令牌
     * @param result 认证结果
     */
    public static void putUserAuth(String token, Object result) {
        if (token != null && result != null) {
            USER_AUTH_CACHE.put(token, result);
        }
    }

    /**
     * 清除AKSK认证缓存
     * 
     * @param accessKey Access Key
     */
    public static void evictAkSkAuth(String accessKey) {
        AKSK_CACHE.asMap().keySet().removeIf(key -> key.startsWith(accessKey + ":"));
        log.debug("清除AKSK认证缓存: {}", accessKey);
    }

    /**
     * 清除用户认证缓存
     * 
     * @param token 用户令牌
     */
    public static void evictUserAuth(String token) {
        USER_AUTH_CACHE.invalidate(token);
        log.debug("清除用户认证缓存: {}", token);
    }

    /**
     * 清除所有缓存
     */
    public static void evictAll() {
        AKSK_CACHE.invalidateAll();
        IP_WHITELIST_CACHE.invalidateAll();
        USER_AUTH_CACHE.invalidateAll();
        log.info("清除所有认证缓存");
    }

    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息
     */
    public static CacheStats getCacheStats() {
        return CacheStats.builder()
                .akskCacheSize(AKSK_CACHE.estimatedSize())
                .akskHitRate(AKSK_CACHE.stats().hitRate())
                .ipCacheSize(IP_WHITELIST_CACHE.estimatedSize())
                .ipHitRate(IP_WHITELIST_CACHE.stats().hitRate())
                .userCacheSize(USER_AUTH_CACHE.estimatedSize())
                .userHitRate(USER_AUTH_CACHE.stats().hitRate())
                .build();
    }

    /**
     * 构建AKSK缓存键
     */
    private static String buildAkSkCacheKey(String accessKey, String secretKey, String clientIp) {
        return accessKey + ":" + secretKey.hashCode() + ":" + (clientIp != null ? clientIp : "");
    }

    /**
     * 构建IP缓存键
     */
    private static String buildIpCacheKey(String accessKey, String clientIp) {
        return accessKey + ":" + (clientIp != null ? clientIp : "");
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final long akskCacheSize;
        private final double akskHitRate;
        private final long ipCacheSize;
        private final double ipHitRate;
        private final long userCacheSize;
        private final double userHitRate;

        private CacheStats(Builder builder) {
            this.akskCacheSize = builder.akskCacheSize;
            this.akskHitRate = builder.akskHitRate;
            this.ipCacheSize = builder.ipCacheSize;
            this.ipHitRate = builder.ipHitRate;
            this.userCacheSize = builder.userCacheSize;
            this.userHitRate = builder.userHitRate;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public long getAkskCacheSize() { return akskCacheSize; }
        public double getAkskHitRate() { return akskHitRate; }
        public long getIpCacheSize() { return ipCacheSize; }
        public double getIpHitRate() { return ipHitRate; }
        public long getUserCacheSize() { return userCacheSize; }
        public double getUserHitRate() { return userHitRate; }

        @Override
        public String toString() {
            return String.format(
                "CacheStats{aksk: %d(%.2f%%), ip: %d(%.2f%%), user: %d(%.2f%%)}",
                akskCacheSize, akskHitRate * 100,
                ipCacheSize, ipHitRate * 100,
                userCacheSize, userHitRate * 100
            );
        }

        public static class Builder {
            private long akskCacheSize;
            private double akskHitRate;
            private long ipCacheSize;
            private double ipHitRate;
            private long userCacheSize;
            private double userHitRate;

            public Builder akskCacheSize(long akskCacheSize) {
                this.akskCacheSize = akskCacheSize;
                return this;
            }

            public Builder akskHitRate(double akskHitRate) {
                this.akskHitRate = akskHitRate;
                return this;
            }

            public Builder ipCacheSize(long ipCacheSize) {
                this.ipCacheSize = ipCacheSize;
                return this;
            }

            public Builder ipHitRate(double ipHitRate) {
                this.ipHitRate = ipHitRate;
                return this;
            }

            public Builder userCacheSize(long userCacheSize) {
                this.userCacheSize = userCacheSize;
                return this;
            }

            public Builder userHitRate(double userHitRate) {
                this.userHitRate = userHitRate;
                return this;
            }

            public CacheStats build() {
                return new CacheStats(this);
            }
        }
    }
}

package com.chua.starter.lock.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * 锁模块配置。
 *
 * @author CH
 * @since 2026-03-28
 */
@ConfigurationProperties(prefix = LockProperties.PRE)
public class LockProperties {

    public static final String PRE = "plugin.lock";

    private boolean enabled = true;

    private boolean registerAdditionalProviders = true;

    private final IdempotentProperties idempotent = new IdempotentProperties();

    private final CompatibilityProperties compatibility = new CompatibilityProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRegisterAdditionalProviders() {
        return registerAdditionalProviders;
    }

    public void setRegisterAdditionalProviders(boolean registerAdditionalProviders) {
        this.registerAdditionalProviders = registerAdditionalProviders;
    }

    public IdempotentProperties getIdempotent() {
        return idempotent;
    }

    public CompatibilityProperties getCompatibility() {
        return compatibility;
    }

    public static class IdempotentProperties {

        private boolean enabled = true;

        private String provider = "auto";

        private String keyPrefix = "lock:idempotent:";

        private long defaultTimeout = 5L;

        private TimeUnit defaultTimeUnit = TimeUnit.SECONDS;

        private long localCleanIntervalSeconds = 60L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public long getDefaultTimeout() {
            return defaultTimeout;
        }

        public void setDefaultTimeout(long defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
        }

        public TimeUnit getDefaultTimeUnit() {
            return defaultTimeUnit;
        }

        public void setDefaultTimeUnit(TimeUnit defaultTimeUnit) {
            this.defaultTimeUnit = defaultTimeUnit;
        }

        public long getLocalCleanIntervalSeconds() {
            return localCleanIntervalSeconds;
        }

        public void setLocalCleanIntervalSeconds(long localCleanIntervalSeconds) {
            this.localCleanIntervalSeconds = localCleanIntervalSeconds;
        }
    }

    public static class CompatibilityProperties {

        private boolean strategyAnnotations = true;

        private String strategyLockType = "reentrant";

        public boolean isStrategyAnnotations() {
            return strategyAnnotations;
        }

        public void setStrategyAnnotations(boolean strategyAnnotations) {
            this.strategyAnnotations = strategyAnnotations;
        }

        public String getStrategyLockType() {
            return strategyLockType;
        }

        public void setStrategyLockType(String strategyLockType) {
            this.strategyLockType = strategyLockType;
        }
    }
}

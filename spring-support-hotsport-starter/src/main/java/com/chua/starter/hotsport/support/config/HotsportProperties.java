package com.chua.starter.hotsport.support.config;

import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.LeakRecord;
import com.chua.runtime.apm.storage.LogRecord;
import com.chua.runtime.apm.storage.Query;
import com.chua.runtime.apm.storage.StorageManager;
import com.chua.runtime.apm.storage.TransmissionEvent;
import com.chua.runtime.protocol.DependencyEdge;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Hotsport 配置属性 — 绑定 {@code hotsport.*}。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
@ConfigurationProperties(prefix = "hotsport")
public class HotsportProperties {

    /** 智能体 配置。 */
    private Agent agent = new Agent();

    /** APM 存储配置。 */
    private Apm apm = new Apm();

    /**
     * 获取智能体。
     * @return 获取智能体的结果
     */
    public Agent getAgent() {
        return agent;
    }

    /**
     * 设置智能体。
     * @param agent 智能体
     */
    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    /**
     * 获取apm。
     * @return 获取apm的结果
     */
    public Apm getApm() {
        return apm;
    }

    /**
     * 设置apm。
     * @param apm apm
     */
    public void setApm(Apm apm) {
        this.apm = apm;
    }

    /** Agent 子配置。 */
    public static class Agent {

        /** 是否启用自注入。 */
        private boolean enabled = true;

        /** Shell 端口。 */
        private int shellPort = 4567;

        /** 额外 智能体 参数（逗号分隔 钥匙=价值观）。 */
        private String options = "";

        /**
         * 是否已启用。
         * @return 是否已启用的结果
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置已启用。
         * @param enabled 已启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
          * 获取Shell端口。
         * @return 获取shell端口的结果
         */
        public int getShellPort() {
            return shellPort;
        }

        /**
          * 设置Shell端口。
         * @param shellPort Shell端口
         */
        public void setShellPort(int shellPort) {
            this.shellPort = shellPort;
        }

        /**
         * 获取期权。
         * @return 获取期权的结果
         */
        public String getOptions() {
            return options;
        }

        /**
         * 设置期权。
         * @param options 期权
         */
        public void setOptions(String options) {
            this.options = options;
        }
    }

    /** APM 子配置。 */
    public static class Apm {

        /** 存储配置。 */
        private Storage storage = new Storage();

        /**
         * 获取storage。
         * @return 获取storage的结果
         */
        public Storage getStorage() {
            return storage;
        }

        /**
         * 设置storage。
         * @param storage storage
         */
        public void setStorage(Storage storage) {
            this.storage = storage;
        }
    }

    /** 存储子配置。 */
    public static class Storage {

        /** 存储类型：inmemory / sqlite / noop。 */
        private String type = "inmemory";

        /** 数据保留毫秒。 */
        private long retentionMs = 7L * 24 * 60 * 60 * 1000;

        /**
         * 获取类型。
         * @return 获取类型的结果
         */
        public String getType() {
            return type;
        }

        /**
         * 设置类型。
         * @param type 类型
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * 获取retentionms。
         * @return 获取retentionms的结果
         */
        public long getRetentionMs() {
            return retentionMs;
        }

        /**
         * 设置retentionms。
         * @param retentionMs retentionms
         */
        public void setRetentionMs(long retentionMs) {
            this.retentionMs = retentionMs;
        }
    }

    // ==================================================================
    // 静态工具 — APM 存储访问兜底
    // ==================================================================

    /**
      * 获取当前 apmstorage（可能为 noopstorage，永不为 空）。
     *
     * @return 存储实例
     */
    public static ApmStorage storage() {
        try {
            ApmStorage s = StorageManager.get();
            return s != null ? s : noop();
        } catch (Throwable t) {
            return noop();
        }
    }

    /** Noop 兜底实例（storage管理器 未初始化时使用）。 */
    private static final ApmStorage NOOP = new ApmStorage() {
        @Override
        public String name() {
            return "noop";
        }

        @Override
        public void start(com.chua.runtime.apm.storage.StorageConfig config) {
        }

        @Override
        public void stop() {
        }

        @Override
        public void appendTransmission(TransmissionEvent event) {
        }

        @Override
        public void appendDependency(DependencyEdge edge) {
        }

        @Override
        public void appendLeak(LeakRecord record) {
        }

        @Override
        public void appendLog(LogRecord record) {
        }

        @Override
        public List<TransmissionEvent> queryTransmissions(Query query) {
            return List.of();
        }

        @Override
        public List<DependencyEdge> queryDependencies(Query query) {
            return List.of();
        }

        @Override
        public List<LeakRecord> queryLeaks(Query query) {
            return List.of();
        }

        @Override
        public List<LogRecord> queryLogs(Query query) {
            return List.of();
        }

        @Override
        public Map<String, Long> stats() {
            return Map.of();
        }

        @Override
        public long cleanup(long retentionMillis) {
            return 0L;
        }
    };

    /**
     * noop。
     * @return noop的结果
     */
    private static ApmStorage noop() {
        return NOOP;
    }
}

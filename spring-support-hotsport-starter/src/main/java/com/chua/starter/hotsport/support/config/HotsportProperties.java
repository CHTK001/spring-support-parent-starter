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
 * Hotsport 配置属性 — 绑定 {@code plugin.hotsport.*}。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
@ConfigurationProperties(prefix = "plugin.hotsport")
public class HotsportProperties {

    /**
     * Agent 配置。
    */
    private Agent agent = new Agent();

    /**
     * APM 存储配置。
    */
    private Apm apm = new Apm();

    /**
     * 获取 Agent 配置。
     *
     * @return Agent 配置
     */
    public Agent getAgent() {
        return agent;
    }

    /**
     * 设置 Agent 配置。
     *
     * @param agent Agent 配置
     */
    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    /**
     * 获取 APM 配置。
     *
     * @return APM 配置
     */
    public Apm getApm() {
        return apm;
    }

    /**
     * 设置 APM 配置。
     *
     * @param apm APM 配置
     */
    public void setApm(Apm apm) {
        this.apm = apm;
    }

    /**
     * Agent 子配置。
    */
    public static class Agent {

        /**
         * 是否启用自注入。
        */
        private boolean enabled = true;

        /**
         * Shell 端口。
        */
        private int shellPort = 4567;

        /**
         * 额外 Agent 参数（逗号分隔 Key=值）。
        */
        private String options = "";

        /**
         * 是否已启用自注入。
         *
         * @return 是否已启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否已启用自注入。
         *
         * @param enabled 是否已启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取 Shell 端口。
         *
         * @return Shell 端口
         */
        public int getShellPort() {
            return shellPort;
        }

        /**
         * 设置 Shell 端口。
         *
         * @param shellPort Shell 端口
         */
        public void setShellPort(int shellPort) {
            this.shellPort = shellPort;
        }

        /**
         * 获取额外 Agent 参数。
         *
         * @return 额外 Agent 参数
         */
        public String getOptions() {
            return options;
        }

        /**
         * 设置额外 Agent 参数。
         *
         * @param options 额外 Agent 参数
         */
        public void setOptions(String options) {
            this.options = options;
        }
    }

    /**
     * APM 子配置。
    */
    public static class Apm {

        /**
         * 存储配置。
        */
        private Storage storage = new Storage();

        /**
         * 获取存储配置。
         *
         * @return 存储配置
         */
        public Storage getStorage() {
            return storage;
        }

        /**
         * 设置存储配置。
         *
         * @param storage 存储配置
         */
        public void setStorage(Storage storage) {
            this.storage = storage;
        }

        /**
         * 把本配置展开成 APM 存储层可识别的键值对。
         *
         * <p>键名与 agent options / 系统属性一致（{@code apm.storage.*}），
         * 由 {@code ApmBootstrap} 统一消费；未显式配置的项不写入，交由默认值兜底。</p>
         *
         * @return 存储配置键值对
         */
        public Map<String, String> toStorageConfig() {
            return toStorageConfig(false);
        }

        /**
         * 把本配置展开成 APM 存储层可识别的键值对。
         *
         * <p><b>为什么需要 {@code includeDefaults}</b>：运行中热更新时，用户可能把某项
         * 改回默认（例如清空落盘路径）。若只输出「非空项」，热更新看到的配置与旧配置
         * 完全相同，会被判定为无变化而直接跳过，清空动作就永远不生效。
         * 因此热更新路径传 {@code true}，把当前值完整输出，空值也如实输出
         * （下游按「空 = 使用默认」处理）。</p>
         *
         * @param includeDefaults 是否输出全部键（含空值/默认值）
         * @return 存储配置键值对
         */
        public Map<String, String> toStorageConfig(boolean includeDefaults) {
            Map<String, String> config = new java.util.LinkedHashMap<>();
            if (storage == null) {
                return config;
            }
            String type = storage.getType() == null ? "" : storage.getType().trim();
            if (!type.isEmpty() || includeDefaults) {
                config.put("apm.storage.type", type);
            }
            if (storage.getRetentionMs() > 0 || includeDefaults) {
                config.put("apm.storage.retention.ms", String.valueOf(storage.getRetentionMs()));
            }
            if (storage.getCapacity() > 0 || includeDefaults) {
                config.put("apm.storage.capacity", String.valueOf(storage.getCapacity()));
            }
            String path = storage.getPath() == null ? "" : storage.getPath().trim();
            if (!path.isEmpty() || includeDefaults) {
                config.put("apm.storage.path", path);
            }
            if (storage.getAsync() != null || includeDefaults) {
                config.put("apm.storage.async", storage.getAsync() == null
                        ? "true" : String.valueOf(storage.getAsync()));
            }
            return config;
        }
    }

    /**
     * 存储子配置。
    */
    public static class Storage {

        /**
         * 存储类型：inmemory / sqlite / noop。
        */
        private String type = "inmemory";

        /**
         * 数据保留毫秒。
        */
        private long retentionMs = 7L * 24 * 60 * 60 * 1000;

        /**
         * 单表容量上限（超出按最旧淘汰）。
        */
        private int capacity = 100000;

        /**
         * 存储落盘目录（sqlite 等非内存实现使用；为空时用临时目录）。
        */
        private String path = "";

        /**
         * 写入模式：true 异步批量写（默认，业务线程不阻塞），false 逐条同步写。
         *
         * <p>null 表示「未显式配置」，沿用 APM 侧默认（异步）。</p>
         */
        private Boolean async;

        /**
         * 获取写入模式。
         *
         * @return 写入模式；未配置返回 null
         */
        public Boolean getAsync() {
            return async;
        }

        /**
         * 设置写入模式。
         *
         * @param async 写入模式
         */
        public void setAsync(Boolean async) {
            this.async = async;
        }

        /**
         * 获取存储类型。
         *
         * @return 存储类型
         */
        public String getType() {
            return type;
        }

        /**
         * 设置存储类型。
         *
         * @param type 存储类型
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * 获取数据保留毫秒。
         *
         * @return 数据保留毫秒
         */
        public long getRetentionMs() {
            return retentionMs;
        }

        /**
         * 设置数据保留毫秒。
         *
         * @param retentionMs 数据保留毫秒
         */
        public void setRetentionMs(long retentionMs) {
            this.retentionMs = retentionMs;
        }

        /**
         * 获取单表容量上限。
         *
         * @return 容量上限
         */
        public int getCapacity() {
            return capacity;
        }

        /**
         * 设置单表容量上限。
         *
         * @param capacity 容量上限
         */
        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        /**
         * 获取存储落盘目录。
         *
         * @return 落盘目录
         */
        public String getPath() {
            return path;
        }

        /**
         * 设置存储落盘目录。
         *
         * @param path 落盘目录
         */
        public void setPath(String path) {
            this.path = path;
        }
    }

    // ==================================================================
    // 静态工具 — APM 存储访问兜底
    // ==================================================================

    /**
     * 获取当前 ApmStorage（可能为 NoopStorage，永不为 null）。
     *
     * @return 存储实例
     */
    public static ApmStorage storage() {
        ApmStorage s;
        try {
            s = StorageManager.get();
        } catch (Throwable t) {
            s = null;
        }
        return s != null ? s : noop();
    }

    /**
     * Noop 兜底实例（storage管理器 未初始化时使用）。
    */
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
     * 获取 Noop 兜底实例。
     *
     * @return Noop 存储实例
     */
    private static ApmStorage noop() {
        return NOOP;
    }
}

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
 * <p>含两个一级子配置：{@link Agent}（Agent 自注入）与 {@link Apm}（APM 存储 / HTTP 上报接收 / TCP 接收端）。
 * 所有键均可省略，省略即用默认值；时间单位如无说明为毫秒。</p>
 *
 * <p>完整配置示例：</p>
 *
 * <pre>{@code
 * plugin:
 *   hotsport:
 *     agent:
 *       enabled: true        # 是否自注入 Runtime Agent，默认 true
 *       shell-port: 4567     # Agent Shell 端口
 *       options: ""          # 附加 Agent 参数（逗号分隔 Key=值）
 *     apm:
 *       storage:
 *         type: sqlite       # sqlite / mysql / inmemory / noop，默认 sqlite
 *         path: ""           # sqlite 落盘目录；空 = APM 侧默认
 *         retention-ms: 604800000   # 数据保留 7 天
 *         capacity: 100000   # 单表容量上限，超出按最旧淘汰
 *         url: ""            # mysql JDBC URL，type=mysql 时必填
 *         username: ""       # mysql 用户名
 *         password: ""       # mysql 密码
 *         pool-size: 4       # mysql 写入连接池大小
 *         batch-size: 200    # mysql 单批写入条数
 *         async: true        # true 异步批量写 / false 同步写；不配置沿用 APM 默认异步
 *         maintenance-interval-ms: 0   # 自维护周期，0 = 默认 60 秒（下限 5 秒）
 *         flush-timeout-ms: 0          # 停机排空等待，0 = APM 侧默认
 *       ingest:
 *         token: ""          # 上报共享密钥；空 = 不校验 x-hotsport-token
 *         max-batch: 1000    # 单批每种类型最大条数
 *       tcp:
 *         enabled: false     # TCP 接收端开关，默认关闭
 *         host: 0.0.0.0      # 监听地址
 *         port: 19392        # 监听端口
 *         topic: hotsport-events   # 订阅主题名
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
@ConfigurationProperties(prefix = "plugin.hotsport")
public class HotsportProperties {

    /**
     * Agent 配置。
     *
     * <p>绑定 {@code plugin.hotsport.agent.*}，控制是否对当前 JVM 自注入 Runtime Agent。</p>
     *
     * <pre>{@code
     * plugin:
     *   hotsport:
     *     agent:
     *       enabled: true
     *       shell-port: 4567
     *       options: ""
     * }</pre>
    */
    private Agent agent = new Agent();

    /**
     * APM 配置。
     *
     * <p>绑定 {@code plugin.hotsport.apm.*}，含存储（storage）、HTTP 上报接收（ingest）、
     * TCP 接收端（tcp）三个子配置。</p>
     *
     * <pre>{@code
     * plugin:
     *   hotsport:
     *     apm:
     *       storage:
     *         type: sqlite
     *       ingest:
     *         token: ""
     *       tcp:
     *         enabled: false
     * }</pre>
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
         * 是否启用 Agent 自注入。
         *
         * <p>默认 {@code true}。开启后经 spring-support-agent-starter 对当前 JVM 自注入
         * Runtime Agent 做字节码采集；JDK 25 等存在 retransform 风险的环境建议设为 {@code false}。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     agent:
         *       enabled: true   # 默认 true
         * }</pre>
        */
        private boolean enabled = true;

        /**
         * Agent Shell 监听端口。
         *
         * <p>默认 {@code 4567}。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     agent:
         *       shell-port: 4567   # 默认 4567
         * }</pre>
        */
        // [P3C A20 豁免]
        private int shellPort = 4567;

        /**
         * 额外 Agent 参数（逗号分隔 {@code Key=值}）。
         *
         * <p>默认空串 {@code ""}，表示不附加参数、走 agent 侧默认配置。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     agent:
         *       options: ""   # 默认空串
         * }</pre>
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
         *
         * <p>绑定 {@code plugin.hotsport.apm.storage.*}；经 {@link #toStorageConfig()}
         * 展开为 {@code apm.storage.*} 键值对交给 APM 存储层。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         type: sqlite   # sqlite / mysql / inmemory / noop
         * }</pre>
        */
        private Storage storage = new Storage();

        /**
         * 上报接收配置（HTTP {@code POST /api/apm/ingest}）。
         *
         * <p>绑定 {@code plugin.hotsport.apm.ingest.*}：共享密钥鉴权与单批上限。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       ingest:
         *         token: ""
         *         max-batch: 1000
         * }</pre>
        */
        private Ingest ingest = new Ingest();

        /**
         * TCP 接收端配置（默认关闭）。
         *
         * <p>绑定 {@code plugin.hotsport.apm.tcp.*}，供无法直连 HTTP 的采集端按主题推送数据。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       tcp:
         *         enabled: false
         *         host: 0.0.0.0
         *         port: 19392
         *         topic: hotsport-events
         * }</pre>
        */
        private Tcp tcp = new Tcp();

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
         * 获取上报接收配置。
         *
         * @return 上报接收配置
         */
        public Ingest getIngest() {
            return ingest;
        }

        /**
         * 设置上报接收配置。
         *
         * @param ingest 上报接收配置
         */
        public void setIngest(Ingest ingest) {
            this.ingest = ingest;
        }

        /**
         * 获取 TCP 接收端配置。
         *
         * @return TCP 接收端配置
         */
        public Tcp getTcp() {
            return tcp;
        }

        /**
         * 设置 TCP 接收端配置。
         *
         * @param tcp TCP 接收端配置
         */
        public void setTcp(Tcp tcp) {
            this.tcp = tcp;
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
            String url = storage.getUrl() == null ? "" : storage.getUrl().trim();
            if (!url.isEmpty() || includeDefaults) {
                config.put("apm.storage.url", url);
            }
            String username = storage.getUsername() == null ? "" : storage.getUsername().trim();
            if (!username.isEmpty() || includeDefaults) {
                config.put("apm.storage.username", username);
            }
            String password = storage.getPassword() == null ? "" : storage.getPassword();
            if (!password.isEmpty() || includeDefaults) {
                config.put("apm.storage.password", password);
            }
            if (storage.getPoolSize() > 0 || includeDefaults) {
                config.put("apm.storage.pool.size", String.valueOf(storage.getPoolSize()));
            }
            if (storage.getBatchSize() > 0 || includeDefaults) {
                config.put("apm.storage.batch.size", String.valueOf(storage.getBatchSize()));
            }
            if (storage.getAsync() != null || includeDefaults) {
                config.put("apm.storage.async", storage.getAsync() == null
                        ? "true" : String.valueOf(storage.getAsync()));
            }
            if (storage.getMaintenanceIntervalMs() > 0 || includeDefaults) {
                config.put("apm.storage.maintenance.interval.ms",
                        String.valueOf(storage.getMaintenanceIntervalMs()));
            }
            if (storage.getFlushTimeoutMs() > 0 || includeDefaults) {
                config.put("apm.storage.flush.timeout.ms", String.valueOf(storage.getFlushTimeoutMs()));
            }
            return config;
        }
    }

    /**
     * 上报接收子配置（HTTP {@code POST /api/apm/ingest}）。
    */
    public static class Ingest {

        /**
         * 上报共享密钥；为空（默认 {@code ""}）表示不校验。
         *
         * <p>非空时校验请求头 {@code x-hotsport-token}，不匹配返回 401；
         * 跨网络上报务必设置为强随机密钥。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       ingest:
         *         token: ""   # 默认空串 = 不校验
         * }</pre>
        */
        private String token = "";

        /**
         * 单批每种类型最大条数（超出部分截断丢弃）。
         *
         * <p>默认 {@code 1000}；配置为 ≤0 或未配置时，服务端同样按 1000 兜底。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       ingest:
         *         max-batch: 1000   # 默认 1000
         * }</pre>
        */
        // [P3C A20 豁免]
        private int maxBatch = 1000;

        /**
         * 获取共享密钥。
         *
         * @return 共享密钥；空表示不校验
         */
        public String getToken() {
            return token;
        }

        /**
         * 设置共享密钥。
         *
         * @param token 共享密钥
         */
        public void setToken(String token) {
            this.token = token;
        }

        /**
         * 获取单批每种类型最大条数。
         *
         * @return 单批上限
         */
        public int getMaxBatch() {
            return maxBatch;
        }

        /**
         * 设置单批每种类型最大条数。
         *
         * @param maxBatch 单批上限
         */
        public void setMaxBatch(int maxBatch) {
            this.maxBatch = maxBatch;
        }
    }

    /**
     * TCP 接收端子配置 — 供无法直连 HTTP 的采集端推送 APM 数据。
     *
     * <p>默认关闭；启用后监听的端口不属于本机白名单时，注意遵守部署机的端口管控约定。</p>
    */
    public static class Tcp {

        /**
         * 是否启用 TCP 接收端。
         *
         * <p>默认 {@code false}（关闭）；HTTP {@code /api/apm/ingest} 始终可用，不受本开关影响。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       tcp:
         *         enabled: false   # 默认 false
         * }</pre>
        */
        private boolean enabled = false;

        /**
         * TCP 接收端监听地址。
         *
         * <p>默认 {@code 0.0.0.0}；配置为空或空白时同样回退 {@code 0.0.0.0}。
         * 仅本机采集时建议收紧为 {@code 127.0.0.1}。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       tcp:
         *         host: 0.0.0.0   # 默认 0.0.0.0
         * }</pre>
        */
        private String host = "0.0.0.0";

        /**
         * TCP 接收端监听端口。
         *
         * <p>默认 {@code 19392}；配置为 ≤0 时回退 {@code 19392}。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       tcp:
         *         port: 19392   # 默认 19392
         * }</pre>
        */
        // [P3C A20 豁免]
        private int port = 19392;

        /**
         * 订阅主题名（采集端按主题投递）。
         *
         * <p>默认 {@code hotsport-events}；配置为空或空白时回退该默认值。
         * 采集端与接收端必须使用同一主题。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       tcp:
         *         topic: hotsport-events   # 默认 hotsport-events
         * }</pre>
        */
        private String topic = "hotsport-events";

        /**
         * 是否已启用。
         *
         * @return 是否已启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取监听地址。
         *
         * @return 监听地址
         */
        public String getHost() {
            return host;
        }

        /**
         * 设置监听地址。
         *
         * @param host 监听地址
         */
        public void setHost(String host) {
            this.host = host;
        }

        /**
         * 获取监听端口。
         *
         * @return 监听端口
         */
        public int getPort() {
            return port;
        }

        /**
         * 设置监听端口。
         *
         * @param port 监听端口
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * 获取订阅主题名。
         *
         * @return 主题名
         */
        public String getTopic() {
            return topic;
        }

        /**
         * 设置订阅主题名。
         *
         * @param topic 主题名
         */
        public void setTopic(String topic) {
            this.topic = topic;
        }
    }

    /**
     * 存储子配置。
    */
    public static class Storage {

        /**
         * 存储类型：sqlite / mysql / inmemory / noop。
         *
         * <p>默认 {@code sqlite}：真实数据库（SQLite 文件），重启不丢数据，
         * 且不需要外部服务，观测组件不会因为数据库不可用而拖住宿主。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         type: sqlite   # 默认 sqlite
         * }</pre>
        */
        private String type = "sqlite";

        /**
         * 数据保留毫秒（超过保留期的数据被定期清理）。
         *
         * <p>默认 {@code 604800000}（7 天）；配置为 ≤0 时不下发 {@code apm.storage.retention.ms}，
         * 交由 APM 侧默认值兜底。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         retention-ms: 604800000   # 默认 7 天
         * }</pre>
        */
        // [P3C A20 豁免]
        private long retentionMs = 7L * 24 * 60 * 60 * 1000;

        /**
         * 单表容量上限（超出按最旧淘汰）。
         *
         * <p>默认 {@code 100000}；配置为 ≤0 时不下发 {@code apm.storage.capacity}，
         * 交由 APM 侧默认值兜底。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         capacity: 100000   # 默认 100000
         * }</pre>
        */
        // [P3C A20 豁免]
        private int capacity = 100000;

        /**
         * 存储落盘目录（sqlite 等非内存实现使用；为空时用临时目录）。
         *
         * <p>默认空串 {@code ""}，表示交由 APM 侧选择默认落盘目录。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         path: ""   # 默认空串 = APM 侧默认目录
         * }</pre>
        */
        private String path = "";

        /**
         * JDBC URL（type=mysql 时必填）。
         *
         * <p>形如 {@code jdbc:mysql://127.0.0.1:3306/hotsport}。驱动是可选依赖，
         * 用 MySQL 存储时需自行引入 {@code mysql-connector-j}。默认空串 {@code ""}。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         url: "jdbc:mysql://127.0.0.1:3306/hotsport"   # type=mysql 时必填
         * }</pre>
        */
        private String url = "";

        /**
         * 数据库用户名（type=mysql 时使用）。
         *
         * <p>默认空串 {@code ""}。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         username: ""   # 默认空串
         * }</pre>
        */
        private String username = "";

        /**
         * 数据库密码（type=mysql 时使用）。
         *
         * <p>默认空串 {@code ""}；生产环境请使用强口令，勿将真实密码提交到配置仓库
         * （示例中的 {@code your-password} 仅为占位符）。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         password: "your-password"   # 默认空串；示例为占位符
         * }</pre>
        */
        private String password = "";

        /**
         * 连接池大小（type=mysql 时使用）。
         *
         * <p>默认 {@code 4}；配置为 ≤0 时不下发 {@code apm.storage.pool.size}，
         * 交由 APM 侧默认值兜底。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         pool-size: 4   # 默认 4
         * }</pre>
        */
        private int poolSize = 4;

        /**
         * 单批写入条数（type=mysql 时使用）。
         *
         * <p>默认 {@code 200}；配置为 ≤0 时不下发 {@code apm.storage.batch.size}，
         * 交由 APM 侧默认值兜底。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         batch-size: 200   # 默认 200
         * }</pre>
        */
        // [P3C A20 豁免]
        private int batchSize = 200;

        /**
         * 写入模式：true 异步批量写（默认，业务线程不阻塞），false 逐条同步写。
         *
         * <p>null 表示「未显式配置」，沿用 APM 侧默认（异步）；
         * 显式配置 {@code false} 时逐条同步写。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         async: true   # 不配置 = APM 侧默认异步
         * }</pre>
         */
        private Boolean async;

        /**
         * 自维护周期（毫秒）：按保留期清理过期数据、按容量淘汰最旧数据的检查间隔。
         *
         * <p>默认 {@code 0}，表示沿用 APM 侧默认（60 秒）；配置为 ≤0 时不下发
         * {@code apm.storage.maintenance.interval.ms}，同样走默认。下限 5 秒。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         maintenance-interval-ms: 0   # 0 = 默认 60 秒，下限 5 秒
         * }</pre>
         */
        private long maintenanceIntervalMs;

        /**
         * 关闭时等待异步队列落盘的最长时间（毫秒）。
         *
         * <p>默认 {@code 0}，表示沿用 APM 侧默认；配置为 ≤0 时不下发
         * {@code apm.storage.flush.timeout.ms}，同样走默认。
         * 设太短会在关闭瞬间丢数据，设太长会拖慢停机。</p>
         *
         * <pre>{@code
         * plugin:
         *   hotsport:
         *     apm:
         *       storage:
         *         flush-timeout-ms: 0   # 0 = APM 侧默认
         * }</pre>
         */
        private long flushTimeoutMs;

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
         * 获取自维护周期（毫秒）。
         *
         * @return 自维护周期；0 表示沿用 APM 侧默认（60 秒）
         */
        public long getMaintenanceIntervalMs() {
            return maintenanceIntervalMs;
        }

        /**
         * 设置自维护周期（毫秒）。
         *
         * @param maintenanceIntervalMs 自维护周期
         */
        public void setMaintenanceIntervalMs(long maintenanceIntervalMs) {
            this.maintenanceIntervalMs = maintenanceIntervalMs;
        }

        /**
         * 获取关闭时等待落盘的最长时间（毫秒）。
         *
         * @return 等待时长；0 表示沿用 APM 侧默认
         */
        public long getFlushTimeoutMs() {
            return flushTimeoutMs;
        }

        /**
         * 设置关闭时等待落盘的最长时间（毫秒）。
         *
         * @param flushTimeoutMs 等待时长
         */
        public void setFlushTimeoutMs(long flushTimeoutMs) {
            this.flushTimeoutMs = flushTimeoutMs;
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

        /**
         * 获取 JDBC URL。
         *
         * @return JDBC URL
         */
        public String getUrl() {
            return url;
        }

        /**
         * 设置 JDBC URL。
         *
         * @param url JDBC URL
         */
        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * 获取数据库用户名。
         *
         * @return 用户名
         */
        public String getUsername() {
            return username;
        }

        /**
         * 设置数据库用户名。
         *
         * @param username 用户名
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * 获取数据库密码。
         *
         * @return 密码
         */
        public String getPassword() {
            return password;
        }

        /**
         * 设置数据库密码。
         *
         * @param password 密码
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 获取连接池大小。
         *
         * @return 连接池大小
         */
        public int getPoolSize() {
            return poolSize;
        }

        /**
         * 设置连接池大小。
         *
         * @param poolSize 连接池大小
         */
        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        /**
         * 获取单批写入条数。
         *
         * @return 单批条数
         */
        public int getBatchSize() {
            return batchSize;
        }

        /**
         * 设置单批写入条数。
         *
         * @param batchSize 单批条数
         */
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
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

package com.chua.starter.osgi.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * osgi 集成配置属性。
 *
 * <p>配置前缀：{@code plugin.osgi}。控制 Felix 框架启动和远程管理。</p>
 *
 * <p>远程管理策略：shell 只支持一种协议，由 {@code protocol} 决定：
 * <ul>
 *   <li>{@code ssh}（默认）— 通过 Apache SSHD bundle 提供 SSH 远程管理</li>
 *   <li>{@code telnet} — 通过 Gogo shell bundle 提供 Telnet 远程管理（仅当 sshd-osgi 不在 classpath 时回退）</li>
 * </ul></p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.osgi")
public class OsgiProperties {

    /**
     * 是否启用 osgi 集成（默认开启）。
     */
    private boolean enabled = true;

    /**
     * Felix 框架持久化存储目录。
     */
    private String storage = "./osgi-cache";

    /**
     * Felix 框架初始启动级别。
     */
    private int startLevel = 4;

    /**
     * Felix 存储清理策略。
     *
     * <p>取值为 OSGi 规范定义的 {@code onFirstInit} / {@code onInit} / {@code none}：</p>
     * <ul>
     *   <li>{@code onFirstInit}（默认）— 首次初始化时清空缓存目录</li>
     *   <li>{@code onInit} — 每次初始化都清空</li>
     *   <li>{@code none} — 不清空，保留上次运行的 bundle 状态（支持重启续用）</li>
     * </ul>
     */
    private String storageClean = "onFirstInit";

    /**
     * 是否在安装 bundle 后自动启动（默认开启）。
     */
    private boolean autoStartInstalledBundles = true;

    /**
     * Felix 框架附加配置（原样透传给 Felix 框架）。
     *
     * <p>用于需要自定义框架行为的场景，例如跨 bundle 共享应用侧接口包：</p>
     * <pre>
     * plugin:
     *   osgi:
     *     framework-properties:
     *       org.osgi.framework.system.packages.extra: com.example:api
     * </pre>
     */
    private Map<String, Object> frameworkProperties = new HashMap<>();

    /**
     * 目录热部署配置。
     */
    private Deploy deploy = new Deploy();

    /**
     * 运行时观测端点配置。
     */
    private Endpoint endpoint = new Endpoint();

    /**
     * Bean 桥接配置。
     */
    private Bridge bridge = new Bridge();

    /**
     * 源码扩展点配置。
     */
    private Source source = new Source();

    /**
     * 远程管理配置（SSH 或 Telnet，二选一）。
     */
    private Shell shell = new Shell();

    /**
     * 框架体征统计采样配置。
     */
    private Stats stats = new Stats();

    /**
     * jar 回收站配置。
     *
     * <p>删除部署目录中的 jar 时，先把文件连同元数据边车移入部署目录下的
     * 回收站目录，支持在控制台「车辆段」里查看、恢复与彻底清除；
     * 关闭后删除即物理删除，行为与旧版本一致。</p>
     */
    private Recycle recycle = new Recycle();

    /**
     * 外部 bundle SPI 自动发布配置。
     */
    private Spi spi = new Spi();

    /**
     * Bean 桥接配置。
     *
     * <p>控制 OSGi 服务投影为容器 Bean 时的验收与切换行为。</p>
     *
     * @author CH
     * @since 4.0.0.43
     */
    @Data
    public static class Bridge {

        /**
         * 是否启用 Bean 验收。
         *
         * <p>启用后，投影出的每个桥接定义都会经过 {@code BeanAcceptance} 验收器链；
         * 任一验收器拒绝即不纳入快照，拒绝原因可经
         * {@code OsgiBeanDefinitionRegister#getRejectedSnapshots()} 查询。</p>
         */
        private boolean acceptanceEnabled = true;

        /**
         * 是否在 bundle 生命周期变化时失效桥接快照。
         *
         * <p>关闭后将退化为「每次查询都重新枚举框架」，正确性不变但失去缓存收益；
         * 仅在调试切换行为时需要关闭。</p>
         */
        private boolean invalidateOnBundleLifecycle = true;

        /**
         * 是否为桥接 Bean 生成动态代理，使在用引用随 OSGi 变化自动切换。
         *
         * <p>启用后，容器外发的桥接 Bean 是「每次调用都解析当前生效服务」的
         * JDK 动态代理：bundle 升级/卸载或更高 {@code service.ranking} 的服务上线后，
         * 此前持有的引用自动打到新实例，无需重新注入或重新查找；
         * 服务被卸载且无替代时，业务方法抛 {@link IllegalStateException}。</p>
         *
         * <p>服务实例没有可代理接口时退化为直接引用（该实例不随 OSGi 切换）。
         * 关闭本开关即回到旧口径：桥接 Bean 是裸实例，仅「下次查询」能看到新服务。</p>
         */
        private boolean proxyEnabled = true;
    }

    /**
     * 运行时观测端点配置。
     *
     * <p>提供 {@code /osgi/**} 下的只读查询接口：框架统计、bundle 清单、
     * 服务注册表、单 bundle 详情。变更类操作不在此暴露。</p>
     *
     * @author CH
     * @since 4.0.0.43
     */
    @Data
    public static class Endpoint {

        /**
         * 是否启用观测端点。
         *
         * <p>默认关闭：端点会暴露插件符号名、版本、来源位置等信息，
         * 需由使用方显式开启并纳入既有的接口鉴权体系。</p>
         */
        private boolean enabled = false;

        /**
         * 是否允许变更类操作（上传安装、升级、启动、停止、卸载及批量操作）。
         *
         * <p>默认开启，但只有在 {@link #enabled}=true 时端点才存在。
         * 关闭后仍可在界面上查看 bundle 清单，但不能改；适用于只读观测场景。</p>
         */
        private boolean manageEnabled = true;

        /**
         * 单个 bundle 启动/停止操作的最长等待时间（秒）。
         *
         * <p>bundle 的 activator 可能在 start/stop 里阻塞（例如等待外部资源），
         * 同步调用会一直占用请求线程。超过该时长仍未返回即放弃等待并报错，
         * 避免一个坏插件拖死整个管理端点。注意：超时只是放弃等待并尽力中断工作线程，
         * 若框架底层调用不响应中断，该 bundle 的真实状态可能仍不确定。
         * &le;0 表示不限制。</p>
         */
        private int lifecycleTimeoutSeconds = 60;

        /**
         * 单个插件 jar 的上传大小上限（字节）。
         *
         * <p>超限直接拒绝上传，避免超大文件占满磁盘。仅约束本端点的插件 jar 上传，
         * 不影响全局 multipart 配置。&le;0 表示不限制。</p>
         */
        private long maxUploadBytes = 100L * 1024 * 1024;
    }

    /**
     * 源码扩展点配置：把源码目录变成可随时改代码即生效的 SPI 扩展点。
     *
     * <p>与 {@link Deploy} 的分工：{@code deploy} 面向<b>已构建好的 bundle jar</b>，
     * 需要先把源码打成 jar；本配置面向<b>源码本身</b>——放进目录即可，
     * 由 {@code javax.tools.JavaCompiler} 现场编译并注册进
     * {@code ServiceProvider} 扩展点表。适用于开发、测试与低频变更的扩展场景。</p>
     *
     * <p><b>默认开启</b>（2026-09-29 用户裁定：starter 的配置默认都是开启的）。
     * 未开启时该 starter 的其余能力仍照常工作，只是不做源码编译。</p>
     *
     * <p>注意：源码热重载会在运行期编译并加载外部代码。源码目录不存在时按
     * 「无源码」处理，不创建输出目录、也不注册任何扩展点，因此默认开启对
     * 不使用该能力的应用是无害的；一旦真的往目录里放源码，即等同于授权运行期加载代码。</p>
     *
     * <p>用法：</p>
     * <pre>
     * plugin:
     *   osgi:
     *     source:
     *       enabled: true      # 默认已是 true，通常无需显式写
     *       directory: ./osgi-source
     * </pre>
     * <p>目录里放一个带 {@code @SourceSpi} 的类即可（Java 或 Groovy）：</p>
     * <pre>{@code
     * @SourceSpi
     * public class MyExtensions {
     *     public final MySpi myExt = new MySpiImpl();   // 扩展名 = 字段名 myExt
     * }
     * }</pre>
     *
     * @author CH
     * @since 4.0.0.43
     */
    @Data
    public static class Source {

        /**
         * 是否启用源码扩展点热重载（默认开启）。
         */
        private boolean enabled = true;

        /**
         * 源码目录。
         *
         * <p>目录不存在时按「无源码」处理，不报错——配置项可以先开起来再放源码。</p>
         */
        private String directory = "./osgi-source";

        /**
         * 编译产物输出目录。
         *
         * <p>留空时取 {@code <directory>/../osgi-source-classes}。</p>
         *
         * <p><b>磁盘占用提示</b>：应用以 Spring Boot fat jar 部署时，编译器必须把
         * {@code BOOT-INF/lib} 里的依赖<b>抽出到该目录</b> 才能让 javac 读到它们
         * （javac 不认 {@code jar:nested:} 协议）。这意味着该目录会占用
         * <b>与一份 BOOT-INF/lib 相当的空间</b>——实测 eduai 约 685MB。
         * 抽取结果跨轮复用、不会重复增长；应用停止时自动清理。
         * 磁盘紧张的应用可把该目录指向大容量卷，或改用解压式部署
         * （非 fat jar，此时不产生这份开销）。</p>
         */
        private String outputDirectory = "";

        /**
         * 扫描间隔（毫秒）。
         *
         * <p>源码指纹（路径 + 大小 + 修改时间）未变化时不会触发编译。</p>
         */
        // [P3C A20 豁免]
        private long pollingInterval = 5000L;

        /**
         * 是否启用 Groovy 脚本扩展点。
         *
         * <p>开启后目录下的 {@code .groovy} 也会被加载，走既有
         * {@code ScriptProvider} SPI。若 classpath 中没有对应引擎实现，
         * 该部分会告警并降级为「只支持 Java 源码」，不影响启动。</p>
         */
        private boolean groovyEnabled = true;

        /**
         * 脚本引擎名，对应 {@code ScriptProvider#engineName}。
         */
        private String scriptEngine = "groovy";
    }

    /**
     * 目录热部署配置。
     *
     * <p>周期扫描部署目录，发现新的 bundle jar 后自动安装并启动；
     * 发现对应符号名的 bundle 已被移除时自动卸载，实现"改代码 → 丢 jar → 生效"的
     * 无重启迭代。适用于开发与测试环境，生产环境不建议开启。</p>
     *
     * @author CH
     * @since 4.0.0.43
     */
    @Data
    public static class Deploy {

        /**
         * 是否启用目录热部署。
         */
        private boolean enabled = false;

        /**
         * 部署目录。
         */
        private String directory = "./osgi-deploy";

        /**
         * 扫描间隔（毫秒）。
         */
        // [P3C A20 豁免]
        private long pollingInterval = 10000L;

        /**
         * 部署目录中 jar 移除后是否自动卸载对应 bundle。
         */
        private boolean uninstallOnRemoved = true;

        /**
         * 是否在移除对应 jar 后对已安装 bundle 执行原地升级。
         *
         * <p>关闭时移除 jar 会导致 bundle 停留在原版本；开启后
         * 以框架原生 update 替换为新内容，保留 bundle 标识与状态。</p>
         */
        private boolean updateOnChanged = false;
    }

    /**
     * 远程管理配置。
     *
     * <p>单一协议原则：shell 同时只启用一种远程连接方式。
     * 默认使用 SSH（端口 8122），安全性更高。</p>
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Shell {

        /**
         * 是否启用远程管理。
         */
        private boolean enabled = false;

        /**
         * 协议类型：{@code ssh}（默认）或 {@code telnet}。
         */
        private String protocol = "ssh";

        /**
         * 监听端口。
         * <ul>
         *   <li>SSH 协议：默认 8122</li>
         *   <li>Telnet 协议：默认 6666</li>
         * </ul>
         */
        private int port;

        /**
         * 绑定地址。
         */
        private String host = "127.0.0.1";

        /**
         * 认证用户名（SSH 模式）。
         */
        private String username = "";

        /**
         * 认证密码（SSH 模式）。
         */
        private String password = "";

        /**
         * 主机密钥文件路径（SSH 模式）。
         *
         * <p>留空时使用默认路径 {@code target/osgi-hostkey.ser}。
         * 首次启动会自动生成并持久化，后续复用同一密钥，避免客户端反复出现主机指纹变更告警。</p>
         */
        private String hostKey = "";

        /**
         * 是否允许空凭据登录。
         *
         * <p>默认关闭。此项开启后，配置为空的用户名与空密码即可登录远程管理通道，
         * 等同于无认证暴露，仅建议在完全受信任的本地调试环境临时开启。</p>
         */
        private boolean allowEmptyCredential = false;

        /**
         * 单次连接允许的认证失败次数上限，超过则断开连接。
         *
         * <p>经 MINA SSHD 的 {@code max-auth-requests} 属性生效：
         * 同一连接上认证请求达到该次数即被服务端主动断开。</p>
         */
        private int maxAuthAttempts = 3;

        /**
         * SSH shell 空闲会话超时（秒）。
         *
         * <p>会话持续无流量超过该时长即被服务端断开，防止半死连接长期占用线程。
         * &le;0 表示不设置，使用 SSHD 默认值（10 分钟）。</p>
         */
        private int idleTimeoutSeconds = 600;

        /**
         * 并发 SSH shell 会话上限。
         *
         * <p>超出上限的新会话会被立即拒绝并提示，防止远程管理通道被占满。
         * &le;0 表示不限制。</p>
         */
        private int maxSessions = 8;

        /**
         * 是否把 Apache SSHD 自身也作为 OSGI bundle 安装到框架内。
         *
         * <p><b>默认关闭，且开启前请先了解：</b>该能力在 sshd 2.13.2 下<b>无法真正工作</b>。
         * {@code sshd-sftp} 与 {@code sshd-scp} 声明 Import
         * {@code org.apache.sshd.client;version="[2.13.2,2.14)"}，
         * 而唯一导出该包的 {@code sshd-osgi} 对 {@code org.apache.sshd.client}
         * 的导出声明<b>不带 version</b>（版本视作 0.0.0），
         * 因此版本区间无法满足，两个 bundle 必定解析失败、停留在 {@code INSTALLED}。</p>
         *
         * <p>这与安装顺序无关——本项目实测已逐个 bundle 核对 MANIFEST 的
         * Import/Export-Package 得出。SSH 远程管理实际由应用层的
         * {@code GogoSshShellServer}（MINA SSHD 2.x 直连）提供，
         * <b>不依赖</b>这些 bundle，故默认不再安装，避免占用内存、产生解析失败告警、
         * 并让使用者误以为 SSH 走的是 OSGi 通道。</p>
         *
         * <p>保留该开关是为了给「拟以 OSGi 方式承载 SSH」的后续方案留出口：
         * 待上游补齐导出版本号后开启即可生效。</p>
         */
        private boolean installFrameworkSshdBundles = false;
    }

    /**
     * 框架体征统计采样配置。
     *
     * <p>采样器周期读取框架读数（bundle 总数 / 活跃数 / 服务类型数 / 自动启动失败数）
     * 写入内存环形缓冲，供前端趋势图展示；同时监听插件操作日志事件，
     * 按操作类型聚合成功 / 失败次数与平均耗时。只采样、不落库：
     * 宿主重启后历史清零，趋势从新积累。</p>
     *
     * <pre>
     * plugin:
     *   osgi:
     *     stats:
     *       enabled: true          # 是否开启体征采样（关闭后趋势图为空态）
     *       interval-seconds: 60   # 采样间隔（秒），最小 5
     *       history-size: 240      # 历史保留点数，范围 [10, 1440]
     * </pre>
     */
    @Data
    public static class Stats {

        /**
         * 是否开启框架体征周期采样。
         *
         * <p>默认开启。关闭后不再起采样线程，趋势接口返回空列表；
         * 操作统计不受影响（事件聚合是被动旁路，无独立线程）。</p>
         */
        private boolean enabled = true;

        /**
         * 采样间隔（秒）。
         *
         * <p>默认 60 秒；配置小于 5 时按 5 秒兜底，过密的采样对框架读数没有额外意义。</p>
         */
        private long intervalSeconds = 60;

        /**
         * 历史保留点数。
         *
         * <p>默认 240 个点（60 秒间隔约 4 小时趋势）；配置会被收敛到 [10, 1440] 区间，
         * 上限按分钟级采样封顶一天。</p>
         */
        private int historySize = 240;
    }

    /**
     * jar 回收站配置。
     *
     * <p>删除部署目录中的 jar 时，先把文件连同 {@code .meta.json} 元数据边车
     * 移入部署目录下的回收站目录；误删可在控制台恢复，确认不需要后再彻底清除。</p>
     *
     * <p>配置示例：</p>
     * <pre>
     * plugin:
     *   osgi:
     *     recycle:
     *       enabled: true          # 删除是否先进回收站（默认 true）
     *       dir-name: .recycle     # 回收站目录名（相对部署目录）
     * </pre>
     *
     * @author CH
     * @since 4.0.0.51
     */
    @Data
    public static class Recycle {

        /**
         * 是否启用回收站。
         *
         * <p>默认开启：删除 jar 先移入回收站目录，可恢复或彻底清除；
         * 关闭后删除即物理删除，不可恢复。</p>
         */
        private boolean enabled = true;

        /**
         * 回收站目录名（相对部署目录）。
         *
         * <p>默认 {@code .recycle}。热部署扫描器只扫描部署目录第一层的
         * {@code .jar} 文件，回收站子目录天然不会被当作插件加载。</p>
         */
        private String dirName = ".recycle";
    }

    /**
     * 外部 bundle SPI 自动发布配置。
     *
     * <p>控制外部 jar（无 Spring 容器参与）中的 {@code @Spi} 实现如何自动发布为
     * OSGi 服务：bundle 启动时发布、停止 / 卸载时反注册。发现顺序：优先读
     * {@code META-INF/extensions/<接口全限定名>} 索引，无索引退回
     * {@code @Spi} 注解类扫描。</p>
     *
     * <pre>
     * plugin:
     *   osgi:
     *     spi:
     *       enabled: true          # 是否启用（默认 true）
     *       max-scan-classes: 2000 # 无索引兜底扫描的最大 class 数
     * </pre>
     *
     * @author CH
     * @since 4.0.0.51
     */
    @Data
    public static class Spi {

        /**
         * 是否启用外部 bundle SPI 自动发布（默认开启）。
         */
        private boolean enabled = true;

        /**
         * 无 SPI 索引时兜底扫描的最大 class 数。
         *
         * <p>扫描发生在框架 bundle 事件线程上，超大 jar 全量扫描会拖慢
         * 事件派发；达到上限即停止并告警。仅对「没有 META-INF/extensions
         * 索引」的 jar 生效。</p>
         */
        private int maxScanClasses = 2000;
    }
}

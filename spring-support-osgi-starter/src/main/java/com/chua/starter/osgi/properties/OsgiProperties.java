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
         * 是否允许变更类操作（上传安装、升级、启动、停止、卸载）。
         *
         * <p>默认开启，但只有在 {@link #enabled}=true 时端点才存在。
         * 关闭后仍可在界面上查看 bundle 清单，但不能改；适用于只读观测场景。</p>
         */
        private boolean manageEnabled = true;
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
         */
        private int maxAuthAttempts = 3;

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
}

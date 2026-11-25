package com.chua.starter.server.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务器资产模块配置属性
 *
 * <p>配置前缀：{@code plugin.server.support}。完整 YAML 配置示例：</p>
 *
 * <pre>{@code
 * plugin:
 *   server:
 *     support:
 *       enabled: true                        # 服务信息面板"服务器资产"展示旗标（非模块开关）
 *       credential-key: change-me            # 凭据加密密钥（生产务必经环境变量/配置中心覆盖）
 *       session-idle-minutes: 30             # 远控会话空闲超时（分钟）
 *       exec-concurrency: 16                 # 批量执行最大并发主机数
 *       guacd:
 *         host: 127.0.0.1                    # guacd 主机地址
 *         port: 4822                         # guacd 端口
 *         client-host: 127.0.0.1:8082        # 浏览器侧回连地址 host:port（同域部署置空）
 *         drive-path: /opt/spring-support/drive          # RDP 磁盘映射挂载根目录
 *         recording-path: /opt/spring-support/recordings # 录屏输出目录
 *       telnet:
 *         port: 23                           # Telnet 默认端口
 *         timeout: 8000                      # Telnet 建连超时（毫秒）
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.server.support")
public class ServerProperties {

    /**
     * 服务信息面板上"服务器资产"模块的展示旗标。
     * <p>
     * 注意：这不是模块开关。本模块没有 {@code @ConditionalOnProperty}，
     * 置 false 不会卸载任何 Bean；monitor/project 等模块直接注入本模块服务，
     * 真要停用只能移除依赖。
     * </p>
     * <p>默认值：{@code true}（配置项缺省时回落到该初始值；布尔型无 ≤0/空回落概念，
     * 显式配置 false 即生效为 false）。
     * YAML 示例：{@code plugin.server.support.enabled: true}</p>
     */
    private boolean enabled = true;

    /**
     * guacd 配置
     * <p>YAML 前缀：{@code plugin.server.support.guacd}；各字段默认值见 {@link Guacd}。
     * YAML 示例：{@code plugin.server.support.guacd.host: 127.0.0.1}</p>
     */
    private Guacd guacd = new Guacd();

    /**
     * 凭据加密密钥（生产环境务必通过配置中心或环境变量注入）
     * <p>默认值：{@code server-support-default-credential-key}（配置项缺省时回落到该初始值；
     * 显式配置为空串则按空串生效，加解密器不做兜底）。该默认值随代码公开，
     * 用默认值上线等同于凭据可逆明文，生产必须覆盖为强随机密钥。
     * YAML 示例：{@code plugin.server.support.credential-key: ${SERVER_CREDENTIAL_KEY:change-me}}</p>
     */
    private String credentialKey = "server-support-default-credential-key";

    /**
     * 远程终端会话空闲超时（分钟），超时后由清理任务关闭
     * <p>默认值：{@code 30}（配置项缺省时回落到该初始值）。配置为 0 或负数时无代码级兜底：
     * 空闲扫描按该值直接比较，会话会被立即判定超时回收，请保持正整数。
     * YAML 示例：{@code plugin.server.support.session-idle-minutes: 30}</p>
     */
    // [P3C A20 豁免]
    private int sessionIdleMinutes = 30;

    /**
     * 批量执行最大并发主机数
     * <p>默认值：{@code 16}（配置项缺省时回落到该初始值）。批量执行线程池按
     * {@code min(exec-concurrency, 主机数)} 创建，0 或负数会导致线程池创建失败，请保持正整数。
     * YAML 示例：{@code plugin.server.support.exec-concurrency: 16}</p>
     */
    // [P3C A20 豁免]
    private int execConcurrency = 16;

    /**
     * Telnet 直连配置
     * <p>
     * Telnet 是「目标机自己开着的 23 端口」，平台只做 WS → TCP 透传，
     * 因此这里只留两个参数：没配端口时用哪个默认值、连不上时等多久。
     * </p>
     * <p>YAML 前缀：{@code plugin.server.support.telnet}；各字段默认值见 {@link Telnet}。</p>
     */
    private Telnet telnet = new Telnet();

    /**
     * Telnet 直连参数
     * @author CH
     * @since 4.0.0.51
     */
    @Data
    public static class Telnet {

        /**
         * 默认 Telnet 端口（主机台账没配远控端口时使用）
         * <p>默认值：{@code 23}（配置项缺省时回落到该初始值；无 ≤0 兜底，请配置 1~65535 的有效端口）。
         * YAML 示例：{@code plugin.server.support.telnet.port: 23}</p>
         */
        // [P3C A20 豁免]
        private int port = 23;

        /**
         * 建连超时（毫秒）
         * <p>默认值：{@code 8000}（配置项缺省时回落到该初始值；无 ≤0 兜底，请配置正整数）。
         * YAML 示例：{@code plugin.server.support.telnet.timeout: 8000}</p>
         */
        // [P3C A20 豁免]
        private int timeout = 8000;
    }

    /**
     * guacd 连接配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Guacd {

        /**
         * guacd 主机地址
         * <p>默认值：{@code 127.0.0.1}（配置项缺省时回落到该初始值）；运行期读取链路为
         * 配置表键 {@code guacd.host} 优先，缺失时才回落到本字段。
         * YAML 示例：{@code plugin.server.support.guacd.host: 127.0.0.1}</p>
         */
        private String host = "127.0.0.1";

        /**
         * guacd 端口
         * <p>默认值：{@code 4822}（配置项缺省时回落到该初始值）；运行期读取链路为
         * 配置表键 {@code guacd.port} 优先，缺失时才回落到本字段；无 ≤0 兜底，请配置 1~65535 的有效端口。
         * YAML 示例：{@code plugin.server.support.guacd.port: 4822}</p>
         */
        // [P3C A20 豁免]
        private int port = 4822;

        /**
         * guacd-客户端 网关地址（浏览器侧 guacamole 客户端回连后端 WS 隧道的 主机:端口；
         * 前端与后端同域部署时置空即可，页面自动使用 位置.主机）
         * <p>默认值：{@code 127.0.0.1:8082}（配置项缺省时回落到该初始值）；运行期读取链路为
         * 配置表键 {@code guacd.client.host} 优先，缺失时才回落到本字段。
         * YAML 示例：{@code plugin.server.support.guacd.client-host: 127.0.0.1:8082}</p>
         */
        private String clientHost = "127.0.0.1:8082";

        /**
         * RDP 磁盘映射挂载根目录（enable-Drive 时使用）
         * <p>默认值：{@code /opt/spring-support/drive}（配置项缺省时回落到该初始值）；
         * 需为 guacd 进程可读写的真实目录。
         * YAML 示例：{@code plugin.server.support.guacd.drive-path: /opt/spring-support/drive}</p>
         */
        private String drivePath = "/opt/spring-support/drive";

        /**
         * 屏幕录制文件输出目录
         * <p>默认值：{@code /opt/spring-support/recordings}（配置项缺省时回落到该初始值）；
         * 需为 guacd 进程可写的真实目录，录屏文件较大时应定期清理。
         * YAML 示例：{@code plugin.server.support.guacd.recording-path: /opt/spring-support/recordings}</p>
         */
        private String recordingPath = "/opt/spring-support/recordings";
    }
}

package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * tshark 运行时自动补齐配置。
 *
 * <p>背景：抓包页原先只做「tshark 在不在」的探测，不在就只提示运维自己装。
 * 但平台常常部署在干净的机器上（尤其是客户现场的内网机器），一句「请安装 Wireshark」
 * 等于把装机工作推回给人。这里让服务端在启动时用<b>虚拟线程</b>自行补齐：
 * 探测 → 下载便携包 → 解压 → 自检，全过程通过 SSE 把进度推给页面。</p>
 *
 * <p><b>为什么是便携包而不是 Wireshark 安装包</b>：安装包会写注册表、装 Npcap 内核驱动，
 * 需要管理员权限；而抓包页的「解析 / 统计 / 导入 pcap」并不需要驱动，只有<b>实时抓包</b>需要。
 * 所以默认只补 tshark 本体（解压到用户目录即可用），Npcap 单独探测并在页面上明确提示 ——
 * 不静默安装驱动，也不要求服务以管理员身份运行。</p>
 *
 * <p>完整配置示例（前缀 {@code plugin.monitor.tshark}，值均为默认值）：</p>
 * <pre>
 * plugin:
 *   monitor:
 *     tshark:
 *       bin: ""                        # 显式指定 tshark；空按 TSHARK_BIN → 本项 → 自动安装目录 → 系统路径 → PATH 探测
 *       auto-install: true             # 探测不到时自动下载便携包
 *       install-dir: ""                # 便携版解压目录；空回退 ~/spring-support/tshark
 *       package-url: ""                # 便携包下载地址；内网文件服务示例 http://10.0.0.5:8094/ms/tshark/package
 *       package-url-windows: ""        # Windows 专用地址，优先于 package-url
 *       package-url-linux: ""          # Linux 专用地址，优先于 package-url
 *       package-path: ""               # 本地便携包路径（离线部署），优先于 package-url
 *       package-path-windows: ""       # Windows 专用本地包路径
 *       package-path-linux: ""         # Linux 专用本地包路径
 *       timeout-seconds: 600           # 下载 + 解压 + 自检总超时（秒）
 *       tls-keylog-file: ""            # TLS keylog 文件（NSS key log 格式）
 *       json-restore: true             # 解析 pcap 时回填 rawData / restoredText
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Component
@ConfigurationProperties(prefix = "plugin.monitor.tshark")
public class TsharkInstallProperties {

    /**
     * 显式指定 tshark 可执行文件路径。
     *
     * <p>留空时按「环境变量 {@code TSHARK_BIN} → 本项 → 自动安装目录 → 系统常见安装路径 → PATH」
     * 依次探测，命中即用。已有 Wireshark 安装的机器因此不需要任何配置。</p>
     *
     * <p>默认值：空字符串（{@code ""}），即走上述探测链。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       bin: ""
     * </pre>
     */
    private String bin = "";

    /**
     * 探测不到时是否自动下载并解压便携版。
     *
     * <p>关闭后只做探测与提示，不发生任何网络下载。</p>
     *
     * <p>默认值：{@code true}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       auto-install: true
     * </pre>
     */
    private boolean autoInstall = true;

    /**
     * 便携版解压目录。
     *
     * <p>留空用 {@code ~/spring-support/tshark}。不用 {@code /opt} 的原因同抓包目录：
     * 平台多以普通用户运行，{@code /opt} 不可写，会在真实部署里直接失败。</p>
     *
     * <p>默认值：空字符串（{@code ""}），回落 {@code ~/spring-support/tshark}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       install-dir: ""
     * </pre>
     */
    private String installDir = "";

    /**
     * 便携包下载地址（zip：tshark 可执行文件与其依赖 DLL / so）。
     *
     * <p>留空且未配置 {@link #packagePath} 时，启动自检会把状态标成「缺少安装包」并给出
     * 明确的处理办法，而不是静默失败。</p>
     *
     * <p>默认值：空字符串（{@code ""}）；Windows/Linux 专用地址未配时回退本项。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       package-url: ""
     * </pre>
     */
    private String packageUrl = "";

    /**
     * Windows 专用便携包地址，优先于 {@link #packageUrl}。
     *
     * <p>同一个部署包里可能同时带 Windows 与 Linux 的产物，用平台专用配置就不必为
     * 不同系统准备两份 application.yml。</p>
     *
     * <p>默认值：空字符串（{@code ""}），回落到 {@link #packageUrl}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       package-url-windows: ""
     * </pre>
     */
    private String packageUrlWindows = "";

    /**
     * Linux 专用便携包地址，优先于 {@link #packageUrl}。
     *
     * <p>支持 {@code .tar.gz} / {@code .tar.xz} / {@code .deb}（deb 会取出其中的
     * {@code data.tar.*} 再解）；Wireshark 官方没有 Linux 便携 zip，通常用发行版的
     * deb 或自建 tar 包。</p>
     *
     * <p>默认值：空字符串（{@code ""}），回落到 {@link #packageUrl}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       package-url-linux: ""
     * </pre>
     */
    private String packageUrlLinux = "";

    /**
     * 本地便携包路径，优先于 {@link #packageUrl}。
     *
     * <p>内网离线部署时把 zip 放到机器上，填这里即可，服务端不再访问外网。</p>
     *
     * <p>默认值：空字符串（{@code ""}）；Windows/Linux 专用路径未配时回退本项。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       package-path: ""
     * </pre>
     */
    private String packagePath = "";

    /**
     * Windows 专用本地包路径，优先于 {@link #packagePath}。
     *
     * <p>默认值：空字符串（{@code ""}），回落到 {@link #packagePath}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       package-path-windows: ""
     * </pre>
     */
    private String packagePathWindows = "";

    /**
     * Linux 专用本地包路径，优先于 {@link #packagePath}。
     *
     * <p>默认值：空字符串（{@code ""}），回落到 {@link #packagePath}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       package-path-linux: ""
     * </pre>
     */
    private String packagePathLinux = "";

    /**
     * 单次下载 + 解压 + 自检的总体超时（秒）。
     *
     * <p>默认值：{@code 600}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       timeout-seconds: 600
     * </pre>
     */
    // [P3C A20 豁免]
    private int timeoutSeconds = 600;

    /**
     * TLS 会话密钥日志文件（NSS key log 格式，如 {@code D:\keys\sslkeys.log}）。
     *
     * <p>配置后 tshark 会带 {@code -o tls.keylog_file:<path>}，把 TLS 里的 HTTP/2、
     * MSRP、SMPP 等明文解出来继续走协议还原。不配置则不传该参数，抓包行为与以前一致。</p>
     *
     * <p><b>安全提醒</b>：keylog 文件本身就是解密钥匙，只能用于自有环境的排障，
     * 不要在生产机器上长期开启，也不要跟抓包文件放在一起。</p>
     *
     * <p>默认值：空字符串（{@code ""}），即不传 {@code -o tls.keylog_file:} 参数。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       tls-keylog-file: ""
     * </pre>
     */
    private String tlsKeylogFile = "";

    /**
     * 是否在解析 pcap 时，额外启用 tshark json 通道回填 {@code rawData}（完整协议层 JSON）
     * 与 {@code restoredText}（协议还原出的明文）。
     *
     * <p>开启后，{@code -T fields} 保障的骨架落成之后，服务端会再跑一次 {@code tshark -T json -x}：
     * 逐包取出完整 JSON（写入 {@code rawData}，包含 payload 展开的十六进制），
     * 再调用 {@code PacketParserService} 把每一层协议的报文还原成明文写进报文表
     * {@code ms_tshark_packet_restored} 列。</p>
     *
     * <p>这不是只有 HTTP 才有的能力——DNS、TLS、SSH、FTP、SMTP、MQTT、数据库协议...
     * 所有带还原器的协议都会有各自的还原结果。页面上「协议还原」这块，离线导入一样能显示。</p>
     *
     * <p>代价是一次额外完整解析（文件越大越明显）。生产侧只要本地有 tshark 就一直开着；
     * 不需要协议还原时可以关。</p>
     *
     * <p>默认值：{@code true}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       json-restore: true
     * </pre>
     *
     * @since 4.0.0.52
     */
    private boolean jsonRestore = true;

    /**
     * 是否启用 tshark json 通道回填。
     *
     * <p>由配置 {@code plugin.monitor.tshark.json-restore} 驱动：
     * 关闭时只做骨架落库（{@code -T fields}），开启时按帧号回填 {@code rawData} / {@code restoredText}。</p>
     *
     * <p>默认值：{@code true}（跟随 {@link #jsonRestore} 的初始值）。</p>
     *
     * @return 是否启用 json 通道回填
     */
    public boolean restoreJsonEnabled() {
        return this.jsonRestore;
    }
}

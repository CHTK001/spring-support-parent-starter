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
 * <p>配置示例：</p>
 * <pre>
 * plugin:
 *   monitor:
 *     tshark:
 *       auto-install: true
 *       package-url: http://10.0.0.5:8094/ms/tshark/package
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
     */
    private String bin = "";

    /**
     * 探测不到时是否自动下载并解压便携版。
     *
     * <p>关闭后只做探测与提示，不发生任何网络下载。</p>
     */
    private boolean autoInstall = true;

    /**
     * 便携版解压目录。
     *
     * <p>留空用 {@code ~/spring-support/tshark}。不用 {@code /opt} 的原因同抓包目录：
     * 平台多以普通用户运行，{@code /opt} 不可写，会在真实部署里直接失败。</p>
     */
    private String installDir = "";

    /**
     * 便携包下载地址（zip：tshark 可执行文件与其依赖 DLL / so）。
     *
     * <p>留空且未配置 {@link #packagePath} 时，启动自检会把状态标成「缺少安装包」并给出
     * 明确的处理办法，而不是静默失败。</p>
     */
    private String packageUrl = "";

    /**
     * 本地便携包路径，优先于 {@link #packageUrl}。
     *
     * <p>内网离线部署时把 zip 放到机器上，填这里即可，服务端不再访问外网。</p>
     */
    private String packagePath = "";

    /**
     * 单次下载 + 解压 + 自检的总体超时（秒）。
     */
    private int timeoutSeconds = 600;

    /**
     * 是否在解析 pcap 时，额外启用 tshark json 通道回填 {@code rawData}（完整协议层 JSON）
     * 与 {@code restoredText}（协议还原出的明文）。
     *
     * <p>开启后，{-T fields} 保障的骨架落成之后，服务端会再跑一次 {@code tshark -T json -x}：
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
      * @since 4.0.0.52
      */
    private boolean jsonRestore = true;

    /**
     * 是否启用 tshark json 通道回填。
     *
     * <p>由配置 {@code plugin.monitor.tshark.json-restore} 驱动：
     * 关闭时只做骨架落库（{-T fields}），开启时按帧号回填 {@code rawData} / {@code restoredText}。</p>
     */
    public boolean restoreJsonEnabled() {
        return this.jsonRestore;
    }
}

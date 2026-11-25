package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * tshark 抓包数据的保留与清理策略。
 *
 * <p>抓包文件（尤其是按天 7x24 小时跑的实时会话）落地后只有分析价值，
 * 不会长期保存。这里提供「过期多久清」和「磁盘空间涨到多少清」两把尺子，
 * 过期清理与空间清理各成一条独立规则，任一条满足都删。</p>
 *
 * <p>清理范围：会话 + 报文 + 原始 pcap + 抓包日志 + 会话自己的 SQLite 报文库，
 * 一起删，不留碎片。</p>
 *
 * <p>完整配置示例（前缀 {@code plugin.monitor.tshark.retention}，值均为默认值）：</p>
 * <pre>
 * plugin:
 *   monitor:
 *     tshark:
 *       retention:
 *         days: 7        # 抓包文件保留天数上限
 *         size-mb: 512   # 抓包目录空间上限（MB）
 *         enabled: true  # 关闭后只统计空间、不删文件
 * </pre>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Data
@Component
@ConfigurationProperties(prefix = "plugin.monitor.tshark.retention")
public class TsharkRetentionProperties {

    /**
     * 抓包文件保留天数上限，超过且清理开启时删除。
     *
     * <p>默认值：{@code 7}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       retention:
     *         days: 7
     * </pre>
     */
    private int days = 7;

    /**
     * 抓包目录空间上限（MB），任一规则满足即清理。
     *
     * <p>默认值：{@code 512}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       retention:
     *         size-mb: 512
     * </pre>
     */
    // [P3C A20 豁免]
    private int sizeMb = 512;

    /**
     * 是否启用自动清理。
     *
     * <p>默认值：{@code true}；关闭时只统计空间、不删文件，
     * 真实的清理交给系统本身（避免误删别的服务数据）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     tshark:
     *       retention:
     *         enabled: true
     * </pre>
     */
    private boolean enabled = true;
}

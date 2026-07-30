package com.chua.starter.panel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Nginx 大屏统计数据。
 * 数据来源：stub_status + access.log 分析。
 *
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Nginx 大屏统计数据")
public class NginxDashboardStats {

    // ── stub_status 数据 ──────────────────────────────────────────────────────

    /** 当前活跃连接数。 */
    @Schema(description = "当前活跃连接数")
    private long activeConnections;

    /** 总接受连接数。 */
    @Schema(description = "总接受连接数")
    private long accepts;

    /** 总处理连接数。 */
    @Schema(description = "总处理连接数")
    private long handled;

    /** 总请求数。 */
    @Schema(description = "总请求数")
    private long requests;

    /** 正在读取请求头的连接数。 */
    @Schema(description = "正在读取请求头的连接数")
    private long reading;

    /** 正在发送响应的连接数。 */
    @Schema(description = "正在发送响应的连接数")
    private long writing;

    /** 等待中的连接数（keep-alive）。 */
    @Schema(description = "等待中的连接数（keep-alive）")
    private long waiting;

    /** stub_status 是否可用。 */
    @Builder.Default
    @Schema(description = "stub_status 是否可用")
    private boolean stubStatusAvailable = false;

    // ── access.log 分析 ───────────────────────────────────────────────────────

    /** Top N IP 及其请求数，key=IP，value=请求数。 */
    @Schema(description = "Top IP 统计")
    private Map<String, Long> topIps;

    /** 最近 N 条访问记录（原始日志行）。 */
    @Schema(description = "最近访问记录")
    private List<String> recentRequests;

    /** 分析的日志行总数。 */
    @Schema(description = "分析的日志行总数")
    private long analyzedLines;

    /** 独立 IP 数量。 */
    @Schema(description = "独立 IP 数量")
    private long uniqueIps;

    /** 每分钟平均请求数（基于最近日志估算）。 */
    @Schema(description = "每分钟平均请求数")
    private double requestsPerMinute;

    /** 最近 24 小时请求数。 */
    @Schema(description = "最近 24 小时请求数")
    private long dailyRequests;

    /** 最近 24 小时独立 IP 数。 */
    @Schema(description = "最近 24 小时独立 IP 数")
    private long dailyUniqueIps;

    /** 最近 7 天请求数。 */
    @Schema(description = "最近 7 天请求数")
    private long weeklyRequests;

    /** 最近 7 天独立 IP 数。 */
    @Schema(description = "最近 7 天独立 IP 数")
    private long weeklyUniqueIps;

    /** 最近 30 天请求数。 */
    @Schema(description = "最近 30 天请求数")
    private long monthlyRequests;

    /** 最近 30 天独立 IP 数。 */
    @Schema(description = "最近 30 天独立 IP 数")
    private long monthlyUniqueIps;

    // ── 服务状态 ──────────────────────────────────────────────────────────────

    /** nginx 版本。 */
    @Schema(description = "nginx 版本")
    private String version;

    /** 主进程 PID。 */
    @Schema(description = "主进程 PID")
    private String pid;

    /** 服务是否运行中。 */
    @Schema(description = "服务是否运行中")
    private boolean running;

    /** 配置检查是否通过。 */
    @Schema(description = "配置检查是否通过")
    private boolean configOk;
}

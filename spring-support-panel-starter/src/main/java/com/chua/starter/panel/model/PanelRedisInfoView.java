package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Redis 服务器信息视图。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "Redis 服务器信息视图")
public class PanelRedisInfoView {
    /** Redis 版本。 */
    @Schema(description = "Redis 版本")
    private String version;
    /** Redis 模式。 */
    @Schema(description = "Redis 模式")
    private String mode;
    /** 已使用内存。 */
    @Schema(description = "已使用内存")
    private long usedMemory;
    /** 人类可读内存值。 */
    @Schema(description = "人类可读内存值")
    private long usedMemoryHuman;
    /** 已连接客户端数。 */
    @Schema(description = "已连接客户端数")
    private long connectedClients;
    /** 已处理命令总数。 */
    @Schema(description = "已处理命令总数")
    private long totalCommandsProcessed;
    /** 运行时长（秒）。 */
    @Schema(description = "运行时长（秒）")
    private long uptimeInSeconds;
    /** 数据库总数。 */
    @Schema(description = "数据库总数")
    private int totalDatabases;
    /** 各数据库 key 数量。 */
    @Schema(description = "各数据库 key 数量")
    private Map<String, Long> dbKeyCounts;
    /** 原始 info 信息。 */
    @Schema(description = "原始 info 信息")
    private Map<String, String> rawInfo;
}

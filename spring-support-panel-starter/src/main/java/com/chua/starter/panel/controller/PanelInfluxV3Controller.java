package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.InfluxV3PanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * InfluxDB 3.x 面板控制器。
 * 路由：/v1/panel/influxv3/{connectionId}/*
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/influxv3")
@Tag(name = "Panel Influx3", description = "Panel InfluxDB 3.x 管理接口")
public class PanelInfluxV3Controller {

    private final InfluxV3PanelService influxV3PanelService;

    /**
     * 打开 InfluxDB 3.x 连接。
     *
     * @param body 连接参数
     * @return 连接标识
     */
    @Operation(summary = "打开连接")
    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 8086)));
        String token = String.valueOf(body.getOrDefault("password", ""));
        String defaultDatabase = String.valueOf(body.getOrDefault("database", ""));
        try {
            return ReturnResult.ok(influxV3PanelService.open(host, port, token, defaultDatabase));
        } catch (Exception e) {
            return ReturnResult.error("InfluxDB 3.x 连接失败: " + e.getMessage());
        }
    }

    /**
     * 关闭指定连接。
     *
     * @param connectionId 连接标识
     * @return 关闭结果
     */
    @Operation(summary = "关闭连接")
    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        influxV3PanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    /**
     * 列出数据库列表。
     *
     * @param connectionId 连接标识
     * @return 数据库列表
     */
    @Operation(summary = "列出数据库")
    @GetMapping("/{connectionId}/databases")
    public ReturnResult<List<String>> listDatabases(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(influxV3PanelService.listDatabases(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取数据库列表失败: " + e.getMessage());
        }
    }

    /**
     * 列出指定数据库下的表。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @return 表列表
     */
    @Operation(summary = "列出数据表")
    @GetMapping("/{connectionId}/tables")
    public ReturnResult<List<String>> listTables(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "数据库名称") @RequestParam(required = false, defaultValue = "") String database) {
        try {
            return ReturnResult.ok(influxV3PanelService.listTables(connectionId, database));
        } catch (Exception e) {
            return ReturnResult.error("获取表列表失败: " + e.getMessage());
        }
    }

    /**
     * 执行 SQL 查询。
     *
     * @param connectionId 连接标识
     * @param body 查询参数
     * @return 查询结果
     */
    @Operation(summary = "执行 SQL 查询")
    @PostMapping("/{connectionId}/query")
    public ReturnResult<List<Map<String, Object>>> query(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String database = String.valueOf(body.getOrDefault("database", ""));
        String sql = String.valueOf(body.getOrDefault("query", ""));
        try {
            return ReturnResult.ok(influxV3PanelService.query(connectionId, database, sql));
        } catch (Exception e) {
            return ReturnResult.error("SQL 查询失败: " + e.getMessage());
        }
    }

    /**
     * 写入 Line Protocol 数据。
     *
     * @param connectionId 连接标识
     * @param body 写入参数
     * @return 写入结果
     */
    @Operation(summary = "写入数据")
    @PostMapping("/{connectionId}/write")
    public ReturnResult<Void> write(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String database = String.valueOf(body.getOrDefault("database", ""));
        String lineProtocol = String.valueOf(body.getOrDefault("lineProtocol", ""));
        try {
            influxV3PanelService.write(connectionId, database, lineProtocol);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("写入失败: " + e.getMessage());
        }
    }

    /**
     * 获取 InfluxDB 3.x 健康状态。
     *
     * @param connectionId 连接标识
     * @return 健康状态
     */
    @Operation(summary = "查询健康状态")
    @GetMapping("/{connectionId}/health")
    public ReturnResult<Map<String, Object>> health(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(influxV3PanelService.health(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取健康状态失败: " + e.getMessage());
        }
    }
}

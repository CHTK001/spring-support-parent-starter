package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.InfluxPanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * InfluxDB 2.x 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/influx")
@Tag(name = "Panel Influx2", description = "Panel InfluxDB 2.x 管理接口")
public class PanelInfluxController {

    private final InfluxPanelService influxPanelService;

    /**
     * 打开 InfluxDB 2.x 连接。
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
        String org = String.valueOf(body.getOrDefault("org", ""));
        String bucket = String.valueOf(body.getOrDefault("bucket", ""));
        try {
            return ReturnResult.ok(influxPanelService.open(host, port, token, org, bucket));
        } catch (Exception e) {
            return ReturnResult.error("InfluxDB 连接失败: " + e.getMessage());
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
        influxPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    /**
     * 列出 Bucket 集合。
     *
     * @param connectionId 连接标识
     * @return Bucket 列表
     */
    @Operation(summary = "列出 Bucket")
    @GetMapping("/{connectionId}/buckets")
    public ReturnResult<List<String>> listBuckets(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(influxPanelService.listBuckets(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取 Bucket 失败: " + e.getMessage());
        }
    }

    /**
     * 列出指定 Bucket 下的 Measurement。
     *
     * @param connectionId 连接标识
     * @param bucket bucket 名称
     * @return Measurement 列表
     */
    @Operation(summary = "列出 Measurement")
    @GetMapping("/{connectionId}/measurements")
    public ReturnResult<List<String>> listMeasurements(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "Bucket 名称") @RequestParam String bucket) {
        try {
            return ReturnResult.ok(influxPanelService.listMeasurements(connectionId, bucket));
        } catch (Exception e) {
            return ReturnResult.error("获取 Measurement 失败: " + e.getMessage());
        }
    }

    /**
     * 执行 Flux 查询。
     *
     * @param connectionId 连接标识
     * @param body 查询参数
     * @return 查询结果
     */
    @Operation(summary = "执行 Flux 查询")
    @PostMapping("/{connectionId}/query")
    public ReturnResult<List<Map<String, Object>>> query(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String fluxQuery = String.valueOf(body.getOrDefault("query", ""));
        try {
            return ReturnResult.ok(influxPanelService.query(connectionId, fluxQuery));
        } catch (Exception e) {
            return ReturnResult.error("Flux 查询失败: " + e.getMessage());
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
        String bucket = String.valueOf(body.getOrDefault("bucket", ""));
        String lineProtocol = String.valueOf(body.getOrDefault("lineProtocol", ""));
        try {
            influxPanelService.write(connectionId, bucket, lineProtocol);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("写入失败: " + e.getMessage());
        }
    }

    /**
     * 获取 InfluxDB 健康状态。
     *
     * @param connectionId 连接标识
     * @return 健康状态
     */
    @Operation(summary = "查询健康状态")
    @GetMapping("/{connectionId}/health")
    public ReturnResult<Map<String, Object>> health(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(influxPanelService.health(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取健康状态失败: " + e.getMessage());
        }
    }
}

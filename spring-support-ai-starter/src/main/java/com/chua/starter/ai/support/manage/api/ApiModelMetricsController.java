package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.service.ModelMetricsSnapshotService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
* 模型价格/指标快照查询与刷新端点。
*
* <p>前端在「模型定价」面板打开时调用 {@code GET /api/model-metrics} 获取当前快照，
* 同时通过 webSocket 订阅 {@code model.metrics.push} 主题接收后台定时推送的增量更新。</p>
*
* @since 4.0.0.42
* @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiModelMetricsController {

    /**
    * 全局设置分组（与 ModelMetricsPushTask 一致）
    */
    private static final String GROUP = "global";

    /**
    * 指标推送开关字段
    */
    private static final String FIELD_ENABLED = "enable_model_metrics_push";

    /**
    * 指标推送间隔字段（秒）
    */
    private static final String FIELD_INTERVAL_SEC = "model_metrics_push_interval_sec";

    /**
    * 默认推送间隔（秒）
    */
    private static final int DEFAULT_INTERVAL_SEC = 300;

    /**
    * 最小推送间隔（秒）
    */
    private static final int MIN_INTERVAL_SEC = 30;

    /**
    * 模型价格/指标快照服务
    */
    private final ModelMetricsSnapshotService snapshotService;

    /**
    * 全局设置读取器
    */
    private final GlobalSettingReader settingReader;

    /**
    * 全局设置写入服务
    */
    private final SysAiGlobalSettingService globalSettingService;

    /**
    * 官方定价同步服务
    */
    private final SysAiPricingService pricingService;

    /**
    * 获取当前模型价格/指标快照。
    *
    * <p>优先从 Redis 读取上次构建的快照；Redis 无缓存时现场构建一份返回。</p>
    *
    * @return 快照数据
    */
    @GetMapping("/model-metrics")
    public ApiResult<Map<String, Object>> getSnapshot() {
        return ApiResult.ok(snapshotService.latestSnapshot());
    }

    /**
    * 手动触发模型价格/指标快照刷新并推送 webSocket。
    *
    * @return 刷新后的快照
    */
    @PostMapping("/model-metrics/refresh")
    public ApiResult<Map<String, Object>> refreshSnapshot() {
        log.info("[ModelMetrics] 手动触发快照刷新");
        return ApiResult.ok(snapshotService.buildAndPushSnapshot());
    }

    /**
    * 读取模型指标推送配置。
    *
    * <p>从 {@code sys_ai_global_setting} 的 {@code global} 分组读取：
    * {@code enable_model_metrics_push}（默认 true）与
    * {@code model_metrics_push_interval_sec}（默认 300，最小 30）。</p>
    *
    * @return {enabled, intervalSec}
    */
    @GetMapping("/model-metrics/config")
    public ApiResult<Map<String, Object>> getConfig() {
        boolean enabled = settingReader.getBool(GROUP, FIELD_ENABLED, true);
        int intervalSec = settingReader.getInt(GROUP, FIELD_INTERVAL_SEC, DEFAULT_INTERVAL_SEC);
        if (intervalSec < MIN_INTERVAL_SEC) {
            intervalSec = MIN_INTERVAL_SEC;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("intervalSec", intervalSec);
        return ApiResult.ok(result);
    }

    /**
    * 保存模型指标推送配置。
    *
    * <p>接收 {@code {enabled, intervalSec}}，写入 {@code global} 分组；
    * {@code intervalSec} 小于 30 时自动钳制为 30。</p>
    *
    * @param body 配置 {@code {enabled, intervalSec}}
    * @return 是否保存成功
    */
    @PutMapping("/model-metrics/config")
    public ApiResult<Boolean> saveConfig(@RequestBody Map<String, Object> body) {
        if (body != null) {
            if (body.containsKey("enabled")) {
                boolean enabled = parseBool(body.get("enabled"), true);
                globalSettingService.upsertValue(GROUP, FIELD_ENABLED, String.valueOf(enabled), "模型指标推送开关");
            }
            if (body.containsKey("intervalSec")) {
                int intervalSec = parseInt(body.get("intervalSec"), DEFAULT_INTERVAL_SEC);
                if (intervalSec < MIN_INTERVAL_SEC) {
                    intervalSec = MIN_INTERVAL_SEC;
                }
                globalSettingService.upsertValue(GROUP, FIELD_INTERVAL_SEC, String.valueOf(intervalSec), "模型指标推送间隔（秒）");
            }
        }
        log.info("[ModelMetrics] 保存推送配置: {}", body);
        return ApiResult.ok(true);
    }

    /**
    * 手动触发官方定价同步到 {@code sys_ai_pricing} 表。
    *
    * <p>清空旧数据并全量插入官方定价，供用量统计金额回退使用。</p>
    *
    * @return {synced, syncedAt}
    */
    @PostMapping("/model-metrics/sync-pricing")
    public ApiResult<Map<String, Object>> syncPricing() {
        log.info("[ModelMetrics] 手动触发官方定价同步");
        pricingService.syncOfficialPricing();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("synced", true);
        result.put("syncedAt", Instant.now().toString());
        return ApiResult.ok(result);
    }

    /**
    * 宽松解析布尔值。
    *
    * @param value        原始值
    * @param defaultValue 默认值
    * @return 布尔值
    */
    private static boolean parseBool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "on".equalsIgnoreCase(s);
    }

    /**
    * 宽松解析整数。
    *
    * @param value        原始值
    * @param defaultValue 默认值
    * @return 整数
    */
    private static int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

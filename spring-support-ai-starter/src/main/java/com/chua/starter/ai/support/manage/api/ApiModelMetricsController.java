package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.service.ModelMetricsSnapshotService;
import com.chua.starter.ai.support.manage.service.SysAiModelMetricsService;
import com.chua.starter.ai.support.manage.store.SysAiModelMetrics;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiPricingService;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型指标库查询、全量同步、配置与手动修改端点。
 *
 * <p>设置页主数据走 {@code GET /api/model-metrics/table}（分页，全量指标库）；
 * {@code POST /api/model-metrics/refresh} 触发指标源全量同步落库；
 * {@code PUT /api/model-metrics/manual} 手动修改/锁定指标表字段。</p>
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
     * 全局设置分组
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
     * 全量指标库服务
     */
    private final SysAiModelMetricsService metricsService;

    /**
     * 模型快照服务（兼容旧快照）
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
     * 分页查询全量指标库。
     *
     * @param page    当前页
     * @param size    每页条数
     * @param keyword 模型名/厂商关键字（可空）
     * @return 分页数据
     */
    @GetMapping("/model-metrics/table")
    public ApiResult<Map<String, Object>> getTable(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "20") long size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        Page<SysAiModelMetrics> p = metricsService.page(page, size, keyword);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", p.getTotal());
        result.put("current", p.getCurrent());
        result.put("size", p.getSize());
        result.put("records", p.getRecords());
        return ApiResult.ok(result);
    }

    /**
     * 获取旧版快照（兼容）。
     *
     * @return 快照数据
     */
    @GetMapping("/model-metrics")
    public ApiResult<Map<String, Object>> getSnapshot() {
        return ApiResult.ok(snapshotService.latestSnapshot());
    }

    /**
     * 手动触发指标源全量同步落库，并重建兼容快照。
     *
     * <p>全量重写共享指标表，仅限管理员。</p>
     *
     * @return 同步条数与快照
     */
    @Permission("admin")
    @PostMapping("/model-metrics/refresh")
    public ApiResult<Map<String, Object>> refreshSnapshot() {
        log.info("[ModelMetrics] 手动触发全量同步");
        int synced = metricsService.syncAll();
        Map<String, Object> snapshot = snapshotService.buildAndPushSnapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("synced", synced);
        result.put("snapshot", snapshot);
        return ApiResult.ok(result);
    }

    /**
     * 读取指标推送配置。
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
     * 保存指标推送配置。
     *
     * <p>写入 {@code sys_ai_global_setting}，对全部用户生效，仅限管理员。</p>
     *
     * @param body 配置 {@code {enabled, intervalSec}}
     * @return 是否保存成功
     */
    @Permission("admin")
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
     * 手动触发官方定价同步。
     *
     * <p>重写共享定价表，仅限管理员。</p>
     *
     * @return {synced, syncedAt}
     */
    @Permission("admin")
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
     * 手动修改指标库字段并锁定。
     *
     * <p>请求体：{@code {key, values:{字段:值}, locked:[字段...]}}。
     * 落点为全量指标表 {@code sys_ai_model_metrics}；被锁定的字段在后续全量同步时不覆盖。
     * 审计操作人取当前登录身份，不接受请求体传入。仅限管理员。</p>
     *
     * @param body 请求体
     * @return 锁定字段、修改时间与更新行
     */
    @Permission("admin")
    @PutMapping("/model-metrics/manual")
    public ApiResult<Map<String, Object>> saveManual(@RequestBody Map<String, Object> body) {
        if (body == null) {
            return manualError("400", "请求体为空");
        }
        String key = body.get("key") == null ? null : body.get("key").toString().trim();
        if (key == null || key.isEmpty()) {
            return manualError("400", "key 不能为空");
        }

        Map<String, BigDecimal> values = new LinkedHashMap<>();
        if (body.get("values") instanceof Map<?, ?> vm) {
            for (Map.Entry<?, ?> en : vm.entrySet()) {
                if (en.getKey() == null) {
                    continue;
                }
                BigDecimal bd = parseBigDecimal(en.getValue());
                if (bd != null) {
                    values.put(en.getKey().toString(), bd);
                }
            }
        }

        List<String> locked = new ArrayList<>();
        if (body.get("locked") instanceof List<?> lk) {
            for (Object o : lk) {
                if (o != null && !locked.contains(o.toString().trim())) {
                    locked.add(o.toString().trim());
                }
            }
        }

        String operator = AiUserContext.currentUsername();
        if (operator == null) {
            operator = AiUserContext.currentUserId();
        }

        try {
            SysAiModelMetrics updated = metricsService.applyManual(key, values, locked, operator);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("key", key);
            result.put("locked", updated.getManualLocked());
            result.put("updatedAt", updated.getManualUpdatedAt());
            result.put("row", updated);
            log.info("[ModelMetrics] 手动修改指标 key={} locked={}", key, updated.getManualLocked());
            return ApiResult.ok(result);
        } catch (IllegalArgumentException e) {
            return manualError("404", e.getMessage());
        }
    }

    /**
     * 构造失败响应。
     * @param code 字符串
     * @param msg 字符串
     * @return 返回值
    */
    private static ApiResult<Map<String, Object>> manualError(String code, String msg) {
        return new ApiResult<>(false, null, code, msg, null, null);
    }

    /**
     * 解析 BigDecimal。
     * @param value 对象
     * @return 返回值
    */
    private static BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析布尔值。
     * @param defaultValue 布尔值
     * @param value 对象
     * @return 返回值
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
     * 解析整数。
     * @param defaultValue 整数
     * @param value 对象
     * @return 返回值
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

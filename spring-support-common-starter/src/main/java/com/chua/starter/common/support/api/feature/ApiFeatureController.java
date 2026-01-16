package com.chua.starter.common.support.api.feature;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.api.annotations.ApiInternal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API 功能开关管理控制器
 * <p>
 * 提供功能开关的查询和管理接口。
 * 此接口为内部接口，仅允许内网IP或白名单服务调用，不受 OAuth 鉴权控制。
 * </p>
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
@RestController
@RequestMapping("${plugin.api.feature.path:/api/features}")
@Tag(name = "功能开关管理")
@ApiInternal(description = "功能开关管理内部接口")
@ConditionalOnProperty(name = "plugin.api.feature.enable", havingValue = "true", matchIfMissing = false)
public class ApiFeatureController {

    private final ApiFeatureManager featureManager;

    /**
     * 构造函数
     *
     * @param featureManager ApiFeatureManager
     */
    public ApiFeatureController(ApiFeatureManager featureManager) {
        this.featureManager = featureManager;
    }

    /**
     * 获取所有功能开关列表
     *
     * @return 功能开关列表
     */
    @GetMapping
    @Operation(summary = "获取所有功能开关")
    public ReturnResult<List<ApiFeatureInfo>> getAllFeaturesForApi() {
        return ReturnResult.ok(featureManager.getAllFeatures());
    }

    /**
     * 按分组获取功能开关
     *
     * @param group 分组名称
     * @return 功能开关列表
     */
    @GetMapping("/group/{group}")
    @Operation(summary = "按分组获取功能开关")
    public ReturnResult<List<ApiFeatureInfo>> getFeaturesByGroupForApi(
            @Parameter(description = "分组名称") @PathVariable String group) {
        return ReturnResult.ok(featureManager.getFeaturesByGroup(group));
    }

    /**
     * 获取所有分组
     *
     * @return 分组列表
     */
    @GetMapping("/groups")
    @Operation(summary = "获取所有分组")
    public ReturnResult<Set<String>> getAllGroupsForApi() {
        return ReturnResult.ok(featureManager.getAllGroups());
    }

    /**
     * 获取单个功能开关信息
     *
     * @param featureId 功能标识
     * @return 功能开关信息
     */
    @GetMapping("/{featureId}")
    @Operation(summary = "获取功能开关详情")
    public ReturnResult<ApiFeatureInfo> getFeatureInfoForApi(
            @Parameter(description = "功能标识") @PathVariable String featureId) {
        ApiFeatureInfo info = featureManager.getFeatureInfo(featureId);
        if (info == null) {
            return ReturnResult.error("功能开关不存在: " + featureId);
        }
        return ReturnResult.ok(info);
    }

    /**
     * 设置功能开关状态
     *
     * @param featureId 功能标识
     * @param enabled   是否启用
     * @return 操作结果
     */
    @PutMapping("/{featureId}")
    @Operation(summary = "设置功能开关状态")
    public ReturnResult<Boolean> setFeatureEnabledForApi(
            @Parameter(description = "功能标识") @PathVariable String featureId,
            @Parameter(description = "是否启用") @RequestParam boolean enabled) {
        boolean success = featureManager.setEnabled(featureId, enabled);
        if (!success) {
            return ReturnResult.error("功能开关不存在: " + featureId);
        }
        return ReturnResult.ok(true);
    }

    /**
     * 批量设置功能开关状态
     *
     * @param states 状态映射
     * @return 操作结果
     */
    @PutMapping("/batch")
    @Operation(summary = "批量设置功能开关状态")
    public ReturnResult<Boolean> setFeatureEnabledBatchForApi(
            @Parameter(description = "状态映射") @RequestBody Map<String, Boolean> states) {
        featureManager.setEnabledBatch(states);
        return ReturnResult.ok(true);
    }

    /**
     * 重置功能开关到默认状态
     *
     * @param featureId 功能标识
     * @return 操作结果
     */
    @PostMapping("/{featureId}/reset")
    @Operation(summary = "重置功能开关到默认状态")
    public ReturnResult<Boolean> resetFeatureForApi(
            @Parameter(description = "功能标识") @PathVariable String featureId) {
        featureManager.resetToDefault(featureId);
        return ReturnResult.ok(true);
    }

    /**
     * 重置所有功能开关到默认状态
     *
     * @return 操作结果
     */
    @PostMapping("/reset-all")
    @Operation(summary = "重置所有功能开关")
    public ReturnResult<Boolean> resetAllFeaturesForApi() {
        featureManager.resetAllToDefault();
        return ReturnResult.ok(true);
    }

    /**
     * 获取功能开关统计
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取功能开关统计")
    public ReturnResult<Map<String, Object>> getFeatureStatsForApi() {
        return ReturnResult.ok(Map.of(
                "total", featureManager.getFeatureCount(),
                "enabled", featureManager.getEnabledCount(),
                "disabled", featureManager.getFeatureCount() - featureManager.getEnabledCount()
        ));
    }
}

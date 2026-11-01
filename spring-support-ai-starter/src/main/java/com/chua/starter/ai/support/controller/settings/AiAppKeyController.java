package com.chua.starter.ai.support.controller.settings;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.model.AiAppKeyDTO;
import com.chua.starter.ai.support.model.AiAppKeyRechargeDTO;
import com.chua.starter.ai.support.model.AiAppKeyPropertyDTO;
import com.chua.starter.ai.support.entity.SysAiAppKeyTopupLog;
import com.chua.starter.ai.support.service.settings.AiAppKeyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.math.BigDecimal;

/**
 * appKey 控制器，提供 appKey 的增删改查与启停切换。
 *
 * <p>统一返回 {@link ReturnResult}。鉴权按用户隔离：查询对全体登录用户开放，
 * 非管理员仅返回本人名下记录；写接口由服务层的归属守卫校验，管理员对他人数据只读。</p>
 *
 * @since 2026-08-03
 * @author CH
 */
@RestController
@RequestMapping("/v2/app-keys")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - AppKey")
public class AiAppKeyController {

    /**
     * appKey 服务
     */
    private final AiAppKeyService appKeyService;

    /**
     * 查询 appKey，管理员全量、普通用户仅本人名下。
     *
     * @return 操作结果
     */
    @Operation(summary = "查询所有 AppKey")
    @GetMapping
    public ReturnResult<List<AiAppKeyDTO>> list() {
        return appKeyService.list();
    }

    /**
     * 新建 appKey。
     *
     * @param appKey appKey DTO
     * @return 操作结果
     */
    @Operation(summary = "新建 AppKey")
    @PostMapping
    public ReturnResult<AiAppKeyDTO> create(@RequestBody AiAppKeyDTO appKey) {
        return appKeyService.create(appKey);
    }

    /**
     * 更新 appKey。
     *
     * @param appKeyId appKey 主键
     * @param appKey   appKey DTO
     * @return 操作结果
     * @param ID" 标识"
     */
    @Operation(summary = "更新 AppKey")
    @PutMapping("/{appKeyId}")
    public ReturnResult<AiAppKeyDTO> update(@Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
                                            @RequestBody AiAppKeyDTO appKey) {
        return appKeyService.update(appKeyId, appKey);
    }

    /**
     * 删除 appKey（逻辑删除）。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果
     * @param ID" 标识"
     */
    @Operation(summary = "删除 AppKey")
    @DeleteMapping("/{appKeyId}")
    public ReturnResult<Void> delete(@Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId) {
        return appKeyService.delete(appKeyId);
    }

    /**
     * 批量删除 AppKey。
     *
     * <p>与 {@link #delete(Long)} 同口径：写出口归属守卫整批过完后才删，
     * 有问题的行直接拦截，不出现部分删除。</p>
     *
     * @param body 请求体，须含 {@code ids} 列表
     * @return 包含 success 与 deletedCount 的结果
     */
    @Operation(summary = "批量删除 AppKey")
    @DeleteMapping("/batch")
    public ReturnResult<Map<String, Object>> batchDelete(@RequestBody Map<String, ?> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = body == null ? null : (List<Long>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ids 不能为空");
        }
        int deleted = appKeyService.batchDelete(ids);
        Map<String, Object> result = new HashMap<>(2);
        result.put("success", true);
        result.put("deletedCount", deleted);
        return ReturnResult.ok(result);
    }

    /**
     * 切换 appKey 启用状态。
     *
     * @param appKeyId appKey 主键
     * @param enabled  是否启用
     * @return 操作结果
     * @param ID" 标识"
     */
    @Operation(summary = "切换 AppKey 启用状态")
    @PutMapping("/{appKeyId}/toggle")
    public ReturnResult<AiAppKeyDTO> toggle(@Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
                                            @Parameter(description = "是否启用") @RequestParam Boolean enabled) {
        return appKeyService.toggle(appKeyId, enabled);
    }

    /**
     * 查询 appKey 余额。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果（data 为余额）
     */
    @Operation(summary = "查询 AppKey 余额")
    @GetMapping("/{appKeyId}/balance")
    public ReturnResult<BigDecimal> balance(
            @Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId) {
        return appKeyService.getBalance(appKeyId);
    }

    /**
     * 充值 AppKey（增加余额 + 记 RECHARGE 流水）。
     *
     * @param appKeyId appKey 主键
     * @param dto      充值金额/备注
     * @return 操作结果（data 为更新后的 AppKey DTO）
     */
    @Operation(summary = "AppKey 充值")
    @PostMapping("/{appKeyId}/recharge")
    public ReturnResult<AiAppKeyDTO> recharge(
            @Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
            @Parameter(description = "充值金额/备注") @RequestBody AiAppKeyRechargeDTO dto) {
        return appKeyService.recharge(appKeyId, dto.getAmount(), dto.getRemark());
    }

    /**
     * 扣减 AppKey 余额（余额结算/调整，记 CONSUME 流水）。
     *
     * @param appKeyId appKey 主键
     * @param dto      扣减金额/备注
     * @return 操作结果（data 为更新后的 AppKey DTO）
     */
    @Operation(summary = "AppKey 扣减")
    @PostMapping("/{appKeyId}/deduct")
    public ReturnResult<AiAppKeyDTO> deduct(
            @Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
            @Parameter(description = "扣减金额/备注") @RequestBody AiAppKeyRechargeDTO dto) {
        return appKeyService.deduct(appKeyId, dto.getAmount(), dto.getRemark());
    }

    /**
     * 查询 AppKey 充值/消费流水（按流水时间倒序）。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果（data 为流水列表）
     */
    @Operation(summary = "AppKey 充值/消费流水")
    @GetMapping("/{appKeyId}/topups")
    public ReturnResult<List<SysAiAppKeyTopupLog>> topups(
            @Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId) {
        return appKeyService.topupLogs(appKeyId);
    }

    /**
     * 更新 AppKey 套餐。
     *
     * @param appKeyId appKey 主键
     * @param dto      套餐变更（套餐 ID）
     * @return 操作结果（data 为更新后的 AppKey DTO）
     */
    @Operation(summary = "AppKey 套餐")
    @PutMapping("/{appKeyId}/plan")
    public ReturnResult<AiAppKeyDTO> plan(
            @Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
            @Parameter(description = "套餐 ID") @RequestBody AiAppKeyPropertyDTO dto) {
        if (dto.getPlanId() == null) {
            return ReturnResult.error("套餐 ID 不能为空");
        }
        return appKeyService.updatePlan(appKeyId, dto.getPlanId());
    }

    /**
     * 更新 AppKey 透支开关。
     *
     * @param appKeyId appKey 主键
     * @param dto      透支开关变更
     * @return 操作结果（data 为更新后的 AppKey DTO）
     */
    @Operation(summary = "AppKey 透支开关")
    @PutMapping("/{appKeyId}/overdraft")
    public ReturnResult<AiAppKeyDTO> overdraft(
            @Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
            @Parameter(description = "是否允许透支") @RequestBody AiAppKeyPropertyDTO dto) {
        if (dto.getOverdraftEnabled() == null) {
            return ReturnResult.error("是否透支不能为空");
        }
        return appKeyService.updateOverdraft(appKeyId, dto.getOverdraftEnabled());
    }

    /**
     * 更新 AppKey 每分钟单价。
     *
     * @param appKeyId appKey 主键
     * @param dto      单价变更
     * @return 操作结果（data 为更新后的 AppKey DTO）
     */
    @Operation(summary = "AppKey 每分钟单价")
    @PutMapping("/{appKeyId}/rate")
    public ReturnResult<AiAppKeyDTO> rate(
            @Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
            @Parameter(description = "每分钟单价") @RequestBody AiAppKeyPropertyDTO dto) {
        if (dto.getMinuteRate() == null || dto.getMinuteRate().compareTo(BigDecimal.ZERO) < 0) {
            return ReturnResult.error("每分钟单价须正数");
        }
        return appKeyService.updateRate(appKeyId, dto.getMinuteRate());
    }
}

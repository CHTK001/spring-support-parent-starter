package com.chua.starter.ai.support.service.settings;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiAppKeyTopupLog;
import com.chua.starter.ai.support.model.AiAppKeyDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * appKey 服务接口
 *
 * @since 2026-08-03
 * @author CH
 */
public interface AiAppKeyService {

    /**
     * 查询所有 appKey。
     *
     * @return 操作结果（data 为 appKey DTO 列表）
     */
    ReturnResult<List<AiAppKeyDTO>> list();

    /**
     * 新建 appKey。
     *
     * @param appKey appKey DTO
     * @return 操作结果（data 为创建后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> create(AiAppKeyDTO appKey);

    /**
     * 更新 appKey。
     *
     * @param appKeyId appKey 主键
     * @param appKey   appKey DTO
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> update(Long appKeyId, AiAppKeyDTO appKey);

    /**
     * 查询 appKey 余额。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果（data 为余额）
     */
    ReturnResult<BigDecimal> getBalance(Long appKeyId);

    /**
     * 为 appKey 充值（增加余额 + 记流水 RECHARGE）。
     *
     * @param appKeyId appKey 主键
     * @param amount   充值金额
     * @param remark   备注
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> recharge(Long appKeyId, BigDecimal amount, String remark);

    /**
     * 扣减 appKey 余额（结算/调整，记流水 CONSUME）。
     *
     * <p>余额不足且不允许透支时拒绝。</p>
     *
     * @param appKeyId appKey 主键
     * @param amount   扣减金额
     * @param remark   备注
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> deduct(Long appKeyId, BigDecimal amount, String remark);

    /**
     * 查询 appKey 充值/消费流水。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果（data 为流水列表）
     */
    ReturnResult<List<SysAiAppKeyTopupLog>> topupLogs(Long appKeyId);

    /**
     * 更新套餐。
     *
     * @param appKeyId appKey 主键
     * @param planId   套餐 ID（0=无套餐）
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> updatePlan(Long appKeyId, Long planId);

    /**
     * 更新透支开关。
     *
     * @param appKeyId appKey 主键
     * @param enabled  是否允许透支
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> updateOverdraft(Long appKeyId, Boolean enabled);

    /**
     * 更新每分钟单价。
     *
     * @param appKeyId appKey 主键
     * @param minuteRate 每分钟单价
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> updateRate(Long appKeyId, BigDecimal minuteRate);

    /**
     * 删除 appKey（逻辑删除）。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果
     */
    ReturnResult<Void> delete(Long appKeyId);

    /**
     * 批量删除 appKey（逻辑删除）。
     *
     * <p>与 {@link #delete(Long)} 同口径：写出口归属守卫整批过完后才删，
     * 有问题的行直接拦截，出现部分删除。</p>
     *
     * @param appKeyIds appKey 主键列表
     * @return 实际删除的 appKey 数量
     */
    int batchDelete(List<Long> appKeyIds);

    /**
     * 切换 appKey 启用状态。
     *
     * @param appKeyId appKey 主键
     * @param enabled  是否启用
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> toggle(Long appKeyId, Boolean enabled);
}

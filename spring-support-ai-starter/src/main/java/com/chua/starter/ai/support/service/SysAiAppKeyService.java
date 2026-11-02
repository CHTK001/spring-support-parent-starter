package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.entity.SysAiAppKeyTopupLog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * AI appKey 服务接口。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysAiAppKeyService {

    /**
     * 查询全部 appKey。
     *
     * @return AppKey 列表
     */
    List<SysAiAppKey> list();

    /**
     * 新增 appKey。
     *
     * @param appKey appKey 实体
     * @return 保存后的 appKey
     */
    SysAiAppKey save(SysAiAppKey appKey);

    /**
     * 更新 appKey。
     *
     * @param appKey appKey 实体
     * @return 是否成功
     */
    boolean update(SysAiAppKey appKey);

    /**
     * 按 标识 删除 appKey。
     *
     * @param id appKey 标识
     * @return 是否成功
     */
    boolean deleteById(Integer id);

    /**
     * 按 appKey 查询。
     *
     * @param appKey appKey
     * @return AppKey 实体（可选）
     */
    default Optional<SysAiAppKey> findByAppKey(String appKey) {
        return Optional.ofNullable(getByAppKey(appKey));
    }

    /**
     * 获取byappKey。
     * @param appKey appKey
     * @return 获取byappKey的结果
     */
    SysAiAppKey getByAppKey(String appKey);

    /**
     * 获取群体idbyappKey。
     * @param appKey appKey
     * @return 获取群体idbyappKey的结果
     */
    Integer getGroupIdByAppKey(String appKey);

    /**
     * 验证。
     * @param appKey appKey
     * @param secret secret
     * @param ip ip
     * @return 验证的结果
     */
    boolean verify(String appKey, String secret, String ip);

    /**
     * 仅校验 appKey 是否有效（状态、过期、IP 白名单）。
     * <p>用于 Bearer/Header 单项凭证鉴权场景，AppKey 即完整凭证，不要求额外的 secret。</p>
     *
     * @param appKey appKey
     * @param ip     客户端 IP（可为空）
     * @return 是否有效
     */
    boolean verifyAppKeyOnly(String appKey, String ip);

    /**
     * 更新use信息byappKey。
     * @param appKey appKey
     */
    void updateUseInfoByAppKey(String appKey);

    /**
     * 更新 appKey 的 令牌 消耗累计值。
     *
     * <p>每次对话成功后异步调用，将本次消耗的 Token 数累加到
     * {@code sys_ai_app_key_total_tokens} 字段。</p>
     *
     * @param appKey  appKey 值
     * @param tokens  本次消耗的 令牌 数
     */
    void updateTokenQuotaByAppKey(String appKey, long tokens);

    /**
     * 按 appKey 查询余额（空/未找到返回零）。
     *
     * <p>仅做读取，不触发任何写操作。</p>
     *
     * @param appKey appKey 值
     * @return 余额（元）
     */
    BigDecimal getBalance(String appKey);

    /**
     * 检查 appKey 是否拥有可用额度。
     *
     * <p>额度由「余额 + 透支开关」决定：余额为负且不允许透支时，
     * 计费守卫（{@code AiAccessGuardService}）拒绝放行。</p>
     *
     * @param appKey appKey 值
     * @return 是否拥有可用额度
     */
    boolean isBillable(String appKey);

    /**
     * 充值：增加余额 + 记流水（RECHARGE），不触发写缓存，仅写库。
     *
     * @param appKey appKey 值
     * @param amount 充值金额（须正数）
     * @param remark 备注
     * @return 更新的行数
     */
    int recharge(String appKey, BigDecimal amount, String remark);

    /**
     * 扣费：减少余额 + 累加累计费用 + 记流水（CONSUME）。
     *
     * <p>余额不足且不允许透支时直接拒绝（返回 0）。</p>
     *
     * @param appKey appKey 值
     * @param amount 扣费金额（须正数）
     * @param remark 备注
     * @return 更新的行数
     */
    int deduct(String appKey, BigDecimal amount, String remark);

    /**
     * 查询充值/消费流水（按 appKey 值，按流水时间倒序）。
     *
     * @param appKey appKey 值
     * @return 流水列表
     */
    List<SysAiAppKeyTopupLog> topupLogs(String appKey);

    /**
     * 更新套餐（按 appKey 值）。
     *
     * @param appKey appKey 值
     * @param planId 套餐 ID，0 表示无套餐
     * @return 是否成功
     */
    boolean updatePlan(String appKey, Long planId);

    /**
     * 更新透支开关（按 appKey 值）。
     *
     * @param appKey  appKey 值
     * @param enabled 是否允许透支
     * @return 是否成功
     */
    boolean updateOverdraft(String appKey, boolean enabled);

    /**
     * 更新每分钟单价（按 appKey 值）。
     *
     * @param appKey     appKey 值
     * @param minuteRate 每分钟单价
     * @return 是否成功
     */
    boolean updateRate(String appKey, BigDecimal minuteRate);
}

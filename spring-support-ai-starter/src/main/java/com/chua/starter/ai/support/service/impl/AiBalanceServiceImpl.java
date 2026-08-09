package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.entity.SysAiToken;
import com.chua.starter.ai.support.service.AiBalanceService;
import com.chua.starter.ai.support.service.SysAiTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * AI 预算/费用累计服务实现。
 *
 * <p>基于 {@link SysAiTokenService} 的 Token 费用统计实现预算检查与费用累计。
 * 当前实现为「费用累计」模式，非传统余额扣减模式：</p>
 *
 * <ul>
 *   <li>{@code totalFee} 是累计消费总额（非剩余余额），每次调用增加</li>
 *   <li>{@code checkBalance} 检查分组下是否有启用的 Token（非余额检查）</li>
 *   <li>{@code deduct} 向 Token 的 {@code totalFee} 累加费用（非扣减余额）</li>
 *   <li>{@code getAvailableBalance} 返回累计消费总额（非可用余额）</li>
 * </ul>
 *
 * <p>如需传统余额模式，需新增 {@code sys_ai_balance} 表的余额字段并重构此服务。</p>
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBalanceServiceImpl implements AiBalanceService {

    private final SysAiTokenService sysAiTokenService;

    /**
     * 检查分组余额是否足够。
     * 目前通过检查该分组下是否有有效的 Token 来判断。
     */
    @Override
    public boolean checkBalance(Integer groupId) {
        if (groupId == null) {
            // 无分组 = 系统内部，不限余额
            return true;
        }
        var tokens = sysAiTokenService.listByGroupId(groupId);
        if (tokens == null || tokens.isEmpty()) {
            log.warn("[AiBalance] 分组 {} 无 Token，余额检查失败", groupId);
            return false;
        }
        // 至少有一个启用的 Token 即可
        boolean hasActive = tokens.stream().anyMatch(t -> t.getSysAiTokenStatus() != null
                && t.getSysAiTokenStatus() == 1);
        if (!hasActive) {
            log.warn("[AiBalance] 分组 {} 无启用的 Token", groupId);
        }
        return hasActive;
    }

    /**
     * 从指定分组扣费。
     * 费用累加到 Token 的 {@code totalFee} 和 {@code todayFee} 字段。
     */
    @Override
    public void deduct(Integer groupId, BigDecimal amount) {
        if (groupId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        var tokens = sysAiTokenService.listByGroupId(groupId);
        if (tokens == null || tokens.isEmpty()) {
            log.warn("[AiBalance] deduct 失败: 分组 {} 无 Token", groupId);
            return;
        }
        // 找到第一个启用的 Token 进行扣费
        SysAiToken target = tokens.stream()
                .filter(t -> t.getSysAiTokenStatus() != null && t.getSysAiTokenStatus() == 1)
                .findFirst()
                .orElse(null);
        if (target == null) {
            log.warn("[AiBalance] deduct 失败: 分组 {} 无启用的 Token", groupId);
            return;
        }
        target.setSysAiTokenTotalFee(
                (target.getSysAiTokenTotalFee() != null ? target.getSysAiTokenTotalFee() : BigDecimal.ZERO)
                        .add(amount));
        target.setSysAiTokenTodayFee(
                (target.getSysAiTokenTodayFee() != null ? target.getSysAiTokenTodayFee() : BigDecimal.ZERO)
                        .add(amount));
        sysAiTokenService.update(target);
    }

    /**
     * 获取分组的可用余额。
     * 返回该分组下第一个启用 Token 的总费用（累计消费）。
     */
    @Override
    public BigDecimal getAvailableBalance(Integer groupId) {
        if (groupId == null) {
            return BigDecimal.ZERO;
        }
        var tokens = sysAiTokenService.listByGroupId(groupId);
        if (tokens == null || tokens.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return tokens.stream()
                .filter(t -> t.getSysAiTokenStatus() != null && t.getSysAiTokenStatus() == 1)
                .findFirst()
                .map(t -> t.getSysAiTokenTotalFee() != null ? t.getSysAiTokenTotalFee() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public void deductBalance(Integer groupId, BigDecimal amount) {
        deduct(groupId, amount);
    }

    /**
     * 获取用户/分组余额（与 getAvailableBalance 一致）。
     */
    @Override
    public BigDecimal getUserBalance(Integer groupId) {
        return getAvailableBalance(groupId);
    }
}


// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderCreditHistoryMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderCreditMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderCredit;
import com.chua.starter.ai.support.manage.store.SysAiProviderCreditHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 供应商余额快照持久化服务。
 *
 * <p>余额读数原先只存进程内缓存，重启即失；本服务把每个 Key 的最新读数落到
 * {@code sys_ai_provider_credit}：同一供应商每次同步<b>先删后增</b>，表内始终
 * 只保留最新快照。手动刷新（{@code /credit?refresh=true}）与时间轮定时刷新
 * （{@code CreditRefreshScheduler}）都经由本服务落库。</p>
 *
 * <p>同时把每轮回源追加进 {@code sys_ai_provider_credit_history}（余额历史，
 * 供趋势图），并清理保留窗口之外的旧行：快照回答「现在剩多少」，历史回答
 * 「这段时间怎么变化」。</p>
 *
 * @since 4.0.0.59
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCreditSnapshotService {

    /**
     * 历史保留窗口：30 天（之外的旧行在每次落库时顺带清理）
     */
    private static final long HISTORY_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L;

    /**
     * 余额快照 映射器
     */
    private final SysAiProviderCreditMapper mapper;

    /**
     * 余额历史 映射器
     */
    private final SysAiProviderCreditHistoryMapper historyMapper;

    /**
     * 用本轮读数整体替换一个供应商的快照（先删后增，事务内）。
     *
     * @param rows 本轮逐 Key 读数（providerId / keyHash 已由调用方填好）
     * @return 落库行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int replaceAll(List<SysAiProviderCredit> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        String providerId = rows.get(0).getProviderId();
        mapper.delete(new LambdaQueryWrapper<SysAiProviderCredit>()
                .eq(SysAiProviderCredit::getProviderId, providerId));
        int saved = 0;
        for (SysAiProviderCredit row : rows) {
            try {
                if (mapper.insert(row) > 0) {
                    saved++;
                }
            } catch (RuntimeException e) {
                // 单行失败不拖垮整批：余额快照是展示数据，丢一行下次刷新会补上
                log.debug("[ProviderCredit] 快照落库失败 provider={} mask={}: {}",
                        row.getProviderId(), row.getKeyMask(), e.getMessage());
            }
        }
        log.info("[ProviderCredit] 余额快照落库完成 provider={}：{} 行", providerId, saved);
        return saved;
    }

    /**
     * 把本轮读数追加进历史表（每个 Key 一行，供趋势图取数）。
     *
     * <p>追加失败只记日志：历史是增值数据，不影响快照与面板主链路。</p>
     *
     * @param rows 本轮逐 Key 读数（providerId / keyHash / syncedAt 已由调用方填好）
     * @return 落库行数
     */
    public int appendHistory(List<SysAiProviderCreditHistory> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (SysAiProviderCreditHistory row : rows) {
            try {
                if (historyMapper.insert(row) > 0) {
                    saved++;
                }
            } catch (RuntimeException e) {
                log.debug("[ProviderCredit] 历史落库失败 provider={} mask={}: {}",
                        row.getProviderId(), row.getKeyMask(), e.getMessage());
            }
        }
        return saved;
    }

    /**
     * 清理保留窗口之外的历史行（每次落库顺带执行，表不会无限增长）。
     *
     * @param nowMs 当前时间（毫秒）
     * @return 清理行数
     */
    public int pruneHistory(long nowMs) {
        try {
            return historyMapper.delete(new LambdaQueryWrapper<SysAiProviderCreditHistory>()
                    .lt(SysAiProviderCreditHistory::getSyncedAt, nowMs - HISTORY_RETENTION_MS));
        } catch (RuntimeException e) {
            log.debug("[ProviderCredit] 历史清理失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 读一个供应商最近一段时间的余额历史（按时间升序，供趋势图）。
     *
     * @param providerId 供应商 ID
     * @param sinceMs    起始时间（毫秒，含）
     * @param limit      最多返回行数
     * @return 历史行（时间升序；无记录返回空列表）
     */
    public List<SysAiProviderCreditHistory> listHistory(String providerId, long sinceMs, int limit) {
        if (providerId == null || providerId.isBlank()) {
            return List.of();
        }
        return historyMapper.selectList(new LambdaQueryWrapper<SysAiProviderCreditHistory>()
                .eq(SysAiProviderCreditHistory::getProviderId, providerId)
                .ge(SysAiProviderCreditHistory::getSyncedAt, sinceMs)
                .orderByAsc(SysAiProviderCreditHistory::getSyncedAt)
                .orderByAsc(SysAiProviderCreditHistory::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    /**
     * 读一个供应商的全部余额快照（按 Key 顺序）。
     *
     * @param providerId 供应商 ID
     * @return 快照列表（无记录返回空列表）
     */
    public List<SysAiProviderCredit> listByProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<SysAiProviderCredit>()
                .eq(SysAiProviderCredit::getProviderId, providerId)
                .orderByAsc(SysAiProviderCredit::getId));
    }

    /**
     * 读一个供应商最近一次成功回源的时间。
     *
     * @param providerId 供应商 ID
     * @return 最近 synced_at；无记录为 null
     */
    public Long lastSyncedAt(String providerId) {
        List<SysAiProviderCredit> rows = listByProvider(providerId);
        Long latest = null;
        for (SysAiProviderCredit row : rows) {
            if (row.getSyncedAt() != null && (latest == null || row.getSyncedAt() > latest)) {
                latest = row.getSyncedAt();
            }
        }
        return latest;
    }
}

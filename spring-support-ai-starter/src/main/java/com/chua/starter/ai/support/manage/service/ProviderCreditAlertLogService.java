// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderCreditAlertLogMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderCreditAlertLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 供应商余额告警推送留痕服务。
 *
 * <p>余额低量告警 / 恢复通知经消息中心投递<b>成功</b>后由控制器写入一行留痕；
 * 投递失败不写。留痕是辅助数据：写入或查询失败只记日志、不抛异常，
 * 不影响推送与余额面板主链路。</p>
 *
 * @since 4.0.0.59
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCreditAlertLogService {

    /**
     * 告警留痕 映射器
     */
    private final SysAiProviderCreditAlertLogMapper mapper;

    /**
     * 追加一条推送留痕（broadcast 成功后调用）。
     *
     * @param row 留痕行（providerId / type / pushedAt 已由调用方填好）
     * @return 是否落库成功
     */
    public boolean record(SysAiProviderCreditAlertLog row) {
        if (row == null) {
            return false;
        }
        try {
            return mapper.insert(row) > 0;
        } catch (RuntimeException e) {
            log.debug("[ProviderCredit] 告警留痕落库失败 provider={} type={}: {}",
                    row.getProviderId(), row.getType(), e.getMessage());
            return false;
        }
    }

    /**
     * 读一个供应商最近的推送留痕（按推送时间倒序）。
     *
     * @param providerId 供应商 ID
     * @param limit      最多返回行数
     * @return 留痕列表（时间倒序；无记录返回空列表）
     */
    public List<SysAiProviderCreditAlertLog> recent(String providerId, int limit) {
        if (providerId == null || providerId.isBlank()) {
            return List.of();
        }
        try {
            return mapper.selectList(new LambdaQueryWrapper<SysAiProviderCreditAlertLog>()
                    .eq(SysAiProviderCreditAlertLog::getProviderId, providerId)
                    .orderByDesc(SysAiProviderCreditAlertLog::getPushedAt)
                    .orderByDesc(SysAiProviderCreditAlertLog::getId)
                    .last("LIMIT " + Math.max(1, limit)));
        } catch (RuntimeException e) {
            log.debug("[ProviderCredit] 告警留痕查询失败 provider={}: {}", providerId, e.getMessage());
            return List.of();
        }
    }
}

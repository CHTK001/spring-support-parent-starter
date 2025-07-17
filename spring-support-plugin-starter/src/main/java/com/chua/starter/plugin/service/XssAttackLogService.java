package com.chua.starter.plugin.service;

import com.chua.starter.plugin.entity.PluginXssAttackLog;
import com.chua.starter.plugin.store.PageResult;
import com.chua.starter.plugin.store.PersistenceStore;
import com.chua.starter.plugin.store.QueryCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * XSS攻击日志服务
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XssAttackLogService {

    private final PersistenceStore<PluginXssAttackLog, Long> xssAttackLogStore;

    /**
     * 保存攻击日志
     * 
     * @param attackLog 攻击日志
     * @return 保存后的日志
     */
    @Transactional
    public PluginXssAttackLog saveAttackLog(PluginXssAttackLog attackLog) {
        if (attackLog == null) {
            throw new IllegalArgumentException("Attack log cannot be null");
        }

        try {
            PluginXssAttackLog savedLog = xssAttackLogStore.save(attackLog);
            log.debug("Saved XSS attack log: {}", savedLog.getAttackSummary());
            return savedLog;
        } catch (Exception e) {
            log.error("Failed to save XSS attack log", e);
            throw e;
        }
    }

    /**
     * 获取攻击日志列表（分页）
     * 
     * @param page 页码
     * @param size 页大小
     * @param attackerIp 攻击者IP（可选）
     * @param riskLevel 风险等级（可选）
     * @return 分页结果
     */
    public PageResult<PluginXssAttackLog> getAttackLogs(int page, int size, String attackerIp, 
                                                       PluginXssAttackLog.RiskLevel riskLevel) {
        try {
            QueryCondition condition = QueryCondition.empty();
            
            // 添加过滤条件
            if (attackerIp != null && !attackerIp.trim().isEmpty()) {
                condition.eq("pluginXssAttackLogAttackerIp", attackerIp.trim());
            }
            
            if (riskLevel != null) {
                condition.eq("pluginXssAttackLogRiskLevel", riskLevel);
            }
            
            // 按攻击时间倒序排列
            condition.orderByDesc("pluginXssAttackLogAttackTime");
            
            return xssAttackLogStore.findPage(condition, page, size);
        } catch (Exception e) {
            log.error("Failed to get XSS attack logs", e);
            return PageResult.empty();
        }
    }

    /**
     * 获取最近的攻击日志
     * 
     * @param limit 限制数量
     * @return 攻击日志列表
     */
    public List<PluginXssAttackLog> getRecentAttackLogs(int limit) {
        try {
            QueryCondition condition = QueryCondition.empty()
                .orderByDesc("pluginXssAttackLogAttackTime");
            
            List<PluginXssAttackLog> allLogs = xssAttackLogStore.findByCondition(condition);
            return allLogs.stream().limit(limit).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get recent XSS attack logs", e);
            return List.of();
        }
    }

    /**
     * 获取高危攻击日志
     * 
     * @return 高危攻击日志列表
     */
    public List<PluginXssAttackLog> getHighRiskAttackLogs() {
        try {
            QueryCondition condition = QueryCondition.empty()
                .in("pluginXssAttackLogRiskLevel", 
                    List.of(PluginXssAttackLog.RiskLevel.HIGH, PluginXssAttackLog.RiskLevel.CRITICAL))
                .orderByDesc("pluginXssAttackLogAttackTime");
            
            return xssAttackLogStore.findByCondition(condition);
        } catch (Exception e) {
            log.error("Failed to get high risk XSS attack logs", e);
            return List.of();
        }
    }

    /**
     * 根据IP获取攻击日志
     * 
     * @param attackerIp 攻击者IP
     * @return 攻击日志列表
     */
    public List<PluginXssAttackLog> getAttackLogsByIp(String attackerIp) {
        if (attackerIp == null || attackerIp.trim().isEmpty()) {
            return List.of();
        }

        try {
            QueryCondition condition = QueryCondition.empty()
                .eq("pluginXssAttackLogAttackerIp", attackerIp.trim())
                .orderByDesc("pluginXssAttackLogAttackTime");
            
            return xssAttackLogStore.findByCondition(condition);
        } catch (Exception e) {
            log.error("Failed to get XSS attack logs by IP: {}", attackerIp, e);
            return List.of();
        }
    }

    /**
     * 获取攻击统计信息
     * 
     * @return 统计信息
     */
    public Map<String, Object> getAttackStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        try {
            // 总攻击次数
            long totalAttacks = xssAttackLogStore.count();
            statistics.put("totalAttacks", totalAttacks);
            
            // 今日攻击次数
            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            QueryCondition todayCondition = QueryCondition.empty()
                .ge("pluginXssAttackLogAttackTime", todayStart);
            long todayAttacks = xssAttackLogStore.countByCondition(todayCondition);
            statistics.put("todayAttacks", todayAttacks);
            
            // 本周攻击次数
            LocalDateTime weekStart = todayStart.minusDays(todayStart.getDayOfWeek().getValue() - 1);
            QueryCondition weekCondition = QueryCondition.empty()
                .ge("pluginXssAttackLogAttackTime", weekStart);
            long weekAttacks = xssAttackLogStore.countByCondition(weekCondition);
            statistics.put("weekAttacks", weekAttacks);
            
            // 高危攻击次数
            QueryCondition highRiskCondition = QueryCondition.empty()
                .in("pluginXssAttackLogRiskLevel", 
                    List.of(PluginXssAttackLog.RiskLevel.HIGH, PluginXssAttackLog.RiskLevel.CRITICAL));
            long highRiskAttacks = xssAttackLogStore.countByCondition(highRiskCondition);
            statistics.put("highRiskAttacks", highRiskAttacks);
            
            // 按风险等级统计
            Map<String, Long> riskLevelStats = new HashMap<>();
            for (PluginXssAttackLog.RiskLevel level : PluginXssAttackLog.RiskLevel.values()) {
                QueryCondition levelCondition = QueryCondition.empty()
                    .eq("pluginXssAttackLogRiskLevel", level);
                long count = xssAttackLogStore.countByCondition(levelCondition);
                riskLevelStats.put(level.name(), count);
            }
            statistics.put("riskLevelStats", riskLevelStats);
            
            // 按攻击类型统计
            Map<String, Long> attackTypeStats = new HashMap<>();
            for (PluginXssAttackLog.AttackType type : PluginXssAttackLog.AttackType.values()) {
                QueryCondition typeCondition = QueryCondition.empty()
                    .eq("pluginXssAttackLogAttackType", type);
                long count = xssAttackLogStore.countByCondition(typeCondition);
                attackTypeStats.put(type.name(), count);
            }
            statistics.put("attackTypeStats", attackTypeStats);
            
            // 最近7天攻击趋势
            Map<String, Long> dailyTrend = new HashMap<>();
            for (int i = 6; i >= 0; i--) {
                LocalDateTime dayStart = todayStart.minusDays(i);
                LocalDateTime dayEnd = dayStart.plusDays(1);
                
                QueryCondition dayCondition = QueryCondition.empty()
                    .ge("pluginXssAttackLogAttackTime", dayStart)
                    .lt("pluginXssAttackLogAttackTime", dayEnd);
                long dayCount = xssAttackLogStore.countByCondition(dayCondition);
                
                String dayKey = dayStart.toLocalDate().toString();
                dailyTrend.put(dayKey, dayCount);
            }
            statistics.put("dailyTrend", dailyTrend);
            
        } catch (Exception e) {
            log.error("Failed to get XSS attack statistics", e);
        }
        
        return statistics;
    }

    /**
     * 获取攻击者IP统计（Top N）
     * 
     * @param topN 前N名
     * @return IP统计列表
     */
    public List<Map<String, Object>> getTopAttackerIps(int topN) {
        try {
            List<PluginXssAttackLog> allLogs = xssAttackLogStore.findAll();
            
            Map<String, Long> ipCounts = allLogs.stream()
                .collect(Collectors.groupingBy(
                    PluginXssAttackLog::getPluginXssAttackLogAttackerIp,
                    Collectors.counting()
                ));
            
            return ipCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> {
                    Map<String, Object> ipStat = new HashMap<>();
                    ipStat.put("ip", entry.getKey());
                    ipStat.put("count", entry.getValue());
                    return ipStat;
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get top attacker IPs", e);
            return List.of();
        }
    }

    /**
     * 标记攻击日志为已处理
     * 
     * @param logId 日志ID
     * @param remark 处理备注
     * @return 是否操作成功
     */
    @Transactional
    public boolean markAsHandled(Long logId, String remark) {
        try {
            var logOpt = xssAttackLogStore.findById(logId);
            if (logOpt.isPresent()) {
                PluginXssAttackLog log = logOpt.get();
                log.setPluginXssAttackLogHandled(true);
                log.setPluginXssAttackLogRemark(remark);
                
                xssAttackLogStore.save(log);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to mark XSS attack log as handled: {}", logId, e);
            return false;
        }
    }

    /**
     * 清理过期攻击日志
     * 
     * @param daysToKeep 保留天数
     * @return 清理的数量
     */
    @Transactional
    public int cleanupExpiredLogs(int daysToKeep) {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(daysToKeep);
            
            QueryCondition condition = QueryCondition.empty()
                .lt("pluginXssAttackLogAttackTime", cutoffTime);
            
            int deletedCount = xssAttackLogStore.deleteByCondition(condition);
            
            log.info("Cleaned up {} expired XSS attack logs (older than {} days)", 
                deletedCount, daysToKeep);
            
            return deletedCount;
        } catch (Exception e) {
            log.error("Failed to cleanup expired XSS attack logs", e);
            return 0;
        }
    }

    /**
     * 删除指定IP的所有攻击日志
     * 
     * @param attackerIp 攻击者IP
     * @return 删除的数量
     */
    @Transactional
    public int deleteLogsByIp(String attackerIp) {
        if (attackerIp == null || attackerIp.trim().isEmpty()) {
            return 0;
        }

        try {
            QueryCondition condition = QueryCondition.empty()
                .eq("pluginXssAttackLogAttackerIp", attackerIp.trim());
            
            int deletedCount = xssAttackLogStore.deleteByCondition(condition);
            
            log.info("Deleted {} XSS attack logs for IP: {}", deletedCount, attackerIp);
            
            return deletedCount;
        } catch (Exception e) {
            log.error("Failed to delete XSS attack logs for IP: {}", attackerIp, e);
            return 0;
        }
    }
}

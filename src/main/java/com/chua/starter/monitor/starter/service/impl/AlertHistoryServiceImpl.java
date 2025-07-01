package com.chua.starter.monitor.starter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.starter.entity.MonitorSysGenServerAlertHistory;
import com.chua.starter.monitor.starter.enums.AlertLevel;
import com.chua.starter.monitor.starter.enums.AlertStatus;
import com.chua.starter.monitor.starter.enums.AlertType;
import com.chua.starter.monitor.starter.mapper.MonitorSysGenServerAlertHistoryMapper;
import com.chua.starter.monitor.starter.service.AlertHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警历史服务实现类
 * 负责告警历史记录的管理，包括保存、查询、状态更新等
 * 
 * @author CH
 * @since 2024/12/25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertHistoryServiceImpl extends ServiceImpl<MonitorSysGenServerAlertHistoryMapper, MonitorSysGenServerAlertHistory>
        implements AlertHistoryService {

    @Override
    public ReturnResult<Boolean> saveAlert(MonitorSysGenServerAlertHistory alert) {
        try {
            if (alert == null) {
                return ReturnResult.error("告警记录不能为空");
            }

            // 设置默认值
            if (alert.getMonitorSysGenServerAlertHistoryTriggerTime() == null) {
                alert.setMonitorSysGenServerAlertHistoryTriggerTime(LocalDateTime.now());
            }
            if (alert.getMonitorSysGenServerAlertHistoryStatus() == null) {
                alert.setMonitorSysGenServerAlertHistoryStatus(AlertStatus.ACTIVE);
            }
            if (alert.getMonitorSysGenServerAlertHistoryNotificationSent() == null) {
                alert.setMonitorSysGenServerAlertHistoryNotificationSent(false);
            }

            boolean success = this.save(alert);
            if (success) {
                log.info("告警记录保存成功: serverId={}, type={}, level={}", 
                        alert.getMonitorSysGenServerAlertHistoryServerId(),
                        alert.getMonitorSysGenServerAlertHistoryType(),
                        alert.getMonitorSysGenServerAlertHistoryLevel());
                return ReturnResult.success(true);
            } else {
                return ReturnResult.error("告警记录保存失败");
            }

        } catch (Exception e) {
            log.error("保存告警记录失败", e);
            return ReturnResult.error("保存告警记录失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<IPage<MonitorSysGenServerAlertHistory>> getAlertHistory(Integer serverId, AlertLevel level, 
                                                                                AlertStatus status, LocalDateTime startTime, 
                                                                                LocalDateTime endTime, int page, int size) {
        try {
            LambdaQueryWrapper<MonitorSysGenServerAlertHistory> queryWrapper = new LambdaQueryWrapper<>();
            
            // 服务器ID过滤
            if (serverId != null) {
                queryWrapper.eq(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryServerId, serverId);
            }
            
            // 告警级别过滤
            if (level != null) {
                queryWrapper.eq(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryLevel, level);
            }
            
            // 告警状态过滤
            if (status != null) {
                queryWrapper.eq(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryStatus, status);
            }
            
            // 时间范围过滤
            if (startTime != null) {
                queryWrapper.ge(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryTriggerTime, startTime);
            }
            if (endTime != null) {
                queryWrapper.le(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryTriggerTime, endTime);
            }
            
            // 按触发时间倒序排列
            queryWrapper.orderByDesc(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryTriggerTime);
            
            Page<MonitorSysGenServerAlertHistory> pageParam = new Page<>(page, size);
            IPage<MonitorSysGenServerAlertHistory> result = this.page(pageParam, queryWrapper);
            
            return ReturnResult.success(result);

        } catch (Exception e) {
            log.error("查询告警历史失败", e);
            return ReturnResult.error("查询告警历史失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<MonitorSysGenServerAlertHistory>> getActiveAlerts(Integer serverId) {
        try {
            LambdaQueryWrapper<MonitorSysGenServerAlertHistory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryStatus, AlertStatus.ACTIVE);
            
            if (serverId != null) {
                queryWrapper.eq(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryServerId, serverId);
            }
            
            queryWrapper.orderByDesc(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryTriggerTime);
            
            List<MonitorSysGenServerAlertHistory> alerts = this.list(queryWrapper);
            return ReturnResult.success(alerts);

        } catch (Exception e) {
            log.error("查询活跃告警失败", e);
            return ReturnResult.error("查询活跃告警失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> resolveAlert(Long alertId, String resolvedBy, String resolveReason) {
        try {
            if (alertId == null) {
                return ReturnResult.error("告警ID不能为空");
            }

            MonitorSysGenServerAlertHistory alert = this.getById(alertId);
            if (alert == null) {
                return ReturnResult.error("告警记录不存在");
            }

            alert.setMonitorSysGenServerAlertHistoryStatus(AlertStatus.RESOLVED);
            alert.setMonitorSysGenServerAlertHistoryResolveTime(LocalDateTime.now());
            alert.setMonitorSysGenServerAlertHistoryResolvedBy(resolvedBy);
            alert.setMonitorSysGenServerAlertHistoryResolveReason(resolveReason);

            boolean success = this.updateById(alert);
            if (success) {
                log.info("告警已解决: alertId={}, resolvedBy={}", alertId, resolvedBy);
                return ReturnResult.success(true);
            } else {
                return ReturnResult.error("更新告警状态失败");
            }

        } catch (Exception e) {
            log.error("解决告警失败: alertId={}", alertId, e);
            return ReturnResult.error("解决告警失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> silenceAlert(Long alertId, Integer silenceDuration, String silencedBy) {
        try {
            if (alertId == null || silenceDuration == null || silenceDuration <= 0) {
                return ReturnResult.error("参数不能为空");
            }

            MonitorSysGenServerAlertHistory alert = this.getById(alertId);
            if (alert == null) {
                return ReturnResult.error("告警记录不存在");
            }

            alert.setMonitorSysGenServerAlertHistoryStatus(AlertStatus.SILENCED);
            alert.setMonitorSysGenServerAlertHistorySilenceUntil(LocalDateTime.now().plusMinutes(silenceDuration));
            alert.setMonitorSysGenServerAlertHistorySilencedBy(silencedBy);

            boolean success = this.updateById(alert);
            if (success) {
                log.info("告警已静默: alertId={}, duration={}分钟, silencedBy={}", alertId, silenceDuration, silencedBy);
                return ReturnResult.success(true);
            } else {
                return ReturnResult.error("更新告警状态失败");
            }

        } catch (Exception e) {
            log.error("静默告警失败: alertId={}", alertId, e);
            return ReturnResult.error("静默告警失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Integer> autoRecoverExpiredAlerts(Integer expiredMinutes) {
        try {
            if (expiredMinutes == null || expiredMinutes <= 0) {
                expiredMinutes = 30; // 默认30分钟
            }

            LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(expiredMinutes);
            
            LambdaQueryWrapper<MonitorSysGenServerAlertHistory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryStatus, AlertStatus.ACTIVE)
                       .lt(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryTriggerTime, expiredTime);

            List<MonitorSysGenServerAlertHistory> expiredAlerts = this.list(queryWrapper);
            
            int recoveredCount = 0;
            for (MonitorSysGenServerAlertHistory alert : expiredAlerts) {
                alert.setMonitorSysGenServerAlertHistoryStatus(AlertStatus.RESOLVED);
                alert.setMonitorSysGenServerAlertHistoryResolveTime(LocalDateTime.now());
                alert.setMonitorSysGenServerAlertHistoryResolvedBy("SYSTEM");
                alert.setMonitorSysGenServerAlertHistoryResolveReason("自动恢复：超过" + expiredMinutes + "分钟未再次触发");
                
                if (this.updateById(alert)) {
                    recoveredCount++;
                }
            }

            log.info("自动恢复过期告警完成: 恢复数量={}, 过期时间={}分钟", recoveredCount, expiredMinutes);
            return ReturnResult.success(recoveredCount);

        } catch (Exception e) {
            log.error("自动恢复过期告警失败", e);
            return ReturnResult.error("自动恢复过期告警失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<MonitorSysGenServerAlertHistory>> getUnsentNotificationAlerts(Integer limit) {
        try {
            LambdaQueryWrapper<MonitorSysGenServerAlertHistory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryNotificationSent, false)
                       .in(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryStatus, 
                           AlertStatus.ACTIVE, AlertStatus.RESOLVED)
                       .orderByAsc(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryTriggerTime);

            if (limit != null && limit > 0) {
                queryWrapper.last("LIMIT " + limit);
            }

            List<MonitorSysGenServerAlertHistory> alerts = this.list(queryWrapper);
            return ReturnResult.success(alerts);

        } catch (Exception e) {
            log.error("查询未发送通知的告警失败", e);
            return ReturnResult.error("查询未发送通知的告警失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> updateNotificationStatus(Long alertId, Boolean sent, String notificationMethod, String notificationAddress) {
        try {
            if (alertId == null) {
                return ReturnResult.error("告警ID不能为空");
            }

            MonitorSysGenServerAlertHistory alert = this.getById(alertId);
            if (alert == null) {
                return ReturnResult.error("告警记录不存在");
            }

            alert.setMonitorSysGenServerAlertHistoryNotificationSent(sent);
            alert.setMonitorSysGenServerAlertHistoryNotificationTime(LocalDateTime.now());
            
            if (StringUtils.hasText(notificationMethod)) {
                alert.setMonitorSysGenServerAlertHistoryNotificationMethod(notificationMethod);
            }
            if (StringUtils.hasText(notificationAddress)) {
                alert.setMonitorSysGenServerAlertHistoryNotificationAddress(notificationAddress);
            }

            boolean success = this.updateById(alert);
            if (success) {
                log.debug("更新告警通知状态成功: alertId={}, sent={}", alertId, sent);
                return ReturnResult.success(true);
            } else {
                return ReturnResult.error("更新告警通知状态失败");
            }

        } catch (Exception e) {
            log.error("更新告警通知状态失败: alertId={}", alertId, e);
            return ReturnResult.error("更新告警通知状态失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Integer> cleanupExpiredAlerts(Integer retentionDays) {
        try {
            if (retentionDays == null || retentionDays <= 0) {
                retentionDays = 30; // 默认保留30天
            }

            LocalDateTime expiredTime = LocalDateTime.now().minusDays(retentionDays);
            
            LambdaQueryWrapper<MonitorSysGenServerAlertHistory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.lt(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryTriggerTime, expiredTime)
                       .in(MonitorSysGenServerAlertHistory::getMonitorSysGenServerAlertHistoryStatus, 
                           AlertStatus.RESOLVED, AlertStatus.CANCELLED);

            int deletedCount = this.baseMapper.delete(queryWrapper);
            
            log.info("清理过期告警记录完成: 删除数量={}, 保留天数={}", deletedCount, retentionDays);
            return ReturnResult.success(deletedCount);

        } catch (Exception e) {
            log.error("清理过期告警记录失败", e);
            return ReturnResult.error("清理过期告警记录失败: " + e.getMessage());
        }
    }
}

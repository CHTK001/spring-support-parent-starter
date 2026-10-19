package com.chua.starter.job.support.notify;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 告警通道聚合器：把宿主注册的多条 {@link JobAlarmNotifier} 按类型收进一张表，按消息类型分发。
 *
 * <p>执行器只依赖单一 {@link JobAlarmNotifier}，由本类承担"多条通道选一条"的路由，
 * 这样新增告警类型只需宿主多注册一个 Bean，执行器无需感知通道数量。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class CompositeJobAlarmNotifier implements JobAlarmNotifier {

    /**
     * 类型（大写）到通道实现；同类型重复注册时后到者被忽略。
     */
    private final Map<String, JobAlarmNotifier> byType = new LinkedHashMap<>();

    /**
     * 构造聚合器。
     *
     * @param notifiers 宿主注册的通道实现，可为空
     */
    public CompositeJobAlarmNotifier(Collection<JobAlarmNotifier> notifiers) {
        if (notifiers == null) {
            return;
        }
        for (JobAlarmNotifier notifier : notifiers) {
            if (notifier == null || notifier.type() == null || notifier.type().isBlank()) {
                continue;
            }
            byType.putIfAbsent(notifier.type().trim().toUpperCase(Locale.ROOT), notifier);
        }
    }

    /**
     * 是否一条通道都没有。
     *
     * @return 空返回 true
     */
    public boolean isEmpty() {
        return byType.isEmpty();
    }

    /**
     * 已注册的类型清单，供日志/诊断使用。
     *
     * @return 类型清单
     */
    public Collection<String> types() {
        return byType.keySet();
    }

    @Override
    public String type() {
        // 聚合器不代表单一类型，只用于路由
        return "*";
    }

    @Override
    public boolean support(String alarmType) {
        return byType.containsKey(JobAlarmMessage.normalizeType(alarmType));
    }

    @Override
    public void notify(JobAlarmMessage message) throws Exception {
        String alarmType = JobAlarmMessage.normalizeType(message == null ? null : message.alarmType());
        JobAlarmNotifier target = byType.get(alarmType);
        if (target == null) {
            throw new IllegalStateException("未接入 " + alarmType + " 类型告警通道（已注册: " + byType.keySet() + "）");
        }
        target.notify(message);
    }
}

package com.chua.starter.server.support.event;

/**
 * 批量计划任务变更事件
 * <p>
 * 任务新增/更新/删除后发布，由 {@code ExecJobScheduler} 监听并同步
 * 对应的 CronTrigger 调度（注册/重排/取消）。用事件解耦，
 * 避免 服务 ↔ 调度器 直接依赖造成循环引用。
 * </p>
 *
 * @param jobId   任务主键
 * @param deleted true=已删除（取消调度）；false=新增或更新（重排调度）
 * @author CH
 * @since 4.0.0.42
 */
public record ExecJobChangedEvent(Long jobId, boolean deleted) {
}

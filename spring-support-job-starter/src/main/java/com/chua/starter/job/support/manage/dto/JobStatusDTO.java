package com.chua.starter.job.support.manage.dto;

/**
 * 任务启停入参。
 *
 * <p>只影响自动调度 (到点是否派发), 手动触发接口对停用任务依然可用。</p>
 *
 * @param jobKey  任务键
 * @param enabled 是否启用自动调度
 * @since 4.0.0.42
 * @author CH
 */
public record JobStatusDTO(
        String jobKey,
        Boolean enabled) {
}

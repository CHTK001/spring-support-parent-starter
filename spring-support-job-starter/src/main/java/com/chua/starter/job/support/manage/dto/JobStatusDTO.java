package com.chua.starter.job.support.manage.dto;

import lombok.Data;

/**
 * 任务启停入参。
 *
 * <p>只影响自动调度 (到点是否派发), 手动触发接口对停用任务依然可用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobStatusDTO {

    /**
     * 任务键
     */
    private String jobKey;

    /**
     * 是否启用自动调度
     */
    private Boolean enabled;
}

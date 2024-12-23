package com.chua.report.client.starter.entity;

import lombok.Data;

/**
 * 任务结果
 * @author CH
 * @since 2024/9/11
 */
@Data
public class JobResult {

    /**
     * 任务ID
     */
    private String id;

    /**
     * 执行结果
     */
    private String code;

    /**
     * 执行信息
     */
    private String message;
}

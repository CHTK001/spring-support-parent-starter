package com.chua.starter.task.support.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务状态
 * @author CH
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskStatus {
    /**
     * 任务ID
     */
    private String tid;
    /**
     * 当前进度
     */
    private long offset;

    /**
     * 总进度
     */
    private long total;
}

package com.chua.starter.job.support.dispatch;

import lombok.Data;

/**
 * 任务分页查询条件。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobQuery {

    /**
     * 页码, 从 1 开始
     */
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    // [P3C A20 豁免]
    private Integer pageSize = 10;

    /**
     * 分组精确匹配, 可空
     */
    private String group;

    /**
     * 任务键/名称/描述模糊匹配, 可空
     */
    private String keyword;

    /**
     * 调度状态: 0-停止 1-运行, 可空
     */
    private Integer status;

    /**
     * 分发模式精确匹配 (LOCAL/REMOTE), 可空
     */
    private String dispatchMode;
}

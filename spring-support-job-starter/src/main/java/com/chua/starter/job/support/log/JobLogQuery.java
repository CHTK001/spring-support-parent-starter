package com.chua.starter.job.support.log;

import lombok.Data;

/**
 * 任务执行日志查询条件。
 *
 * <p>只描述「看哪些日志」, 不含分页以外的展示语义。{@link #keyword} 同时命中处理器 Bean 名、
 * 任务编号、节点标识、结果说明与台账里的任务名（页面上「任务」列显示的就是它），
 * 避免页面为几个列各传一个参数。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobLogQuery {

    /**
     * 页码, 从 1 开始
     */
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 关键字: 处理器 Bean 名 / 任务编号 / 节点标识 / 结果说明 / 台账任务名
     */
    private String keyword;

    /**
     * 任务键精确匹配 (处理器 Bean 名或任务编号), 可空
     */
    private String jobKey;

    /**
     * 执行结果码精确匹配: SUCCESS / FAILURE / SKIPPED, 可空
     */
    private String executeCode;

    /**
     * 任务分组精确匹配, 可空。
     *
     * <p>按<b>作业归属</b>过滤, 而不是按日志产生节点过滤: {@code sys_job_log} 自身没有分组列,
     * 且 {@code sys_job_log_node_id} 记的是<b>实际执行并落日志的那个节点</b>——远程作业在执行器上
     * 跑完时, 这里写的是执行器的 nodeId, 调度器自己永远匹配不到。若按 nodeId 过滤,
     * 调度中心会把执行器上发生的全部失败一并漏掉, 比不过滤更危险。
     * 因此这里通过 {@code sys_job_id} 关联台账的 {@code sys_job_group} 来限定范围。</p>
     */
    private String group;

    /**
     * 触发时刻下界 (毫秒), 可空
     */
    private Long startTime;

    /**
     * 触发时刻上界 (毫秒), 可空
     */
    private Long endTime;
}

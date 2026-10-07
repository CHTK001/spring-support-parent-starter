package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅卷任务实时进度 VO（SSE 推送载荷）。
 *
 * <p>与 {@link MarkingResultVO} 的分工：结果 VO 面向「已经判完的逐题明细」，
 * 这个 VO 面向「正在跑的过程」——老师上传答题卡后不必等整单判完，
 * 通过 {@code GET /v2/edu/marking/stream/{taskId}} 就能看到识别、解析、判分的实时推进。</p>
 *
 * <p>字段同时落库（{@code edu_marking_task}），因此断线重连 / 换页后重订阅时，
 * 服务端能用库里当前行拼一个快照先推给新连接，不丢进度。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class MarkingProgressVO {

    /**
     * 任务业务标识
     */
    private String taskId;

    /**
     * 任务状态：0-待处理 1-处理中 2-已完成 3-失败
     */
    private Integer taskStatus;

    /**
     * 当前阶段描述（识别中 / 解析题目 / 判分中 3/15 / 已完成 / 失败）
     */
    private String stage;

    /**
     * 识别出的题目总数（识别完成前为 null）
     */
    private Integer total;

    /**
     * 已判分题目数
     */
    private Integer doneCount;

    /**
     * 进度百分比 0-100；无法计算（题目数未知）时为 null，前端用不确定态进度条展示
     */
    private Integer percent;

    /**
     * 失败原因（taskStatus=3 时）
     */
    private String error;

    /**
     * 是否终态（已完成或失败）——前端据此收流并拉取最终结果
     */
    private Boolean finished;

    /**
     * 试卷类型：SELF 自有卷 / ANY 任意卷
     */
    private String mode;

    /**
     * 学生标识
     */
    private String studentId;

    /**
     * 本次进度事件时间
     */
    private LocalDateTime updateTime;
}

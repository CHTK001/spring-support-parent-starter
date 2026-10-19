package com.chua.starter.eduai.model.vo;

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
 * @param taskId     任务业务标识
 * @param taskStatus 任务状态：0-待处理 1-处理中 2-已完成 3-失败
 * @param stage      当前阶段描述（识别中 / 解析题目 / 判分中 3/15 / 已完成 / 失败）
 * @param total      识别出的题目总数（识别完成前为 null）
 * @param doneCount  已判分题目数
 * @param percent    进度百分比 0-100；无法计算（题目数未知）时为 null，前端用不确定态进度条展示
 * @param error      失败原因（taskStatus=3 时）
 * @param finished   是否终态（已完成或失败）——前端据此收流并拉取最终结果
 * @param mode           试卷类型：SELF 自有卷 / ANY 任意卷
 * @param studentId     学生标识
 * @param paperTotalScore 卷面总分（SELF 卷有效；ANY 卷为 null）
 * @param updateTime     本次进度事件时间
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingProgressVO(
        String taskId,
        Integer taskStatus,
        String stage,
        Integer total,
        Integer doneCount,
        Integer percent,
        String error,
        Boolean finished,
        String mode,
        String studentId,
        Integer paperTotalScore,
        LocalDateTime updateTime) {
}

package com.chua.starter.eduai.model.vo;

/**
 * 撤销阅卷「完成」的结果 VO。
 *
 * <p>撤销用于误点或改分后重新归位：删掉本次登记的错题、复位完成标记。</p>
 *
 * @param taskId      任务标识
 * @param studentId   该卷归属学生（可为空：未识别学生的卷本就没有登记过错题）
 * @param removed     实际删除的错题条数
 * @param wasFinished 调用前是否处于「已完成」状态（false 表示本来就是未完成，属幂等空操作）
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingUndoResultVO(
        String taskId,
        String studentId,
        Integer removed,
        Boolean wasFinished) {
}

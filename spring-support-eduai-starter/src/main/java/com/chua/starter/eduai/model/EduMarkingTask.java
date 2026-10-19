package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 阅卷任务实体（异步任务的状态载体）。
 *
 * <p>承载「创建任务 → 有状态 → 后台处理 → 重启续跑」：接口只落库并返回 taskId，
 * 后台线程读取任务处理；进程重启后按 {@code status in (0,1)} 重新入队。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_marking_task")
public class EduMarkingTask extends SysBase {

    /**
     * 任务状态：待处理
     */
    public static final int STATUS_PENDING = 0;

    /**
     * 任务状态：处理中（可能因进程重启而中断，故必须可重入）
     */
    public static final int STATUS_PROCESSING = 1;

    /**
     * 任务状态：已完成
     */
    public static final int STATUS_DONE = 2;

    /**
     * 任务状态：失败
     */
    public static final int STATUS_FAILED = 3;

    /**
     * 任务自增标识
     */
    @TableId(type = IdType.AUTO)
    private Long markingTaskId;

    /**
     * 任务业务标识（对外返回，结果按它查询）
     */
    private String markingTaskTaskId;

    /**
     * 状态：0-待处理 1-处理中 2-已完成 3-失败
     */
    private Integer markingTaskStatus;

    /**
     * 试卷类型：SELF 自有卷 / ANY 任意卷
     */
    private String markingTaskMode;

    /**
     * 自有卷对应的试卷 ID
     */
    private Long markingTaskPaperId;

    /**
     * 考试 ID（用于取卷面分值）
     */
    private Long markingTaskExamId;

    /**
     * 学生 ID（留空时由服务端兜底为 anonymous）
     */
    private String markingTaskStudentId;

    /**
     * 任意卷每题满分
     */
    private Integer markingTaskMaxScore;

    /**
     * 上传图片的落盘路径（续跑时读取）
     */
    private String markingTaskImagePath;

    /**
     * 生成的答题卡 ID
     */
    private Long markingTaskAnswerSheetId;

    /**
     * 当前阶段（识别中 / 判分中 2/3 等，供前端展示进度）
     */
    private String markingTaskStage;

    /**
     * 识别出的题目数
     */
    private Integer markingTaskTotal;

    /**
     * 已判分题目数
     */
    private Integer markingTaskDoneCount;

    /**
     * 失败原因
     */
    private String markingTaskError;

    /**
     * 开始处理时间
     */
    private LocalDateTime markingTaskStartTime;

    /**
     * 处理完成时间
     */
    private LocalDateTime markingTaskFinishTime;

    /**
     * 老师是否已点「阅卷完成」：0-未完成 1-已完成。
     *
     * <p>与 {@link #markingTaskStatus} 的「处理完成（后台判分结束）」不是一回事：
     * 本字段表示老师已复核收口，错题已按最终分归位；「完成」动作据此保证幂等。</p>
     */
    private Integer markingTaskTeacherFinished;

    /**
     * 老师点「阅卷完成」的时间
     */
    private LocalDateTime markingTaskTeacherFinishTime;

    /**
     * AI 评语满意度（1=差 2=一般 3=良好 4=优秀 5=极好；null 表示未评价）。
     *
     * <p>教师确认阅卷时可提交，累积数据用于 Prompt 优化。</p>
     */
    private Integer markingTaskSatisfaction;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("marking_task_owner_user_id")
    private String ownerUserId;

    /**
     * 学生姓名（非库字段：列表展示「学号 + 姓名」用）。
     *
     * <p>来源顺序：答题卡 content 里卷面识别的姓名（ANY 链路扫描时模型/正则
     * 从卷头「姓名/学号」栏抽取）优先；没有则按 {@link #markingTaskStudentId}
     * 查 {@code edu_student.student_name}。两者都没有时留空，
     * 前端只显示学号。</p>
     */
    @TableField(exist = false)
    private String markingTaskStudentName;
}
package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduWrongQuestion;
import com.chua.starter.eduai.model.dto.WrongQueryDTO;
import com.chua.starter.eduai.model.dto.ReviewPaperDTO;
import com.chua.starter.eduai.model.vo.WrongQuestionVO;
import com.chua.starter.eduai.model.vo.WrongSourceVO;

import java.util.List;

/**
 * 错题库服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduWrongQuestionService extends IService<EduWrongQuestion> {

    /**
     * 记录错题
     *
     * <p>阅卷消费线程取不到登录身份，归属必须由调用方从父行（答题卡）继承后传入。</p>
     *
     * @param studentId   学生标识
     * @param questionId  题目标识
     * @param examId      考试标识
     * @param ownerUserId 归属用户标识
     */
    void recordWrongQuestion(String studentId, Long questionId, Long examId, String ownerUserId);

    /**
     * 记录错题（带试卷归因）。
     *
     * <p>与四参重载的区别：额外落 {@code wrong_question_paper_id}，
     * 让错题能回答「来自哪张试卷」。老师点「阅卷完成」时走本重载。</p>
     *
     * @param studentId   学生标识
     * @param questionId  题目标识
     * @param examId      考试标识
     * @param ownerUserId 归属用户标识
     * @param paperId     试卷标识（可空：任意卷没有对应试卷）
     */
    void recordWrongQuestion(String studentId, Long questionId, Long examId, String ownerUserId, Long paperId);

    /**
     * 记录错题（带试卷归因与来源任务）。
     *
     * <p>来源任务落 {@code wrong_question_source_task_id}，「撤销完成」据此
     * 只删除本次登记的错题行，不误伤历史。</p>
     *
     * @param studentId    学生标识
     * @param questionId   题目标识
     * @param examId       考试标识
     * @param ownerUserId  归属用户标识
     * @param paperId      试卷标识（可空）
     * @param sourceTaskId 来源阅卷任务（可空）
     */
    void recordWrongQuestion(String studentId, Long questionId, Long examId, String ownerUserId,
                             Long paperId, String sourceTaskId);

    /**
     * 删除某个阅卷任务登记过的错题（「撤销完成」用）。
     *
     * <p>只删 {@code wrong_question_source_task_id = taskId} 的行：
     * 同一道题若也被别的任务登记过，那一次的记录保留。</p>
     *
     * @param sourceTaskId 来源阅卷任务标识
     * @return 实际删除的行数
     */
    int removeBySourceTask(String sourceTaskId);

    /**
     * 分页查询错题
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<WrongQuestionVO> pageQuery(WrongQueryDTO query);

    /**
     * 生成复习卷
     *
     * @param dto 复习卷配置
     * @return 题目ID列表
     */
    List<Long> generateReviewPaper(ReviewPaperDTO dto);

    /**
     * 更新掌握度
     *
     * @param wrongId      错题记录标识
     * @param masteryLevel 掌握度
     * @return 是否成功
     */
    boolean updateMastery(Long wrongId, Integer masteryLevel);

    /**
     * 一键订正错题。
     *
     * <p>订正是老师讲解后学生在错题卡上重新作答的客观动作，
     * 与掌握度（主观自评）独立。标记后 {@code wrong_question_corrected=1}，
     * 并记录最近订正时间；幂等（已订正重复调用仍返回 true）。</p>
     *
     * @param wrongId 错题记录标识
     * @return 是否成功
     */
    boolean markCorrected(Long wrongId);

    /**
     * 错题来源任务列表（溯源）。
     *
     * <p>错题按 (学生, 题目) 唯一，同一题可能被多张卷、多次「完成」登记。
     * 本接口返回该错题关联到的来源任务及其完成时间，供前端展示溯源时间线。</p>
     *
     * @param wrongId 错题记录标识
     * @return 来源任务列表（按完成时间倒序）
     */
    List<WrongSourceVO> listSourceTasks(Long wrongId);
}

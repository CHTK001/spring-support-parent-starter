package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduMarkingResult;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 阅卷结果 Mapper。
 *
 * @author CH
 * @since 4.0.0.51
 */

@Mapper
public interface EduMarkingResultMapper extends BaseMapper<EduMarkingResult> {

    /**
     * 按任务标识取阅卷结果行（XML 语句 selectByTaskId，无归属隔离，队列/同步共用）。
     *
     * @param taskId 阅卷任务标识
     * @return 阅卷结果列表
     */
    List<EduMarkingResult> selectByTaskId(@Param("taskId") String taskId);

    /**
     * 按任务标识取阅卷结果行（XML 语句 selectTaskResults，含归属隔离，面向用户）。
     *
     * @param taskId 阅卷任务标识
     * @param scope  归属隔离参数
     * @return 阅卷结果列表
     */
    List<EduMarkingResult> selectTaskResults(@Param("taskId") String taskId,
                                             @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 把单题 AI 批改结果写入库（XML 语句 updateAiOutcome：AI/统一得分评语 + 状态置为 AI 已批改 + 更新时间）。
     *
     * @param taskId     阅卷任务标识
     * @param questionId 题目标识
     * @param aiScore    AI 得分
     * @param aiComment  AI 评语
     * @param now        更新时刻
     * @return 更新行数
     */
    int updateAiOutcome(@Param("taskId") String taskId,
                       @Param("questionId") Long questionId,
                       @Param("aiScore") Integer aiScore,
                       @Param("aiComment") String aiComment,
                       @Param("now") LocalDateTime now);

    /**
     * 把扫描阅卷的单题结果写入库（XML 语句 updateScanOutcome）。
     *
     * <p>与 {@link #updateAiOutcome} 同口径（AI/统一得分评语 + 状态置为 AI 已批改），
     * 额外写入 {@code marking_result_detail} —— 该列原本闲置，这里用来保存
     * 识别到的学生作答、对错判定、来源与识别题号。既有的表结构没有「学生作答」列，
     * 用该 JSON 列承载可避免为此单独改表。</p>
     *
     * @param taskId     阅卷任务标识
     * @param questionId 题目标识（任意卷题库无对应记录时写 0）
     * @param aiScore    AI 得分
     * @param aiComment  AI 评语
     * @param detail     批改详情 JSON
     * @param now        更新时刻
     * @return 更新行数
     */
    int updateScanOutcome(@Param("taskId") String taskId,
                          @Param("questionId") Long questionId,
                          @Param("aiScore") Integer aiScore,
                          @Param("aiComment") String aiComment,
                          @Param("detail") String detail,
                          @Param("now") LocalDateTime now);

    /**
     * 按【结果行主键】写入扫描阅卷的单题结果（XML 语句 updateScanOutcomeById）。
     *
     * <p><b>为什么必须按主键而不是 (taskId, questionId)</b>：任意卷没有题库记录，
     * 题目标识统一记 0，若按 (taskId, questionId) 更新，一次 UPDATE 会命中该任务下
     * 所有 question_id=0 的行，导致多道题被最后一批数据覆盖（实测 3 行 detail 完全相同）。
     * 按行主键更新可保证每行独立。</p>
     *
     * @param resultId  阅卷结果行主键
     * @param aiScore   AI 得分
     * @param aiComment AI 评语
     * @param detail    批改详情 JSON
     * @param now       更新时刻
     * @return 更新行数
     */
    int updateScanOutcomeById(@Param("resultId") Long resultId,
                              @Param("aiScore") Integer aiScore,
                              @Param("aiComment") String aiComment,
                              @Param("detail") String detail,
                              @Param("now") LocalDateTime now);

    /**
     * 把单题标记为「AI 判分失败」（XML 语句 markResultFailed）。
     *
     * <p>与 {@link #updateScanOutcomeById} 的本质区别：失败<b>不是</b> 0 分。
     * 得分与 AI 得分显式置 NULL（得分未知），状态写成 {@code 3}，评语保留失败原因；
     * 老师后续可「重判失败题」或手动给分（教师修正会把状态改回 2）。</p>
     *
     * <p>历史事故：失败题曾按 0 分 + 状态 1（AI 已批改）落库，学生白丢分、
     * 老师误以为已判过；且完成阅卷时按 0 分误登错题。</p>
     *
     * @param resultId 阅卷结果行主键
     * @param comment  失败原因（写给老师看，不得直出内部异常细节）
     * @param detail   批改详情 JSON（保留识别到的题干与作答，供重判/人工复核）
     * @param now      更新时刻
     * @return 更新行数
     */
    int markResultFailed(@Param("resultId") Long resultId,
                         @Param("comment") String comment,
                         @Param("detail") String detail,
                         @Param("now") LocalDateTime now);
}

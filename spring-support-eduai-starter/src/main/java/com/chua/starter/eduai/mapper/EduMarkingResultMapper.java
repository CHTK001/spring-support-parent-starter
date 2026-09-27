package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduMarkingResult;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @作者 CH
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
}

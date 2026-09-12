package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduWrongQuestion;
import com.chua.starter.eduai.model.dto.WrongQueryDTO;
import com.chua.starter.eduai.model.dto.ReviewPaperDTO;
import com.chua.starter.eduai.model.vo.WrongQuestionVO;

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
    * @param studentId  学生标识
    * @param questionId 题目标识
    * @param examId     考试标识
     */
    void recordWrongQuestion(String studentId, Long questionId, Long examId);

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
}
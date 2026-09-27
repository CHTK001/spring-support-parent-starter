package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduPaperQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 试卷-题目关联 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduPaperQuestionMapper extends BaseMapper<EduPaperQuestion> {

    /**
     * 取试卷下的题目关联（XML 语句 selectByPaperId，按题目顺序升序）。
     *
     * @param paperId 试卷标识
     * @return 关联列表
     */
    List<EduPaperQuestion> selectByPaperId(@Param("paperId") Long paperId);

    /**
     * 取试卷下各题的分值映射（XML 语句 selectByPaperIdForScores）。
     *
     * @param paperId 试卷标识
     * @return 关联列表
     */
    List<EduPaperQuestion> selectByPaperIdForScores(@Param("paperId") Long paperId);

    /**
     * 删除试卷下指定题目关联（XML 语句 deleteByPaperAndQuestion）。
     *
     * @param paperId    试卷标识
     * @param questionId 题目标识
     * @return 删除行数
     */
    int deleteByPaperAndQuestion(@Param("paperId") Long paperId, @Param("questionId") Long questionId);

    /**
     * 取试卷下指定题目的关联（XML 语句 selectByPaperAndQuestion，移题前校验存在）。
     *
     * @param paperId    试卷标识
     * @param questionId 题目标识
     * @return 关联实体，不存在返回 null
     */
    EduPaperQuestion selectByPaperAndQuestion(@Param("paperId") Long paperId, @Param("questionId") Long questionId);

    /**
     * 按题项唯一编号取关联（XML 语句 selectByQuestionNo，二维码反查用）。
     *
     * @param questionNo 题项唯一编号
     * @return 关联实体，不存在返回 null
     */
    EduPaperQuestion selectByQuestionNo(@Param("questionNo") String questionNo);

    /**
     * 把试卷下所有题项的版本号同步为最新版本（XML 语句 updateVersionByPaperId）。
     *
     * @param paperId 试卷标识
     * @param version 最新版本号
     * @return 更新行数
     */
    int updateVersionByPaperId(@Param("paperId") Long paperId, @Param("version") Integer version);
}

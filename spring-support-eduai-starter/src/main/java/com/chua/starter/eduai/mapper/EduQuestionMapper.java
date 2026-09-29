package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.QuestionQueryDTO;
import com.chua.starter.eduai.model.vo.QuestionGradeDimensionVO;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 题目 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduQuestionMapper extends BaseMapper<EduQuestion> {

    /**
     * 分页查询题目（XML 语句 pageSelect）。
     *
     * @param page  分页参数
     * @param query 查询条件
     * @param scope 归属隔离参数
     * @return 题目分页
     */
    IPage<EduQuestion> pageSelect(Page<EduQuestion> page, @Param("query") QuestionQueryDTO query,
                                  @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 学段年级组合维度统计（XML 语句 selectGradeDimensions）。
     *
     * @param status 题目状态，为空时只统计未停用题目
     * @param scope  归属隔离参数
     * @return 组合维度列表
     */
    List<QuestionGradeDimensionVO> selectGradeDimensions(@Param("status") Integer status,
                                                         @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 统计同题干同学科的有效题目数（XML 语句 countSameQuestion）。
     *
     * @param stem        题干
     * @param subject     学科
     * @param ownerUserId 归属用户，为空时不限定归属
     * @return 题目数
     */
    long countSameQuestion(@Param("stem") String stem, @Param("subject") String subject,
                           @Param("ownerUserId") String ownerUserId);

    /**
     * 题目被加入试卷的次数 +1（XML 语句 incrAddCount）。
     *
     * <p>用 {@code COALESCE(question_add_count, 0) + 1} 保证该列历史为 NULL 时也能累加。</p>
     *
     * @param questionId 题目标识
     * @return 受影响行数
     */
    int incrAddCount(@Param("questionId") Long questionId);

    /**
     * 组卷候选题目取样（XML 语句 selectCandidates）。
     *
     * @param subject    学科
     * @param gradeLevel 学段，为空时不限学段
     * @param scope      归属隔离参数
     * @param limit      取样条数
     * @return 候选题目列表
     */
    List<EduQuestion> selectCandidates(@Param("subject") String subject,
                                       @Param("gradeLevel") String gradeLevel,
                                       @Param("scope") EduUserContext.OwnerScope scope,
                                       @Param("limit") int limit);
}

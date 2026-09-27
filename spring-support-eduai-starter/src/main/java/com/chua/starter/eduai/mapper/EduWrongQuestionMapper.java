package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.eduai.model.EduWrongQuestion;
import com.chua.starter.eduai.model.dto.WrongQueryDTO;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 错题记录 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduWrongQuestionMapper extends BaseMapper<EduWrongQuestion> {

    /**
     * 按学生/题目/归属取错题记录（XML 语句 selectOneByStudentQuestionOwner）。
     *
     * @param studentId   学生标识
     * @param questionId  题目标识
     * @param ownerUserId 归属用户标识
     * @return 错题记录，不存在时返回 {@code null}
     */
    EduWrongQuestion selectOneByStudentQuestionOwner(@Param("studentId") String studentId,
                                                     @Param("questionId") Long questionId,
                                                     @Param("ownerUserId") String ownerUserId);

    /**
     * 分页查询错题（XML 语句 pageSelect，学科条件走题目子查询）。
     *
     * @param page  分页参数
     * @param query 查询条件
     * @param scope 归属隔离参数
     * @return 错题分页
     */
    IPage<EduWrongQuestion> pageSelect(Page<EduWrongQuestion> page, @Param("query") WrongQueryDTO query,
                                        @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 复习卷取题：按学生取未掌握错题并限制条数（XML 语句 selectReviewQuestions）。
     *
     * @param studentId 学生标识
     * @param count     取题条数
     * @param scope     归属隔离参数
     * @return 错题记录列表
     */
    List<EduWrongQuestion> selectReviewQuestions(@Param("studentId") String studentId,
                                                 @Param("count") int count,
                                                 @Param("scope") EduUserContext.OwnerScope scope);
}

package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.QuestionQueryDTO;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.model.vo.QuestionGradeDimensionVO;

import java.util.List;

/**
 * 题目服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduQuestionService extends IService<EduQuestion> {

    /**
     * 分页查询题目
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<EduQuestion> pageQuery(QuestionQueryDTO query);

    /**
     * 保存题目（新增或更新）
     *
     * @param dto 题目数据
     * @return 题目ID
     */
    Long saveQuestion(QuestionSaveDTO dto);

    /**
     * 批量导入题目
     *
     * @param list 题目列表
     * @return 导入数量
     */
    int importQuestions(List<QuestionSaveDTO> list);

    /**
     * 删除题目
     *
     * @param id 题目标识
     * @return 是否成功
     */
    boolean deleteQuestion(Long id);

    /**
     * 列出题库中真实存在的"学段 + 年级"组合，供前端级联筛选取值。
     *
     * @param status 题目状态；为空时只统计未停用（status&lt;&gt;0）的题目
     * @return 组合维度列表
     */
    List<QuestionGradeDimensionVO> listGradeDimensions(Integer status);
}

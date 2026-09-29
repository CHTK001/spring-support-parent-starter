package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.QuestionAiSelectDTO;
import com.chua.starter.eduai.model.dto.QuestionQueryDTO;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.model.vo.QuestionGradeDimensionVO;
import com.chua.starter.eduai.service.IEduQuestionService;
import com.chua.starter.eduai.support.util.EduUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题目管理控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/question")
@RequiredArgsConstructor
@Tag(name = "题目管理")
public class EduQuestionController {

    /**
     * Question服务
    */
    private final IEduQuestionService questionService;

    /**
     * 分页查询题目
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询题目")
    @GetMapping("/page")
    public ReturnResult<PageResult<EduQuestion>> page(QuestionQueryDTO query) {
        PageResult<EduQuestion> result = questionService.pageQuery(query);
        return ReturnResult.ok(result);
    }

    /**
     * 列出题库中真实存在的学段与年级组合，供前端级联筛选取候选项
     *
     * @param status 题目状态，为空时只统计未停用的题目
     * @return 组合维度列表
     */
    @Operation(summary = "学段年级组合维度")
    @GetMapping("/grade-dimensions")
    public ReturnResult<List<QuestionGradeDimensionVO>> gradeDimensions(
            @RequestParam(required = false) Integer status) {
        return ReturnResult.ok(questionService.listGradeDimensions(status));
    }

    /**
     * 获取题目详情
     *
     * @param id 题目标识
     * @return 题目详情
     */
    @Operation(summary = "获取题目详情")
    @GetMapping("/{id}")
    public ReturnResult<EduQuestion> detail(@PathVariable Long id) {
        EduQuestion question = questionService.getById(id);
        if (question != null) {
            EduUserContext.checkReadable(question.getOwnerUserId());
        }
        return ReturnResult.ok(question);
    }

    /**
     * 新增或更新题目
     *
     * @param dto 题目数据
     * @return 题目ID
     */
    @Operation(summary = "新增或更新题目")
    @PostMapping("/save")
    public ReturnResult<Long> save(@RequestBody QuestionSaveDTO dto) {
        Long id = questionService.saveQuestion(dto);
        return ReturnResult.ok(id);
    }

    /**
     * 批量导入题目
     *
     * @param list 题目列表
     * @return 导入数量
     */
    @Operation(summary = "批量导入题目")
    @PostMapping("/import")
    public ReturnResult<Integer> importQuestions(@RequestBody List<QuestionSaveDTO> list) {
        int count = questionService.importQuestions(list);
        return ReturnResult.ok(count);
    }

    /**
     * 删除题目（逻辑删除）
     *
     * @param id 题目标识
     * @return 是否成功
     */
    @Operation(summary = "删除题目")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        boolean success = questionService.deleteQuestion(id);
        return ReturnResult.ok(success);
    }

    /**
     * AI 选题：按条件向 AI 要一批题目，供组卷页往已存在的试卷里补题。
     *
     * <p>AI 能力依赖 spring-support-ai-starter；不可用时服务端回退到规则选题，
     * 调用方应先用 {@code GET /v2/edu/ai/capability} 判断是否展示 AI 入口。</p>
     *
     * @param dto 选题条件
     * @return 题目列表，保持 AI 给出的顺序
     */
    @Operation(summary = "AI 选题")
    @PostMapping("/ai-select")
    public ReturnResult<List<EduQuestion>> aiSelect(@RequestBody QuestionAiSelectDTO dto) {
        return ReturnResult.ok(questionService.aiSelect(dto));
    }
}

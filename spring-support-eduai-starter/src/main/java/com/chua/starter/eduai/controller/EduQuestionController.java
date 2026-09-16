package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.QuestionQueryDTO;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.service.IEduQuestionService;
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

    /** Question服务 */
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
    * 获取题目详情
    *
    * @param id 题目标识
    * @return 题目详情
     */
    @Operation(summary = "获取题目详情")
    @GetMapping("/{id}")
    public ReturnResult<EduQuestion> detail(@PathVariable Long id) {
        EduQuestion question = questionService.getById(id);
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
}
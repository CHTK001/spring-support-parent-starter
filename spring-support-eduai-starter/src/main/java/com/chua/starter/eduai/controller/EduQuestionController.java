package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.QuestionAiSelectDTO;
import com.chua.starter.eduai.model.dto.QuestionQueryDTO;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.model.vo.QuestionGradeDimensionVO;
import com.chua.starter.eduai.model.vo.QuestionImportResultVO;
import com.chua.starter.eduai.service.IEduQuestionService;
import com.chua.starter.eduai.support.util.EduUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
     * 题库统计：总数 / 待审 / 今日新增 / 学科数（供管理端统计卡）。
     *
     * @return 统计 map
     */
    @Operation(summary = "题库统计")
    @GetMapping("/stats")
    public ReturnResult<Map<String, Object>> stats() {
        return ReturnResult.ok(questionService.questionStats());
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
        if (id == null) {
            throw new BusinessException("id 不能为空");
        }
        EduQuestion question = questionService.getById(id);
        if (question != null) {
            EduUserContext.checkReadable(question.getOwnerUserId());
        }
        if (question == null) {
            throw new BusinessException("题目不存在");
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
        if (dto == null) {
            throw new BusinessException("题目内容不能为空");
        }
        Long id = questionService.saveQuestion(dto);
        return ReturnResult.ok(id);
    }

    /**
     * 批量导入题目（逐条容错，返回成功/失败条数与逐条失败原因）
     *
     * @param list 题目列表
     * @return 导入结果
     */
    @Operation(summary = "批量导入题目")
    @PostMapping("/import")
    public ReturnResult<QuestionImportResultVO> importQuestions(@RequestBody List<QuestionSaveDTO> list) {
        return ReturnResult.ok(questionService.importQuestions(list));
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

    /**
     * 审核单条题目（通过/驳回）：仅管理员可调。
     *
     * @param id     题目标识
     * @param passed 是否通过
     * @param remark 驳回理由（驳回必填，通过时忽略）
     * @return 是否成功
     */
    @Operation(summary = "审核题目")
    @PostMapping("/audit/{id}")
    public ReturnResult<Boolean> audit(@PathVariable Long id,
                                       @RequestParam boolean passed,
                                       @RequestParam(required = false) String remark) {
        questionService.auditQuestion(id, passed, remark);
        return ReturnResult.ok(true);
    }

    /**
     * 批量审核题目（通过/驳回）：仅管理员可调。
     *
     * @param ids    题目ID列表
     * @param passed 是否通过
     * @param remark 驳回理由（驳回必填，通过时忽略）
     * @return 成功条数
     */
    @Operation(summary = "批量审核题目")
    @PostMapping("/audit-batch")
    public ReturnResult<Integer> auditBatch(@RequestBody List<Long> ids,
                                            @RequestParam boolean passed,
                                            @RequestParam(required = false) String remark) {
        // 单次批量上限，防止内存溢出与超时
        if (ids != null && ids.size() > 100) {
            throw new BusinessException("单次批量审核不超过 100 条");
        }
        return ReturnResult.ok(questionService.auditQuestions(ids, passed, remark));
    }

    /**
     * 题目导出为 CSV 文件。
     *
     * <p>按当前筛选条件导出题目，UTF-8 编码。最大导出 10000 条，防止大结果集。</p>
     *
     * @param keyword      关键字过滤（题干）
     * @param subject     学科过滤
     * @param gradeLevel  学段过滤
     * @param questionType 题型过滤
     * @param auditStatus 审核状态（null=全部）
     * @param limit       最大条数（默认 5000，上限 10000）
     * @param response    HTTP 响应
     */
    @Operation(summary = "导出题目（CSV）")
    @GetMapping("/export")
    public void exportQuestions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(defaultValue = "5000") int limit,
            HttpServletResponse response) throws Exception {
        String csv = questionService.exportQuestions(keyword, subject, gradeLevel, questionType, auditStatus, limit);
        String filename = "questions_" + System.currentTimeMillis() + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        response.getWriter().write(csv);
    }
}

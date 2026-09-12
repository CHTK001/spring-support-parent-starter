package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.dto.PaperGenerateDTO;
import com.chua.starter.eduai.model.dto.PaperQueryDTO;
import com.chua.starter.eduai.model.vo.PaperVO;
import com.chua.starter.eduai.service.IEduPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 试卷管理控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/paper")
@RequiredArgsConstructor
@Tag(name = "试卷管理")
public class EduPaperController {

    /** Paper服务 */
    private final IEduPaperService paperService;

    /**
     * 分页查询试卷
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询试卷")
    @GetMapping("/page")
    public ReturnResult<PageResult<PaperVO>> page(PaperQueryDTO query) {
        return ReturnResult.ok(paperService.pageQuery(query));
    }

    /**
     * 获取试卷详情
     *
     * @param id 试卷标识
     * @return 试卷详情
     */
    @Operation(summary = "获取试卷详情")
    @GetMapping("/{id}")
    public ReturnResult<PaperVO> detail(@PathVariable Long id) {
        return ReturnResult.ok(paperService.getPaperDetail(id));
    }

    /**
     * AI生成试卷
     *
     * @param dto 组卷配置
     * @return 试卷ID
     */
    @Operation(summary = "AI生成试卷")
    @PostMapping("/generate")
    public ReturnResult<Long> generate(@RequestBody PaperGenerateDTO dto) {
        return ReturnResult.ok(paperService.generatePaper(dto));
    }

    /**
     * 替换试卷中的某道题
     *
     * @param id    试卷标识
     * @param oldId 旧题目标识
     * @param newId 新题目标识
     * @return 是否成功
     */
    @Operation(summary = "替换试卷中的某道题")
    @PutMapping("/{id}/replace")
    public ReturnResult<Boolean> replace(@PathVariable Long id, @RequestParam Long oldId, @RequestParam Long newId) {
        return ReturnResult.ok(paperService.replaceQuestion(id, oldId, newId));
    }
}
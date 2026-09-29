package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.dto.PaperComposeUpdateDTO;
import com.chua.starter.eduai.model.dto.PaperGenerateDTO;
import com.chua.starter.eduai.model.dto.PaperQueryDTO;
import com.chua.starter.eduai.model.dto.PaperQuestionItemDTO;
import com.chua.starter.eduai.model.vo.PaperHistoryVO;
import com.chua.starter.eduai.model.vo.PaperQuestionQrVO;
import com.chua.starter.eduai.model.vo.PaperVO;
import com.chua.starter.eduai.service.IEduPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * Paper服务
    */
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

    /**
     * 新建空白试卷（手动组卷）
     *
     * @param dto 卷头信息，可为空
     * @return 试卷ID
     */
    @Operation(summary = "新建空白试卷")
    @PostMapping
    public ReturnResult<Long> create(@RequestBody(required = false) PaperComposeUpdateDTO dto) {
        return ReturnResult.ok(paperService.createManualPaper(dto));
    }

    /**
     * 批量加题
     *
     * @param id    试卷标识
     * @param items 加题项列表（题目ID + 分值）
     * @return 实际加入的题目数量
     */
    @Operation(summary = "批量加题")
    @PostMapping("/{id}/questions")
    public ReturnResult<Integer> addQuestions(@PathVariable Long id, @RequestBody List<PaperQuestionItemDTO> items) {
        return ReturnResult.ok(paperService.addQuestions(id, items));
    }

    /**
     * 移除试卷中的题目
     *
     * @param id  试卷标识
     * @param qid 题目标识
     * @return 是否成功
     */
    @Operation(summary = "移除试卷中的题目")
    @DeleteMapping("/{id}/questions/{qid}")
    public ReturnResult<Boolean> removeQuestion(@PathVariable Long id, @PathVariable Long qid) {
        return ReturnResult.ok(paperService.removeQuestion(id, qid));
    }

    /**
     * 按题项编号顺序重排题目
     *
     * @param id          试卷标识
     * @param questionNos 题项唯一编号列表
     * @return 是否成功
     */
    @Operation(summary = "题目排序")
    @PutMapping("/{id}/questions/sort")
    public ReturnResult<Boolean> sortQuestions(@PathVariable Long id, @RequestBody List<String> questionNos) {
        return ReturnResult.ok(paperService.sortQuestions(id, questionNos));
    }

    /**
     * 按题数均分 100 分
     *
     * @param id 试卷标识
     * @return 是否成功
     */
    @Operation(summary = "按题数均分分值")
    @PostMapping("/{id}/questions/even")
    public ReturnResult<Boolean> evenQuestions(@PathVariable Long id) {
        return ReturnResult.ok(paperService.evenQuestionScores(id));
    }

    /**
     * 更新试卷卷头/纸张/模板/页头页脚
     *
     * @param id  试卷标识
     * @param dto 更新内容
     * @return 更新后的试卷详情
     */
    @Operation(summary = "更新试卷信息")
    @PutMapping("/{id}")
    public ReturnResult<PaperVO> update(@PathVariable Long id, @RequestBody PaperComposeUpdateDTO dto) {
        return ReturnResult.ok(paperService.updatePaper(id, dto));
    }

    /**
     * 发布试卷
     *
     * @param id 试卷标识
     * @return 是否成功
     */
    @Operation(summary = "发布试卷")
    @PutMapping("/{id}/publish")
    public ReturnResult<Boolean> publish(@PathVariable Long id) {
        return ReturnResult.ok(paperService.publishPaper(id));
    }

    /**
     * 下架试卷（已发布 → 草稿）
     *
     * @param id 试卷标识
     * @return 是否成功
     */
    @Operation(summary = "下架试卷")
    @PutMapping("/{id}/unpublish")
    public ReturnResult<Boolean> unpublish(@PathVariable Long id) {
        return ReturnResult.ok(paperService.unpublishPaper(id));
    }

    /**
     * 归档试卷（草稿/已发布 → 已归档，之后只读）
     *
     * @param id 试卷标识
     * @return 是否成功
     */
    @Operation(summary = "归档试卷")
    @PutMapping("/{id}/archive")
    public ReturnResult<Boolean> archive(@PathVariable Long id) {
        return ReturnResult.ok(paperService.archivePaper(id));
    }

    /**
     * 试卷历史列表
     *
     * @param id 试卷标识
     * @return 历史列表
     */
    @Operation(summary = "试卷历史列表")
    @GetMapping("/{id}/history")
    public ReturnResult<List<PaperHistoryVO>> history(@PathVariable Long id) {
        return ReturnResult.ok(paperService.listHistory(id));
    }

    /**
     * 历史快照详情
     *
     * @param hid 历史标识
     * @return 历史详情
     */
    @Operation(summary = "历史快照详情")
    @GetMapping("/history/{hid}")
    public ReturnResult<PaperHistoryVO> historyDetail(@PathVariable Long hid) {
        return ReturnResult.ok(paperService.getHistoryDetail(hid));
    }

    /**
     * 按题项唯一编号反查
     *
     * @param no 题项唯一编号
     * @return 题项反查结果
     */
    @Operation(summary = "题项二维码反查")
    @GetMapping("/qr/{no}")
    public ReturnResult<PaperQuestionQrVO> qr(@PathVariable String no) {
        return ReturnResult.ok(paperService.queryQuestionByNo(no));
    }
}

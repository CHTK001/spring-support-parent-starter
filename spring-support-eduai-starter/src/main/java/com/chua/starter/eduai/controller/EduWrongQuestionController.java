package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.dto.ReviewPaperDTO;
import com.chua.starter.eduai.model.dto.WrongQueryDTO;
import com.chua.starter.eduai.model.vo.WrongQuestionVO;
import com.chua.starter.eduai.model.vo.WrongSourceVO;
import com.chua.starter.eduai.service.IEduWrongQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 错题库管理控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/wrongbook")
@RequiredArgsConstructor
@Tag(name = "错题库管理")
public class EduWrongQuestionController {

    /**
     * Wrong服务
    */
    private final IEduWrongQuestionService wrongService;

    /**
     * 分页查询错题
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询错题")
    @GetMapping("/page")
    public ReturnResult<PageResult<WrongQuestionVO>> page(WrongQueryDTO query) {
        return ReturnResult.ok(wrongService.pageQuery(query));
    }

    /**
     * 生成复习卷
     *
     * @param dto 复习卷配置
     * @return 题目ID列表
     */
    @Operation(summary = "生成复习卷")
    @PostMapping("/review")
    public ReturnResult<List<Long>> generateReview(@RequestBody ReviewPaperDTO dto) {
        return ReturnResult.ok(wrongService.generateReviewPaper(dto));
    }

    /**
     * 更新掌握度
     *
     * @param wrongId      错题记录标识
     * @param masteryLevel 掌握度
     * @return 是否成功
     */
    @Operation(summary = "更新掌握度")
    @PutMapping("/mastery")
    public ReturnResult<Boolean> updateMastery(@RequestParam Long wrongId, @RequestParam Integer masteryLevel) {
        return ReturnResult.ok(wrongService.updateMastery(wrongId, masteryLevel));
    }

    /**
     * 一键订正错题。
     *
     * <p>订正是老师讲解后学生在错题卡上重新作答的客观动作，
     * 与掌握度（主观自评）独立。标记后 {@code wrong_question_corrected=1}，
     * 并记录最近订正时间；幂等（已订正重复调用仍返回 true）。</p>
     *
     * @param wrongId 错题记录标识
     * @return 是否成功
     */
    @Operation(summary = "标记错题已订正")
    @PutMapping("/correct/{wrongId}")
    public ReturnResult<Boolean> markCorrected(@PathVariable Long wrongId) {
        return ReturnResult.ok(wrongService.markCorrected(wrongId));
    }

    /**
     * 错题来源任务列表（溯源）。
     *
     * <p>错题按 (学生, 题目) 唯一聚合，同一题可能被多张卷、多次「完成」登记。
     * 本接口返回该错题关联到的来源任务及其完成时间，供前端展示溯源时间线。</p>
     *
     * @param wrongId 错题记录标识
     * @return 来源任务列表（按完成时间倒序）
     */
    @Operation(summary = "错题来源任务溯源")
    @GetMapping("/sources/{wrongId}")
    public ReturnResult<List<WrongSourceVO>> sources(@PathVariable Long wrongId) {
        return ReturnResult.ok(wrongService.listSourceTasks(wrongId));
    }
}

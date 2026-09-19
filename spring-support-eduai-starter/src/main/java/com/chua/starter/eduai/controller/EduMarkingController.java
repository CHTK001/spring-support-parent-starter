package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.dto.MarkingConfirmDTO;
import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.service.IEduMarkingService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅卷管理控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/marking")
@RequiredArgsConstructor
@Tag(name = "阅卷管理")
public class EduMarkingController {

    /**
     * Marking服务
    */
    private final IEduMarkingService markingService;

    /**
     * 提交答题卡
     *
     * @param dto 答题卡数据
     * @return 任务ID
     */
    @Operation(summary = "提交答题卡")
    @PostMapping("/submit")
    public ReturnResult<String> submit(@RequestBody MarkingSubmitDTO dto) {
        return ReturnResult.ok(markingService.submitAnswerSheet(dto));
    }

    /**
     * 轮询阅卷结果
     *
     * @param taskId 任务标识
     * @return 阅卷结果
     */
    @Operation(summary = "轮询阅卷结果")
    @GetMapping("/result/{taskId}")
    public ReturnResult<MarkingResultVO> result(@PathVariable String taskId) {
        return ReturnResult.ok(markingService.getResult(taskId));
    }

    /**
     * 教师确认评分
     *
     * @param dto 确认数据
     * @return 是否成功
     */
    @Operation(summary = "教师确认评分")
    @PutMapping("/confirm")
    public ReturnResult<Boolean> confirm(@RequestBody MarkingConfirmDTO dto) {
        return ReturnResult.ok(markingService.confirmMarking(dto));
    }
}

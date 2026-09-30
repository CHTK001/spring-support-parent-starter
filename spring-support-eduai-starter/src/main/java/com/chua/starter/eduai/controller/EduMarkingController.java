package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.exception.BusinessException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
     * 扫描试卷图片并阅卷（自有卷 / 任意卷）。
     *
     * <p>支持两类试卷：{@code mode=SELF} 自有卷（题目在本系统，分值与标准答案回数据库取，准确）、
     * {@code mode=ANY} 任意卷（外来试卷，题干与作答由模型抽取并判定）。</p>
     *
     * @param file      试卷/答题卡图片
     * @param mode      试卷类型：SELF（默认）/ ANY
     * @param paperId   自有卷对应的试卷 ID
     * @param studentId 学生标识
     * @param examId    考试 ID（自有卷用于取卷面分值）
     * @param maxScore  任意卷每题满分
     * @return 任务 ID，用 {@code GET /result/{taskId}} 轮询结果
     */
    @Operation(summary = "扫描试卷图片并阅卷")
    @PostMapping("/scan")
    public ReturnResult<String> scan(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "mode", required = false, defaultValue = "SELF") String mode,
                                     @RequestParam(value = "paperId", required = false) Long paperId,
                                     @RequestParam(value = "studentId", required = false) String studentId,
                                     @RequestParam(value = "examId", required = false) Long examId,
                                     @RequestParam(value = "maxScore", required = false) Integer maxScore) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            return ReturnResult.ok(markingService.scanAndMark(
                    file.getBytes(), mode, paperId, studentId, examId, maxScore));
        } catch (BusinessException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[EduAi] 扫描阅卷失败: mode={}, paperId={}", mode, paperId, e);
            return ReturnResult.error("扫描阅卷失败: " + e.getMessage());
        }
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

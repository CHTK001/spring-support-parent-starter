package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.model.dto.MarkingConfirmDTO;
import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import com.chua.starter.eduai.model.vo.MarkingProgressVO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.model.vo.MarkingFinishResultVO;
import com.chua.starter.eduai.service.IEduMarkingService;
import com.chua.starter.eduai.stream.EduMarkingProgressHub;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Map;

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
     * 阅卷进度 SSE 推送注册表
     */
    private final EduMarkingProgressHub progressHub;

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
     * 扫描试卷图片并阅卷（自有卷 / 任意卷，<b>支持多张图片</b>）。
     *
     * <p>支持两类试卷：{@code mode=SELF} 自有卷（题目在本系统，分值与标准答案回数据库取，准确）、
     * {@code mode=ANY} 任意卷（外来试卷，题干与作答由模型抽取并判定）。</p>
     *
     * <p>一份试卷通常跨多页，因此图片按页顺序可传多张：表单里重复出现同名的 {@code file} 字段即可
     * （单张也兼容）。服务端逐张识别后按顺序拼接文本，再统一结构化与判分。</p>
     *
     * @param files     试卷/答题卡图片，按页顺序（同名 file 字段重复出现）
     * @param mode      试卷类型：SELF（默认）/ ANY
     * @param paperId   自有卷对应的试卷 ID
     * @param studentId 学生标识
     * @param examId    考试 ID（自有卷用于取卷面分值）
     * @param maxScore  任意卷每题满分
     * @return 任务 ID，用 {@code GET /result/{taskId}} 轮询结果
     */
    @Operation(summary = "扫描试卷图片并阅卷（支持多张）")
    @PostMapping("/scan")
    public ReturnResult<String> scan(@RequestParam(value = "file", required = false) MultipartFile[] files,
                                     @RequestParam(value = "mode", required = false, defaultValue = "SELF") String mode,
                                     @RequestParam(value = "paperId", required = false) Long paperId,
                                     @RequestParam(value = "studentId", required = false) String studentId,
                                     @RequestParam(value = "examId", required = false) Long examId,
                                     @RequestParam(value = "maxScore", required = false) Integer maxScore) {
        if (files == null || files.length == 0) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            var images = new ArrayList<byte[]>(files.length);
            for (MultipartFile item : files) {
                if (item != null && !item.isEmpty()) {
                    images.add(item.getBytes());
                }
            }
            if (images.isEmpty()) {
                return ReturnResult.error("上传文件不能为空");
            }
            return ReturnResult.ok(markingService.scanAndMark(
                    images, mode, paperId, studentId, examId, maxScore));
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
     * 获取「与图片同版式」的结构化解析结果。
     *
     * <p>返回逐页的版面区域/文字块坐标与页尺寸，以及题号锚点和判分明细（含知识点），
     * 供前端按原图位置叠放分数标记、对错与知识点，并支持点击编辑。</p>
     *
     * @param taskId 任务标识
     * @return 结构化数据
     */
    @Operation(summary = "获取结构化解析（同版式展示用）")
    @GetMapping("/structure/{taskId}")
    public ReturnResult<Map<String, Object>> structure(@PathVariable String taskId) {
        return ReturnResult.ok(markingService.getStructure(taskId));
    }

    /**
     * 获取阅卷所用答卷图片的指定页（同版式展示的打底图）。
     *
     * @param taskId 任务标识
     * @param page   页码（从 1 开始）
     * @return 图片字节
     */
    @Operation(summary = "获取答卷图片（按页）")
    @GetMapping("/image/{taskId}")
    public ResponseEntity<byte[]> image(@PathVariable String taskId,
                                        @RequestParam(value = "page", defaultValue = "1") int page) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(markingService.getSheetImage(taskId, page));
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

    /**
     * 阅卷完成：把该卷错题按最终分归入对应学生的错题库
     *
     * @param taskId 任务业务标识
     * @return 完成结果（学生 / 试卷 / 本次登记的错题数）
     */
    @Operation(summary = "阅卷完成（错题归到对应学生）")
    @PostMapping("/{taskId}/finish")
    public ReturnResult<MarkingFinishResultVO> finish(@PathVariable String taskId) {
        return ReturnResult.ok(markingService.finishMarking(taskId));
    }

    /**
     * 实时订阅阅卷进度（SSE）。
     *
     * <p>上传答题卡拿到 taskId 后立刻建这条流：识别 / 解析 / 判分每推进一步都会收到一帧
     * {@code progress}，终态（完成或失败）时收到 {@code done} 且服务端主动收流。
     * 建连瞬间先推一帧当前快照，断线重连 / 判完后再订阅都不会空等。</p>
     *
     * @param taskId 任务标识
     * @return SSE 流
     */
    @Operation(summary = "实时订阅阅卷进度（SSE）")
    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String taskId) {
        SseEmitter emitter = progressHub.subscribe(taskId);
        try {
            MarkingProgressVO snapshot = markingService.getProgress(taskId);
            progressHub.sendTo(emitter, "progress", snapshot);
            if (Boolean.TRUE.equals(snapshot.finished())) {
                progressHub.complete(taskId, "done", snapshot);
            }
        } catch (BusinessException e) {
            // 任务不存在：显式告诉前端并收流，避免连接挂到超时前端一直「连接中」
            sendStreamError(taskId, e.getMessage());
        } catch (Exception e) {
            log.error("[EduAi] 建立阅卷进度流失败: taskId={}", taskId, e);
            sendStreamError(taskId, "订阅失败: " + e.getMessage());
        }
        return emitter;
    }

    /**
     * 收流并推送一条终态错误帧。
     *
     * @param taskId  任务标识
     * @param message 错误信息
     */
    private void sendStreamError(String taskId, String message) {
        MarkingProgressVO error = new MarkingProgressVO(
                taskId, null, null, null, null, null, message, true, null, null, null);
        progressHub.complete(taskId, "done", error);
    }
}

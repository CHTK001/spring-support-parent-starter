package com.chua.starter.ai.training.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainProgress;
import com.chua.starter.ai.training.support.domain.TrainTask;
import com.chua.starter.ai.training.support.service.DataService;
import com.chua.starter.ai.training.support.service.TrainService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 训练平台 REST 接口。
 *
 * <p>对应 AIAS 2_training_platform 的 TrainController，提供完整的训练闭环：</p>
 * <ul>
 *   <li>上传训练数据（ZIP）并自动解压</li>
 *   <li>启动异步训练（后台 DL4J ResNet50 迁移学习）</li>
 *   <li>实时查询训练进度、最佳 F1、类别标签</li>
 *   <li>训练超参数配置（epoch/batchSize/nClasses 等）</li>
 *   <li>查看训练模型保存路径</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/train")
@RequiredArgsConstructor
@Tag(name = "AI 训练平台", description = "分类模型训练：数据上传、异步训练、状态查询、超参数配置")
public class TrainController {

    private final DataService dataService;
    private final TrainService trainService;

    /**
     * 上传训练数据 ZIP 文件并创建训练任务。
     *
     * @param file 训练数据 ZIP 文件（子目录为类别）
     * @return 训练任务信息
     */
    @Permission("admin")
    @Operation(summary = "上传训练数据", description = "上传 ZIP 格式训练数据并创建训练任务")
    @PostMapping("/upload")
    public ReturnResult<?> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            String fileName = file.getOriginalFilename();
            if (fileName == null || !dataService.isZipFile(fileName)) {
                return ReturnResult.error("仅支持 ZIP 格式文件");
            }
            // 保存文件
            String savedPath = dataService.saveUploadedFile(file.getBytes(), fileName);
            // 解压
            String dataPath = dataService.unzip(savedPath);
            // 创建任务
            TrainTask task = trainService.createTask(fileName, dataPath);

            return ReturnResult.ok(Map.of(
                    "taskId", task.getId(),
                    "dataPath", dataPath,
                    "fileName", fileName
            ));
        } catch (Exception e) {
            log.error("[Train] 上传失败", e);
            return ReturnResult.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 启动训练（异步）。
     *
     * @param body 请求体：{"taskId": "xxx"}
     * @return 启动结果
     */
    @Permission("admin")
    @Operation(summary = "启动训练", description = "启动异步训练，后台执行 DL4J ResNet50 迁移学习")
    @PostMapping("/trigger")
    public ReturnResult<?> trigger(@RequestBody Map<String, String> body) {
        String taskId = body.get("taskId");
        if (taskId == null || taskId.isBlank()) {
            return ReturnResult.error("taskId 不能为空");
        }
        try {
            trainService.startTraining(taskId);
            return ReturnResult.ok(Map.of("taskId", taskId, "status", "RUNNING"));
        } catch (Exception e) {
            log.error("[Train] 启动训练失败: taskId={}", taskId, e);
            return ReturnResult.error("启动训练失败: " + e.getMessage());
        }
    }

    /**
     * 查询训练进度。
     *
     * @param taskId 任务 ID
     * @return 任务详情（含实时进度、最佳 F1、类别标签等）
     */
    @Permission("admin")
    @Operation(summary = "查询训练进度", description = "实时查询训练任务的进度、当前 epoch、F1 等信息")
    @GetMapping("/status")
    public ReturnResult<?> status(@RequestParam("taskId") String taskId) {
        TrainTask task = trainService.getTask(taskId);
        if (task == null) {
            return ReturnResult.error("任务不存在: " + taskId);
        }

        // Map.of 不允许 null value，而 PENDING/RUNNING/SUCCESS 阶段多个字段为空，
        // 用 LinkedHashMap 做可空字段的安全组装
        TrainProgress progress = task.getProgress();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("status", task.getStatus().name());
        data.put("fileName", task.getOriginalFileName());

        Map<String, Object> progressMap = new LinkedHashMap<>();
        progressMap.put("currentEpoch", progress.getCurrentEpoch());
        progressMap.put("totalEpochs", progress.getTotalEpochs());
        progressMap.put("bestScore", progress.getBestScore());
        if (progress.getStage() != null) {
            progressMap.put("stage", progress.getStage());
        }
        progressMap.put("progress", progress.getProgress());
        data.put("progress", progressMap);

        if (progress.getModelPath() != null) {
            data.put("modelPath", progress.getModelPath());
        }
        if (progress.getClassLabels() != null) {
            data.put("classLabels", progress.getClassLabels());
        }
        data.put("createdAt", task.getCreatedAt().toString());
        if (task.getStartedAt() != null) {
            data.put("startedAt", task.getStartedAt().toString());
        }
        if (task.getFinishedAt() != null) {
            data.put("finishedAt", task.getFinishedAt().toString());
        }
        if (task.getErrorMessage() != null) {
            data.put("errorMessage", task.getErrorMessage());
        }
        return ReturnResult.ok(data);
    }

    /**
     * 列出所有训练任务。
     *
     * @return 任务列表
     */
    @Permission("admin")
    @Operation(summary = "任务列表", description = "列出所有训练任务及其当前状态")
    @GetMapping("/list")
    public ReturnResult<?> list() {
        List<TrainTask> tasks = trainService.listTasks();
        return ReturnResult.ok(tasks.stream().map(t -> Map.of(
                "taskId", t.getId(),
                "status", t.getStatus().name(),
                "fileName", t.getOriginalFileName() != null ? t.getOriginalFileName() : "",
                "bestScore", t.getProgress().getBestScore(),
                "createdAt", t.getCreatedAt().toString()
        )).toList());
    }

    /**
     * 更新训练超参数（仅 PENDING 状态可修改）。
     *
     * @param body 超参数 JSON
     * @return 更新结果
     */
    @Permission("admin")
    @Operation(summary = "配置超参数", description = "修改训练超参数（epoch/batchSize/nClasses 等），仅 PENDING 状态可修改")
    @PostMapping("/argument")
    public ReturnResult<?> updateArgument(@RequestBody Map<String, String> body) {
        String taskId = body.get("taskId");
        if (taskId == null || taskId.isBlank()) {
            return ReturnResult.error("taskId 不能为空");
        }
        try {
            TrainArgument argument = TrainArgument.builder()
                    .epoch(body.containsKey("epoch") ? Integer.parseInt(body.get("epoch")) : 10)
                    .batchSize(body.containsKey("batchSize") ? Integer.parseInt(body.get("batchSize")) : 2)
                    .nClasses(body.containsKey("nClasses") ? Integer.parseInt(body.get("nClasses")) : 3)
                    .learningRate(body.containsKey("learningRate") ? Double.parseDouble(body.get("learningRate")) : 1e-3)
                    .lrMomentum(body.containsKey("lrMomentum") ? Double.parseDouble(body.get("lrMomentum")) : 0.9)
                    .trainPercent(body.containsKey("trainPercent") ? Integer.parseInt(body.get("trainPercent")) : 70)
                    .classLabels(body.get("classLabels"))
                    .detLabels(body.get("detLabels"))
                    .build();
            trainService.updateArgument(taskId, argument);
            return ReturnResult.ok("超参数已更新");
        } catch (Exception e) {
            log.error("[Train] 更新超参数失败: taskId={}", taskId, e);
            return ReturnResult.error("更新超参数失败: " + e.getMessage());
        }
    }
}
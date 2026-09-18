package com.chua.starter.ai.training.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.training.support.model.request.TrainArgumentRequest;
import com.chua.starter.ai.training.support.model.request.TrainTriggerRequest;
import com.chua.starter.ai.training.support.model.view.TrainTaskView;
import com.chua.starter.ai.training.support.model.view.TrainUploadView;
import com.chua.starter.ai.training.support.domain.TrainTask;
import com.chua.starter.ai.training.support.service.DataService;
import com.chua.starter.ai.training.support.service.TrainService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
* AI 训练平台 REST 接口（面向对象风格）。
*
* <p>对应 AIAS 2_training_platform 的 TrainController，提供完整的训练闭环：</p>
* <ul>
*   <li>上传训练数据（ZIP）并自动解压</li>
*   <li>启动异步训练（后台 DL4J ResNet50 迁移学习），支持冷启动/续训练</li>
*   <li>实时查询训练进度、最佳 F1、类别标签</li>
*   <li>训练超参数配置（epoch/batchSize/nClasses 等）</li>
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

    private final DataService dataService; // 数据服务
    private final TrainService trainService; // train服务

    /**
    * 上传训练数据 压缩 文件并创建训练任务。
    *
    * @param file 训练数据 压缩 文件（子目录为类别）
    * @return 上传结果视图
    * @param @RequestParam("file" @请求参数("文件"
    * @param @RequestParam("file" @请求参数("文件"
    * @param @RequestParam("file" @RequestParam("file"
    */
    @Permission("admin")
    @Operation(summary = "上传训练数据", description = "上传 ZIP 格式训练数据并创建训练任务")
    @PostMapping("/upload")
    public ReturnResult<TrainUploadView> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            String fileName = file.getOriginalFilename();
            if (fileName == null || !dataService.isZipFile(fileName)) {
                return ReturnResult.error("仅支持 ZIP 格式文件");
            }
            String savedPath = dataService.saveUploadedFile(file.getBytes(), fileName);
            String dataPath = dataService.unzip(savedPath);
            TrainTask task = trainService.createTask(fileName, dataPath);
            return ReturnResult.ok(TrainUploadView.builder()
                    .taskId(task.getId())
                    .fileName(fileName)
                    .dataPath(dataPath)
                    .build());
        } catch (Exception e) {
            log.error("[Train] 上传失败", e);
            return ReturnResult.error("上传失败: " + e.getMessage());
        }
    }

    /**
    * 启动训练（异步）。
    *
    * <p>不传 {@code resumeModelPath} 时使用默认预训练模型（冷启动训练）；
    * 传入已训练模型路径时从该模型继续微调。{@code type} 缺省 resnet50，
    * 传 {@code yolo} 时走 Python-http 引擎（本地拉起 Ultralytics YOLO 子进程）。</p>
    *
    * @param request 请求体
    * @return 启动结果
    */
    @Permission("admin")
    @Operation(summary = "启动训练", description = "启动异步训练；type 缺省 resnet50(DL4J 图像分类)，可选 yolo(python 检测)；可传 resumeModelPath 从已有模型继续微调")
    @PostMapping("/trigger")
    public ReturnResult<Void> trigger(@RequestBody @Valid TrainTriggerRequest request) {
        try {
            // 续训练模式下，将续训配置合并到任务超参数（领域行为封装在请求对象内）
            if (request.isResumeMode()) {
                trainService.updateArgument(request.getTaskId(), request.toArgument());
            }
 // 类型 随启动请求传入（缺省 resnet50，由任务领域对象锁定）
            trainService.startTraining(request.getTaskId(), request.getType());
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("[Train] 启动训练失败: taskId={}", request.getTaskId(), e);
            return ReturnResult.error("启动训练失败: " + e.getMessage());
        }
    }

    /**
    * 查询训练进度（返回强类型视图对象）。
    *
    * @param taskId 任务 标识
    * @return 任务详情视图
    * @param @RequestParam("taskId" @请求参数("任务标识"
    * @param @RequestParam("taskId" @请求参数("任务id"
    * @param @RequestParam("taskId" @RequestParam("taskId"
    */
    @Permission("admin")
    @Operation(summary = "查询训练进度", description = "实时查询训练任务的进度、当前 epoch、F1 等信息")
    @GetMapping("/status")
    public ReturnResult<TrainTaskView> status(@RequestParam("taskId") String taskId) {
        TrainTask task = trainService.getTask(taskId);
        if (task == null) {
            return ReturnResult.error("任务不存在: " + taskId);
        }
        return ReturnResult.ok(TrainTaskView.from(task));
    }

    /**
    * 列出所有训练任务。
    *
    * @return 任务摘要列表
    */
    @Permission("admin")
    @Operation(summary = "任务列表", description = "列出所有训练任务及其当前状态")
    @GetMapping("/list")
    public ReturnResult<List<TrainTaskView>> list() {
        List<TrainTask> tasks = trainService.listTasks();
        return ReturnResult.ok(tasks.stream().map(TrainTaskView::from).toList());
    }

    /**
    * 更新训练超参数（部分更新语义，仅非空字段生效，PENDING 状态可修改）。
    *
    * @param request 超参数更新请求
    * @return 更新结果
    */
    @Permission("admin")
    @Operation(summary = "配置超参数", description = "修改训练超参数（仅非空字段生效），仅 PENDING 状态可修改")
    @PostMapping("/argument")
    public ReturnResult<Void> updateArgument(@RequestBody @Valid TrainArgumentRequest request) {
        try {
            trainService.updateArgument(request.getTaskId(), request.toArgument());
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("[Train] 更新超参数失败: taskId={}", request.getTaskId(), e);
            return ReturnResult.error("更新超参数失败: " + e.getMessage());
        }
    }
}

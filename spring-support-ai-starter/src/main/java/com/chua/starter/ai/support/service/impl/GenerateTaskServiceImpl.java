package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.image.ImageResponse;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.common.support.ai.video.VideoResponse;
import com.chua.deeplearning.support.capability.ModelCapabilities;
import com.chua.starter.ai.support.configuration.client.AiCapabilityResolver;
import com.chua.starter.ai.support.model.GenerateTask;
import com.chua.starter.ai.support.service.GenerateTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 生成任务服务实现。
 *
 * <p>采用内存任务表跟踪文生图/文生视频的异步生成任务。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateTaskServiceImpl implements GenerateTaskService {

    /**
     * 内存任务表：taskId → 任务
     */
    private final Map<String, GenerateTask> taskStore = new ConcurrentHashMap<>();

    /**
     * 统一能力解析器
     */
    private final AiCapabilityResolver capabilityResolver;

    /**
     * 创建文生图任务。
     *
     * @param prompt   提示词
     * @param model    模型
     * @param size     尺寸
     * @param style    风格
     * @param quality  质量
     * @return 生成任务
     */
    @Override
    public GenerateTask createImageTask(String prompt, String model, String size, String style, String quality) {
        AiCapabilityResolver.CapabilityConfig config = capabilityResolver.resolve(ModelCapabilities.TEXT_TO_IMAGE);
        String defaultModel = config.model().isBlank() ? "dall-e-3" : config.model();
        int width = capabilityResolver.getInt(ModelCapabilities.TEXT_TO_IMAGE, "width", 1024);
        int height = capabilityResolver.getInt(ModelCapabilities.TEXT_TO_IMAGE, "height", 1024);

        if (size != null && size.contains("x")) {
            String[] parts = size.split("x");
            width = Integer.parseInt(parts[0]);
            height = Integer.parseInt(parts[1]);
        }
        String effectiveModel = model != null ? model : defaultModel;

        GenerateTask task = new GenerateTask();
        task.setTaskId("img_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        task.setType(GenerateTask.TYPE_IMAGE);
        task.setPrompt(prompt);
        task.setModel(effectiveModel);
        task.setStatus(GenerateTask.STATUS_PROCESSING);
        task.setCreateTime(LocalDateTime.now());
        taskStore.put(task.getTaskId(), task);

        // 异步执行生成
        executeImageAsync(task, config, effectiveModel, width, height, style, quality);
        return task;
    }

    /**
     * 创建文生视频任务。
     *
     * @param prompt   提示词
     * @param model    模型
     * @param duration 时长
     * @param size     尺寸
     * @param style    风格
     * @return 生成任务
     */
    @Override
    public GenerateTask createVideoTask(String prompt, String model, Integer duration, String size, String style) {
        AiCapabilityResolver.CapabilityConfig config = capabilityResolver.resolve(ModelCapabilities.TEXT_TO_VIDEO);
        String defaultModel = config.model().isBlank() ? "sora" : config.model();
        int effectiveDuration = duration != null ? duration
                : capabilityResolver.getInt(ModelCapabilities.TEXT_TO_VIDEO, "duration", 5);
        String effectiveModel = model != null ? model : defaultModel;

        GenerateTask task = new GenerateTask();
        task.setTaskId("vid_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        task.setType(GenerateTask.TYPE_VIDEO);
        task.setPrompt(prompt);
        task.setModel(effectiveModel);
        task.setStatus(GenerateTask.STATUS_PROCESSING);
        task.setCreateTime(LocalDateTime.now());
        taskStore.put(task.getTaskId(), task);

        // 异步执行生成
        executeVideoAsync(task, config, effectiveModel, effectiveDuration, size, style);
        return task;
    }

    /**
     * 查询任务状态。
     *
     * @param taskId 任务 ID
     * @return 生成任务
     */
    @Override
    public GenerateTask getTask(String taskId) {
        return taskId != null ? taskStore.get(taskId) : null;
    }

    /**
     * 查询最近的任务列表。
     *
     * @param type  任务类型
     * @param limit 数量限制
     * @return 任务列表
     */
    @Override
    public List<GenerateTask> listRecent(String type, int limit) {
        List<GenerateTask> result = new ArrayList<>(taskStore.values());
        result.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));
        List<GenerateTask> filtered = new ArrayList<>();
        for (GenerateTask task : result) {
            if (type == null || type.equals(task.getType())) {
                filtered.add(task);
                if (filtered.size() >= limit) {
                    break;
                }
            }
        }
        return filtered;
    }

    /**
     * 异步执行文生图生成。
     *
     * @param task     任务
     * @param config   能力配置
     * @param model    模型
     * @param width    宽度
     * @param height   高度
     * @param style    风格
     * @param quality  质量
     */
    @Async("taskExecutor")
    public void executeImageAsync(GenerateTask task, AiCapabilityResolver.CapabilityConfig config,
                                  String model, int width, int height, String style, String quality) {
        try {
            ImageClient client = capabilityResolver.createImageClient(config);
            String taskId = client.model(model).size(width, height).style(style).quality(quality)
                    .createTask(task.getPrompt());
            // 轮询直到完成
            int maxRetry = 60;
            for (int retry = 0; retry < maxRetry; retry++) {
                ImageResponse resp = client.queryTask(taskId);
                if (resp.getStatus() == ImageResponse.Status.SUCCESS) {
                    task.setStatus(GenerateTask.STATUS_SUCCESS);
                    task.setResultUrl(resp.getImageUrl());
                    task.setCompleteTime(LocalDateTime.now());
                    return;
                } else if (resp.getStatus() == ImageResponse.Status.FAILED) {
                    task.setStatus(GenerateTask.STATUS_FAILED);
                    task.setErrorMessage(resp.getErrorMessage());
                    task.setCompleteTime(LocalDateTime.now());
                    return;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            task.setStatus(GenerateTask.STATUS_FAILED);
            task.setErrorMessage("图片生成超时");
            task.setCompleteTime(LocalDateTime.now());
        } catch (Exception e) {
            log.error("[GenerateTask] 文生图失败: {}", e.getMessage());
            task.setStatus(GenerateTask.STATUS_FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompleteTime(LocalDateTime.now());
        }
    }

    /**
     * 异步执行文生视频生成。
     *
     * @param task     任务
     * @param config   能力配置
     * @param model    模型
     * @param duration 时长
     * @param size     尺寸
     * @param style    风格
     */
    @Async("taskExecutor")
    public void executeVideoAsync(GenerateTask task, AiCapabilityResolver.CapabilityConfig config,
                                  String model, int duration, String size, String style) {
        try {
            VideoClient client = capabilityResolver.createVideoClient(config);
            String taskId;
            if (size != null && size.contains("x")) {
                String[] parts = size.split("x");
                taskId = client.model(model).size(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]))
                        .duration(duration).style(style).createTask(task.getPrompt());
            } else {
                taskId = client.model(model).duration(duration).style(style).createTask(task.getPrompt());
            }
            int maxRetry = 120;
            for (int retry = 0; retry < maxRetry; retry++) {
                VideoResponse resp = client.queryTask(taskId);
                if (resp.getStatus() == VideoResponse.Status.SUCCESS) {
                    task.setStatus(GenerateTask.STATUS_SUCCESS);
                    task.setResultUrl(resp.getVideoUrl());
                    task.setCompleteTime(LocalDateTime.now());
                    return;
                } else if (resp.getStatus() == VideoResponse.Status.FAILED) {
                    task.setStatus(GenerateTask.STATUS_FAILED);
                    task.setErrorMessage(resp.getErrorMessage());
                    task.setCompleteTime(LocalDateTime.now());
                    return;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            task.setStatus(GenerateTask.STATUS_FAILED);
            task.setErrorMessage("视频生成超时");
            task.setCompleteTime(LocalDateTime.now());
        } catch (Exception e) {
            log.error("[GenerateTask] 文生视频失败: {}", e.getMessage());
            task.setStatus(GenerateTask.STATUS_FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompleteTime(LocalDateTime.now());
        }
    }
}
package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.image.ImageClientSetting;
import com.chua.common.support.ai.image.ImageResponse;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.common.support.ai.video.VideoClientSetting;
import com.chua.common.support.ai.video.VideoResponse;
import com.chua.starter.ai.support.model.GenerateTask;
import com.chua.starter.ai.support.service.GenerateTaskService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
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
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateTaskServiceImpl implements GenerateTaskService {

    /**
     * 分组：图片
     */
    private static final String GROUP_IMAGE = "image";

    /**
     * 分组：视频
     */
    private static final String GROUP_VIDEO = "video";

    /**
     * 内存任务表：taskId → 任务
     */
    private final Map<String, GenerateTask> taskStore = new ConcurrentHashMap<>();

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService settingService;

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
        String apiKey = settingService.getValue(GROUP_IMAGE, "provider_api_key").orElse("");
        String baseUrl = settingService.getValue(GROUP_IMAGE, "provider_base_url").orElse("");
        String defaultModel = settingService.getValue(GROUP_IMAGE, "default_model").orElse("dall-e-3");
        String provider = settingService.getValue(GROUP_IMAGE, "provider").orElse("openai");
        int width = Integer.parseInt(settingService.getValue(GROUP_IMAGE, "width").orElse("1024"));
        int height = Integer.parseInt(settingService.getValue(GROUP_IMAGE, "height").orElse("1024"));

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
        executeImageAsync(task, apiKey, baseUrl, provider, effectiveModel, width, height, style, quality);
        return task;
    }

    /**
     * 创建文生视频任务。
     *
     * @param prompt   提示词
     * @param model    模型
     * @param duration 时长
     * @param style    风格
     * @return 生成任务
     */
    @Override
    public GenerateTask createVideoTask(String prompt, String model, Integer duration, String style) {
        String apiKey = settingService.getValue(GROUP_VIDEO, "provider_api_key").orElse("");
        String baseUrl = settingService.getValue(GROUP_VIDEO, "provider_base_url").orElse("");
        String defaultModel = settingService.getValue(GROUP_VIDEO, "default_model").orElse("sora");
        String provider = settingService.getValue(GROUP_VIDEO, "provider").orElse("openai");
        int effectiveDuration = duration != null ? duration
                : Integer.parseInt(settingService.getValue(GROUP_VIDEO, "duration").orElse("5"));
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
        executeVideoAsync(task, apiKey, baseUrl, provider, effectiveModel, effectiveDuration, style);
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
     * @param apiKey   API Key
     * @param baseUrl  基础地址
     * @param provider 提供方
     * @param model    模型
     * @param width    宽度
     * @param height   高度
     * @param style    风格
     * @param quality  质量
     */
    @Async("taskExecutor")
    public void executeImageAsync(GenerateTask task, String apiKey, String baseUrl, String provider,
                                  String model, int width, int height, String style, String quality) {
        try {
            ImageClient client = ImageClient.create(ImageClientSetting.builder()
                    .provider(provider)
                    .appKey(apiKey)
                    .baseUrl(baseUrl)
                    .build());
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
     * @param apiKey   API Key
     * @param baseUrl  基础地址
     * @param provider 提供方
     * @param model    模型
     * @param duration 时长
     * @param style    风格
     */
    @Async("taskExecutor")
    public void executeVideoAsync(GenerateTask task, String apiKey, String baseUrl, String provider,
                                  String model, int duration, String style) {
        try {
            VideoClient client = VideoClient.create(VideoClientSetting.builder()
                    .provider(provider)
                    .appKey(apiKey)
                    .baseUrl(baseUrl)
                    .build());
            String taskId = client.model(model).duration(duration).style(style).createTask(task.getPrompt());
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
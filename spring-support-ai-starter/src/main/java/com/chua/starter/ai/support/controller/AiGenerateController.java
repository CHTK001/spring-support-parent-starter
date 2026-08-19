package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.image.ImageResponse;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.common.support.ai.video.VideoResponse;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.deeplearning.support.capability.ModelCapabilities;
import com.chua.deeplearning.support.utils.ImageUtils;
import com.chua.starter.ai.support.configuration.client.AiCapabilityResolver;
import com.chua.starter.ai.support.model.GenerateResult;
import com.chua.starter.ai.support.model.GenerateTask;
import com.chua.starter.ai.support.model.ImageGenRequest;
import com.chua.starter.ai.support.model.VideoGenRequest;
import com.chua.starter.ai.support.service.GenerateTaskService;
import com.chua.starter.common.support.annotations.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * AI 生成控制器（文生图、文生视频、上色）
 *
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/generate")
@RequiredArgsConstructor
@Tag(name = "AI 生成", description = "文生图、文生视频、上色等 AI 生成接口")
public class AiGenerateController {

    /**
     * 统一能力解析器
     */
    private final AiCapabilityResolver capabilityResolver;

    /**
     * 生成任务服务
     */
    private final GenerateTaskService generateTaskService;

    @Permission("admin")
    @Operation(summary = "文生图", description = "根据提示词生成图片，返回图片URL")
    @PostMapping("/image")
    public ReturnResult<GenerateResult> generateImage(@RequestBody ImageGenRequest request) {
        // 通过能力标签统一解析配置
        AiCapabilityResolver.CapabilityConfig config = capabilityResolver.resolve(ModelCapabilities.TEXT_TO_IMAGE);
        if (config.apiKey().isBlank()) {
            return ReturnResult.error("图片生成未配置 API Key，请在 sys_ai_global_setting 中设置 image.provider_api_key");
        }
        String model = config.model().isBlank() ? "dall-e-3" : config.model();
        int width = capabilityResolver.getInt(ModelCapabilities.TEXT_TO_IMAGE, "width", 1024);
        int height = capabilityResolver.getInt(ModelCapabilities.TEXT_TO_IMAGE, "height", 1024);

        // 请求参数覆盖
        if (request.getModel() != null) {
            model = request.getModel();
        }
        if (request.getSize() != null && request.getSize().contains("x")) {
            String[] parts = request.getSize().split("x");
            width = Integer.parseInt(parts[0]);
            height = Integer.parseInt(parts[1]);
        }

        try {
            ImageClient client = capabilityResolver.createImageClient(config);

            // 使用异步任务，获取图片URL
            String taskId = client
                    .model(model)
                    .size(width, height)
                    .style(request.getStyle())
                    .quality(request.getQuality())
                    .createTask(request.getPrompt());

            // 轮询直到完成
            int maxRetry = 60;
            int retry = 0;
            String imageUrl = null;
            while (retry < maxRetry) {
                ImageResponse resp = client.queryTask(taskId);
                if (resp.getStatus() == ImageResponse.Status.SUCCESS) {
                    imageUrl = resp.getImageUrl();
                    break;
                } else if (resp.getStatus() == ImageResponse.Status.FAILED) {
                    return ReturnResult.error("图片生成失败: " + resp.getErrorMessage());
                }
                retry++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }

            if (imageUrl == null || imageUrl.isBlank()) {
                return ReturnResult.error("图片生成超时或未返回URL");
            }
            return ReturnResult.ok(new GenerateResult(imageUrl));
        } catch (Exception e) {
            log.error("[AiGenerate] 文生图失败: {}", e.getMessage());
            return ReturnResult.error("文生图失败: " + e.getMessage());
        }
    }

    @Permission("admin")
    @Operation(summary = "文生视频", description = "根据提示词生成视频（异步任务，返回视频URL）")
    @PostMapping("/video")
    public ReturnResult<GenerateResult> generateVideo(@RequestBody VideoGenRequest request) {
        // 通过能力标签统一解析配置
        AiCapabilityResolver.CapabilityConfig config = capabilityResolver.resolve(ModelCapabilities.TEXT_TO_VIDEO);
        if (config.apiKey().isBlank()) {
            return ReturnResult.error("视频生成未配置 API Key，请在 sys_ai_global_setting 中设置 video.provider_api_key");
        }
        String model = config.model().isBlank() ? "sora" : config.model();
        int duration = capabilityResolver.getInt(ModelCapabilities.TEXT_TO_VIDEO, "duration", 5);

        if (request.getModel() != null) {
            model = request.getModel();
        }
        if (request.getDuration() != null) {
            duration = request.getDuration();
        }

        try {
            VideoClient client = capabilityResolver.createVideoClient(config);

            String taskId = client
                    .model(model)
                    .duration(duration)
                    .style(request.getStyle())
                    .createTask(request.getPrompt());

            int maxRetry = 120;
            int retry = 0;
            String videoUrl = null;
            while (retry < maxRetry) {
                VideoResponse resp = client.queryTask(taskId);
                if (resp.getStatus() == VideoResponse.Status.SUCCESS) {
                    videoUrl = resp.getVideoUrl();
                    break;
                } else if (resp.getStatus() == VideoResponse.Status.FAILED) {
                    return ReturnResult.error("视频生成失败: " + resp.getErrorMessage());
                }
                retry++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }

            if (videoUrl == null || videoUrl.isBlank()) {
                return ReturnResult.error("视频生成超时或未返回URL");
            }
            return ReturnResult.ok(new GenerateResult(videoUrl));
        } catch (Exception e) {
            log.error("[AiGenerate] 文生视频失败: {}", e.getMessage());
            return ReturnResult.error("文生视频失败: " + e.getMessage());
        }
    }

    @Permission("admin")
    @Operation(summary = "上色", description = "对上传的黑白或灰度图片进行上色（基于图生图，返回URL或Base64）")
    @PostMapping(value = "/colorize", consumes = "multipart/form-data")
    public ReturnResult<GenerateResult> colorize(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }

        // 通过能力标签统一解析配置
        AiCapabilityResolver.CapabilityConfig config = capabilityResolver.resolve(AiCapabilityResolver.COLORIZE);
        if (config.apiKey().isBlank()) {
            return ReturnResult.error("上色未配置 API Key，请在 sys_ai_global_setting 中设置 colorize.provider_api_key");
        }
        String model = config.model().isBlank() ? "dall-e-3" : config.model();

        try {
            BufferedImage inputImage = ImageUtils.toBufferedImage(file.getBytes());
            if (inputImage == null) {
                return ReturnResult.error("无法解析图片文件");
            }

            ImageClient client = capabilityResolver.createImageClient(config);

            // 上色目前只能同步（图生图），返回BufferedImage，转Base64
            BufferedImage result = client
                    .model(model)
                    .referenceImage(inputImage)
                    .imageStrength(0.7)
                    .prompt("Colorize this black and white image, make it vibrant and natural, keep the original content")
                    .generate();

            String base64 = encodeImageToBase64(result);
            return ReturnResult.ok(new GenerateResult("data:image/png;base64," + base64));
        } catch (IOException e) {
            log.error("[AiGenerate] 上色失败: {}", e.getMessage());
            return ReturnResult.error("上色失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[AiGenerate] 上色异常: {}", e.getMessage());
            return ReturnResult.error("上色异常: " + e.getMessage());
        }
    }

    private String encodeImageToBase64(BufferedImage image) {
        return Base64.getEncoder().encodeToString(ImageUtils.encode(ImageUtils.toMat(image), "png"));
    }

    @Permission("admin")
    @Operation(summary = "创建文生图任务", description = "提交文生图任务，返回任务ID（异步，前端轮询查询状态）")
    @PostMapping("/task/image")
    public ReturnResult<GenerateTask> createImageTask(@RequestBody ImageGenRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return ReturnResult.error("提示词不能为空");
        }
        String apiKey = capabilityResolver.getStr(ModelCapabilities.TEXT_TO_IMAGE, "provider_api_key", "");
        if (apiKey.isBlank()) {
            return ReturnResult.error("图片生成未配置 API Key，请在 sys_ai_global_setting 中设置 image.provider_api_key");
        }
        return ReturnResult.ok(generateTaskService.createImageTask(
                request.getPrompt(), request.getModel(), request.getSize(), request.getStyle(), request.getQuality()));
    }

    @Permission("admin")
    @Operation(summary = "创建文生视频任务", description = "提交文生视频任务，返回任务ID（异步，前端轮询查询状态）")
    @PostMapping("/task/video")
    public ReturnResult<GenerateTask> createVideoTask(@RequestBody VideoGenRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return ReturnResult.error("提示词不能为空");
        }
        String apiKey = capabilityResolver.getStr(ModelCapabilities.TEXT_TO_VIDEO, "provider_api_key", "");
        if (apiKey.isBlank()) {
            return ReturnResult.error("视频生成未配置 API Key，请在 sys_ai_global_setting 中设置 video.provider_api_key");
        }
        return ReturnResult.ok(generateTaskService.createVideoTask(
                request.getPrompt(), request.getModel(), request.getDuration(), request.getSize(), request.getStyle()));
    }

    @Permission("admin")
    @Operation(summary = "查询生成任务状态", description = "根据任务ID查询生成进度，前端轮询使用")
    @GetMapping("/task/{taskId}")
    public ReturnResult<GenerateTask> queryTask(@PathVariable String taskId) {
        GenerateTask task = generateTaskService.getTask(taskId);
        if (task == null) {
            return ReturnResult.error("任务不存在或已过期: " + taskId);
        }
        return ReturnResult.ok(task);
    }

    @Permission("admin")
    @Operation(summary = "生成历史记录", description = "查询最近的文生图/文生视频任务历史")
    @GetMapping("/task/list")
    public ReturnResult<List<GenerateTask>> listTasks(@RequestParam(required = false) String type) {
        return ReturnResult.ok(generateTaskService.listRecent(type, 50));
    }
}


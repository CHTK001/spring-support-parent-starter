package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.image.ImageClientSetting;
import com.chua.common.support.ai.image.ImageResponse;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.common.support.ai.video.VideoClientSetting;
import com.chua.common.support.ai.video.VideoResponse;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.model.GenerateResult;
import com.chua.starter.ai.support.model.ImageGenRequest;
import com.chua.starter.ai.support.model.VideoGenRequest;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * AI 生成控制器（文生图、文生视频、上色）
 *
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/ai/generate")
@RequiredArgsConstructor
@Tag(name = "AI 生成", description = "文生图、文生视频、上色等 AI 生成接口")
public class AiGenerateController {

    private static final String GROUP_IMAGE = "image";
    private static final String GROUP_VIDEO = "video";
    private static final String GROUP_COLORIZE = "colorize";

    private final SysAiGlobalSettingService settingService;

    @Operation(summary = "文生图", description = "根据提示词生成图片，返回图片URL")
    @PostMapping("/image")
    public ReturnResult<GenerateResult> generateImage(@RequestBody ImageGenRequest request) {
        // 从数据库读取配置
        String provider = settingService.getValue(GROUP_IMAGE, "provider").orElse("openai");
        String apiKey = settingService.getValue(GROUP_IMAGE, "provider_api_key").orElse("");
        String baseUrl = settingService.getValue(GROUP_IMAGE, "provider_base_url").orElse("");
        String model = settingService.getValue(GROUP_IMAGE, "default_model").orElse("dall-e-3");
        int width = Integer.parseInt(settingService.getValue(GROUP_IMAGE, "width").orElse("1024"));
        int height = Integer.parseInt(settingService.getValue(GROUP_IMAGE, "height").orElse("1024"));

        if (apiKey.isBlank()) {
            return ReturnResult.error("图片生成未配置 API Key，请在 sys_ai_global_setting 中设置 image.provider_api_key");
        }

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
            ImageClient client = ImageClient.create(ImageClientSetting.builder()
                    .provider(provider)
                    .appKey(apiKey)
                    .baseUrl(baseUrl)
                    .build());

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

    @Operation(summary = "文生视频", description = "根据提示词生成视频（异步任务，返回视频URL）")
    @PostMapping("/video")
    public ReturnResult<GenerateResult> generateVideo(@RequestBody VideoGenRequest request) {
        // 从数据库读取配置
        String provider = settingService.getValue(GROUP_VIDEO, "provider").orElse("openai");
        String apiKey = settingService.getValue(GROUP_VIDEO, "provider_api_key").orElse("");
        String baseUrl = settingService.getValue(GROUP_VIDEO, "provider_base_url").orElse("");
        String model = settingService.getValue(GROUP_VIDEO, "default_model").orElse("sora");
        int duration = Integer.parseInt(settingService.getValue(GROUP_VIDEO, "duration").orElse("5"));

        if (apiKey.isBlank()) {
            return ReturnResult.error("视频生成未配置 API Key，请在 sys_ai_global_setting 中设置 video.provider_api_key");
        }

        if (request.getModel() != null) {
            model = request.getModel();
        }
        if (request.getDuration() != null) {
            duration = request.getDuration();
        }

        try {
            VideoClient client = VideoClient.create(VideoClientSetting.builder()
                    .provider(provider)
                    .appKey(apiKey)
                    .baseUrl(baseUrl)
                    .build());

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

    @Operation(summary = "上色", description = "对上传的黑白或灰度图片进行上色（基于图生图，返回URL或Base64）")
    @PostMapping(value = "/colorize", consumes = "multipart/form-data")
    public ReturnResult<GenerateResult> colorize(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }

        // 从数据库读取配置
        String provider = settingService.getValue(GROUP_COLORIZE, "provider").orElse("openai");
        String apiKey = settingService.getValue(GROUP_COLORIZE, "provider_api_key").orElse("");
        String baseUrl = settingService.getValue(GROUP_COLORIZE, "provider_base_url").orElse("");
        String model = settingService.getValue(GROUP_COLORIZE, "default_model").orElse("dall-e-3");

        if (apiKey.isBlank()) {
            return ReturnResult.error("上色未配置 API Key，请在 sys_ai_global_setting 中设置 colorize.provider_api_key");
        }

        try {
            BufferedImage inputImage = ImageIO.read(file.getInputStream());
            if (inputImage == null) {
                return ReturnResult.error("无法解析图片文件");
            }

            ImageClient client = ImageClient.create(ImageClientSetting.builder()
                    .provider(provider)
                    .appKey(apiKey)
                    .baseUrl(baseUrl)
                    .build());

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

    private String encodeImageToBase64(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }
}


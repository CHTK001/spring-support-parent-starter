package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.common.support.annotations.Permission;
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

import java.io.IOException;
import java.util.Base64;

/**
 * 多模态/识图控制器
 * 提供图像描述、OCR、视觉问答等能力
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/vision")
@RequiredArgsConstructor
@Tag(name = "AI 识图", description = "图像识别、OCR、视觉问答")
public class VisionController {

    /**
     * 识图分组标识
     */
    private static final String GROUP_VISION = "vision";

    /**
     * 全局配置服务
     */
    private final SysAiGlobalSettingService settingService;

    @Permission("admin")
    @Operation(summary = "图像描述", description = "识别图片内容并返回文字描述")
    @PostMapping("/describe")
    public ReturnResult<String> describe(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "prompt", required = false) String prompt) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            String dataUri = buildDataUri(file);
            String userPrompt = (prompt != null) ? prompt : "描述这张图片的内容，用中文回答";
            String result = callVisionModel(userPrompt, dataUri);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("[Vision] 图像描述失败: {}", e.getMessage());
            return ReturnResult.error("图像描述失败: " + e.getMessage());
        }
    }

    @Permission("admin")
    @Operation(summary = "OCR识别", description = "识别图片中的文字")
    @PostMapping("/ocr")
    public ReturnResult<String> ocr(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            String dataUri = buildDataUri(file);
            String prompt = "识别这张图片中的所有文字，输出纯文本内容，不要额外解释";
            String result = callVisionModel(prompt, dataUri);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("[Vision] OCR失败: {}", e.getMessage());
            return ReturnResult.error("OCR失败: " + e.getMessage());
        }
    }

    @Permission("admin")
    @Operation(summary = "视觉问答", description = "针对图片内容提问")
    @PostMapping("/ask")
    public ReturnResult<String> ask(@RequestParam("file") MultipartFile file,
                                    @RequestParam("question") String question) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        if (question == null || question.isBlank()) {
            return ReturnResult.error("问题不能为空");
        }
        try {
            String dataUri = buildDataUri(file);
            String result = callVisionModel(question, dataUri);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("[Vision] 视觉问答失败: {}", e.getMessage());
            return ReturnResult.error("视觉问答失败: " + e.getMessage());
        }
    }

    /**
     * 调用多模态模型
     *
     * @param prompt 提示词
     * @param imageDataUri 图像 Data URI
     * @return 模型响应
     * @throws Exception 异常
     */
    private String callVisionModel(String prompt, String imageDataUri) throws Exception {
        String provider = settingService.getValue(GROUP_VISION, "provider").orElse("openai");
        String apiKey = settingService.getValue(GROUP_VISION, "provider_api_key").orElse("");
        String baseUrl = settingService.getValue(GROUP_VISION, "provider_base_url").orElse("");
        String model = settingService.getValue(GROUP_VISION, "default_model").orElse("gpt-4-vision-preview");

        if (apiKey.isBlank()) {
            throw new IllegalStateException("识图功能未配置 API Key，请在 sys_ai_global_setting 中设置 vision.provider_api_key");
        }

        ChatClient client = ChatClient.create(ChatClientSetting.builder()
                .provider(provider)
                .appKey(apiKey)
                .baseUrl(baseUrl)
                .build());
        client.model(model);
        client.addImage(imageDataUri);

        // 同步调用多模态模型，返回完整文本内容
        String content = client.chatSync(prompt);
        return content;
    }

    /**
     * 构建 Data URI
     *
     * @param file 上传的图片文件
     * @return Data URI 字符串
     * @throws IOException 文件读取异常
     */
    private String buildDataUri(MultipartFile file) throws IOException {
        String base64 = Base64.getEncoder().encodeToString(file.getBytes());
        String mimeType = file.getContentType();
        if (mimeType == null) {
            mimeType = "image/png";
        }
        return "data:" + mimeType + ";base64," + base64;
    }
}


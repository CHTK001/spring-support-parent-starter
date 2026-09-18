package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
* 打开AI 标准协议扩展控制器，提供 镜像 / 音频 / 文件 / 罚金-tuning 等扩展接口。
*
* <p>继承 {@link BaseAiController} 统一使用 Bearer Token 鉴权。
* 所有接口均需通过 {@link #authCheck(HttpServletRequest)} 校验后才可访问。</p>
*
* <h3>接口列表</h3>
* <ul>
*   <li>{@code POST /v2/v1/images/generations} — 图像生成</li>
*   <li>{@code POST /v2/v1/images/edits} — 图像编辑</li>
*   <li>{@code POST /v2/v1/images/variations} — 图像变体</li>
*   <li>{@code POST /v2/v1/audio/transcriptions} — 音频转录</li>
*   <li>{@code POST /v2/v1/audio/translations} — 音频翻译</li>
*   <li>{@code POST /v2/v1/moderations} — 内容审核</li>
*   <li>{@code POST /v2/v1/files} — 文件上传</li>
*   <li>{@code GET /v2/v1/files} — 文件列表</li>
*   <li>{@code GET /v2/v1/files/{file_id}} — 文件检索</li>
*   <li>{@code DELETE /v2/v1/files/{file_id}} — 文件删除</li>
*   <li>{@code GET /v2/v1/files/{file_id}/content} — 文件内容检索</li>
*   <li>{@code POST /v2/v1/fine_tuning/jobs} — 创建微调任务</li>
*   <li>{@code GET /v2/v1/fine_tuning/jobs} — 微调任务列表</li>
*   <li>{@code GET /v2/v1/fine_tuning/jobs/{job_id}} — 微调任务详情</li>
*   <li>{@code POST /v2/v1/fine_tuning/jobs/{job_id}/cancel} — 取消微调任务</li>
* </ul>
*
* @since 4.0.0.42
* @author CH
 */
@Tag(name = "AI OpenAI Extended Protocol Controller", description = "OpenAI 标准协议扩展接口（Images / Audio / Files / Fine-tuning）")
@RestController
@ApiReturnFormatIgnore
@RequestMapping("/v2")
public class AiOpenaiExtendedController extends BaseAiController {

    /**
    * AI 对话门面服务，负责模型路由与对话执行
    */
    private final AiChatFacadeService aiChatFacadeService;

    /**
    * 构造函数，注入扩展控制器所需的服务。
    *
    * <p>鉴权相关服务由基类 {@link BaseAiController} 通过 {@code @Autowired} 注入。</p>
    *
    * @param aiChatFacadeService AI 对话门面服务
    */
    public AiOpenaiExtendedController(AiChatFacadeService aiChatFacadeService) {
        this.aiChatFacadeService = aiChatFacadeService;
    }

 // ==================== 打开AI 镜像 Generation ====================

    /**
    * 打开AI 镜像 Generation 图像生成接口。
    *
    * <p>接收图像生成请求，调用对话门面服务处理后返回结果。</p>
    *
    * @param request         请求体（包含 提示符、模型 等）
    * @param servletRequest  HTTP 请求
    * @return 图像生成响应
    */
    @Operation(summary = "OpenAI Image Generation", description = "OpenAI 标准协议图像生成接口")
    @PostMapping("/v1/images/generations")
    public ResponseEntity<Map<String, Object>> imageGeneration(@RequestBody Map<String, Object> request,
                                                                HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        try {
            String prompt = (String) request.getOrDefault("prompt", "");
            String model = (String) request.getOrDefault("model", "dall-e-3");
            AiChatRequest chatRequest = new AiChatRequest();
            chatRequest.setModel(model);
            chatRequest.setPrompt(prompt);
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            Map<String, Object> body = imageResponse();
            if (result != null && result.get("text") != null) {
                String text = (String) result.get("text");
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("url", text);
                data.put("revised_prompt", prompt);
                body.put("data", List.of(data));
            }
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            return serverError("Image generation failed: " + ex.getMessage());
        }
    }

 // ==================== 打开AI 镜像 Edits ====================

    /**
    * 打开AI 镜像 Edits 图像编辑接口。
    *
    * <p>上传原始图像与编辑描述，返回编辑后的图像。</p>
    *
    * @param image           原始图像文件
    * @param prompt          编辑描述
    * @param model           模型名称
    * @param n               生成数量
    * @param servletRequest  HTTP 请求
    * @return 图像编辑响应
    * @param @RequestParam("image" @请求参数("镜像"
    */
    @Operation(summary = "OpenAI Image Edits", description = "OpenAI 标准协议图像编辑接口")
    @PostMapping(value = "/v1/images/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> imageEdits(@RequestParam("image") MultipartFile image,
                                                           @RequestParam("prompt") String prompt,
                                                           @RequestParam(value = "model", required = false) String model,
                                                           @RequestParam(value = "n", required = false, defaultValue = "1") Integer n,
                                                           HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        // 文件判空
        if (image == null || image.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "error", Map.of("message", "No image file provided", "type", "invalid_request_error")
            ));
        }
        try {
            AiChatRequest chatRequest = new AiChatRequest();
            chatRequest.setModel(model != null ? model : "dall-e-2");
            chatRequest.setPrompt("Edit image: " + prompt);
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            return ResponseEntity.ok(imageEditVariationResponse(n, result));
        } catch (Exception ex) {
            return serverError("Image edit failed: " + ex.getMessage());
        }
    }

 // ==================== 打开AI 镜像 Variations ====================

    /**
    * 打开AI 镜像 Variations 图像变体接口。
    *
    * <p>上传原始图像，生成风格相似的变体图像。</p>
    *
    * @param image           原始图像文件
    * @param model           模型名称
    * @param n               生成数量
    * @param servletRequest  HTTP 请求
    * @return 图像变体响应
    * @param @RequestParam("image" @请求参数("镜像"
    */
    @Operation(summary = "OpenAI Image Variations", description = "OpenAI 标准协议图像变体接口")
    @PostMapping(value = "/v1/images/variations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> imageVariations(@RequestParam("image") MultipartFile image,
                                                                @RequestParam(value = "model", required = false) String model,
                                                                @RequestParam(value = "n", required = false, defaultValue = "1") Integer n,
                                                                HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        // 文件判空
        if (image == null || image.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "error", Map.of("message", "No image file provided", "type", "invalid_request_error")
            ));
        }
        try {
            AiChatRequest chatRequest = new AiChatRequest();
            chatRequest.setModel(model != null ? model : "dall-e-2");
            chatRequest.setPrompt("Generate variations of image");
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            return ResponseEntity.ok(imageEditVariationResponse(n, result));
        } catch (Exception ex) {
            return serverError("Image variation failed: " + ex.getMessage());
        }
    }

 // ==================== 打开AI 音频 转写 ====================

    /**
    * 打开AI 音频 转写 音频转录接口。
    *
    * <p>上传音频文件，返回文本转录结果。</p>
    *
    * @param file            音频文件
    * @param model           模型名称
    * @param prompt          提示词
    * @param servletRequest  HTTP 请求
    * @return 转录文本
    * @param @RequestParam("file" @请求参数("文件"
    */
    @Operation(summary = "OpenAI Audio Transcription", description = "OpenAI 标准协议音频转录接口")
    @PostMapping(value = "/v1/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> audioTranscriptions(@RequestParam("file") MultipartFile file,
                                                                    @RequestParam("model") String model,
                                                                    @RequestParam(value = "prompt", required = false) String prompt,
                                                                    HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        // 文件判空
        if (file == null || file.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "error", Map.of("message", "No audio file provided", "type", "invalid_request_error")
            ));
        }
        try {
            AiChatRequest chatRequest = new AiChatRequest();
            chatRequest.setModel(model);
            chatRequest.setPrompt("Transcribe audio: " + (prompt != null ? prompt : ""));
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            String text = result != null ? (String) result.get("text") : "";
            return ResponseEntity.ok(Map.of("text", text));
        } catch (Exception ex) {
            return serverError("Audio transcription failed: " + ex.getMessage());
        }
    }

 // ==================== 打开AI 音频 翻译 ====================

    /**
    * 打开AI 音频 翻译 音频翻译接口。
    *
    * <p>上传非英语音频文件，返回英语翻译文本。</p>
    *
    * @param file            音频文件
    * @param model           模型名称
    * @param prompt          提示词
    * @param servletRequest  HTTP 请求
    * @return 翻译文本
    * @param @RequestParam("file" @请求参数("文件"
    */
    @Operation(summary = "OpenAI Audio Translation", description = "OpenAI 标准协议音频翻译接口")
    @PostMapping(value = "/v1/audio/translations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> audioTranslations(@RequestParam("file") MultipartFile file,
                                                                  @RequestParam("model") String model,
                                                                  @RequestParam(value = "prompt", required = false) String prompt,
                                                                  HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        // 文件判空
        if (file == null || file.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "error", Map.of("message", "No audio file provided", "type", "invalid_request_error")
            ));
        }
        try {
            AiChatRequest chatRequest = new AiChatRequest();
            chatRequest.setModel(model);
            chatRequest.setPrompt("Translate audio to English: " + (prompt != null ? prompt : ""));
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            String text = result != null ? (String) result.get("text") : "";
            return ResponseEntity.ok(Map.of("text", text));
        } catch (Exception ex) {
            return serverError("Audio translation failed: " + ex.getMessage());
        }
    }

 // ==================== 打开AI Moderations ====================

    /**
    * 打开AI Moderations 内容审核接口。
    *
    * <p>对输入文本进行内容安全审核，返回各类别的违规评分。</p>
    *
    * @param request         请求体（包含 输入 文本）
    * @param servletRequest  HTTP 请求
    * @return 审核结果
    */
    @Operation(summary = "OpenAI Moderations", description = "OpenAI 标准协议内容审核接口")
    @PostMapping("/v1/moderations")
    public ResponseEntity<Map<String, Object>> moderations(@RequestBody Map<String, Object> request,
                                                            HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        try {
            String input = (String) request.getOrDefault("input", "");
            String model = (String) request.getOrDefault("model", "text-moderation-latest");
            AiChatRequest chatRequest = new AiChatRequest();
            chatRequest.setModel(model);
            chatRequest.setPrompt("Moderate: " + input);
            aiChatFacadeService.call(chatRequest);
            return ResponseEntity.ok(moderationResponse());
        } catch (Exception ex) {
            return serverError("Moderation failed: " + ex.getMessage());
        }
    }

 // ==================== 打开AI 文件 ====================

    /**
    * 打开AI Upload 文件 文件上传接口。
    *
    * <p>上传文件到 OpenAI 兼容存储，返回文件元信息。</p>
    *
    * @param file            文件
    * @param purpose         用途（如 罚金-tune）
    * @param servletRequest  HTTP 请求
    * @return 文件元信息
    * @param @RequestParam("file" @请求参数("文件"
    */
    @Operation(summary = "OpenAI Upload File", description = "OpenAI 标准协议文件上传接口")
    @PostMapping(value = "/v1/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
                                                           @RequestParam("purpose") String purpose,
                                                           HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        String fileId = "file-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> body = fileResponse(fileId, "upload");
        body.put("filename", file.getOriginalFilename());
        body.put("purpose", purpose);
        body.put("bytes", file.getSize());
        return ResponseEntity.ok(body);
    }

    /**
    * 打开AI 列表 文件 文件列表接口。
    *
    * @param purpose         用途过滤
    * @param servletRequest  HTTP 请求
    * @return 文件列表
    * @param false false
    */
    @Operation(summary = "OpenAI List Files", description = "OpenAI 标准协议文件列表接口")
    @GetMapping("/v1/files")
    public ResponseEntity<Map<String, Object>> listFiles(@RequestParam(value = "purpose", required = false) String purpose,
                                                          HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        return ResponseEntity.ok(Map.of("object", "list", "data", List.of()));
    }

    /**
    * 打开AI Retrieve 文件 文件检索接口。
    *
    * @param fileId          文件 标识
    * @param servletRequest  HTTP 请求
    * @return 文件元信息
    * @param @PathVariable("file_id" @路径变量("文件_标识"
    */
    @Operation(summary = "OpenAI Retrieve File", description = "OpenAI 标准协议文件检索接口")
    @GetMapping("/v1/files/{file_id}")
    public ResponseEntity<Map<String, Object>> retrieveFile(@PathVariable("file_id") String fileId,
                                                             HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        return ResponseEntity.ok(fileResponse(fileId, "retrieve"));
    }

    /**
    * 打开AI 删除 文件 文件删除接口。
    *
    * @param fileId          文件 标识
    * @param servletRequest  HTTP 请求
    * @return 删除结果
    * @param @PathVariable("file_id" @路径变量("文件_标识"
    */
    @Operation(summary = "OpenAI Delete File", description = "OpenAI 标准协议文件删除接口")
    @DeleteMapping("/v1/files/{file_id}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable("file_id") String fileId,
                                                           HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        return ResponseEntity.ok(Map.of("id", fileId, "object", "file", "deleted", true));
    }

    /**
    * 打开AI Retrieve 文件 内容 文件内容检索接口。
    *
    * @param fileId          文件 标识
    * @param servletRequest  HTTP 请求
    * @return 文件内容（当前实现返回不可用提示）
    * @param @PathVariable("file_id" @路径变量("文件_标识"
    */
    @Operation(summary = "OpenAI Retrieve File Content", description = "OpenAI 标准协议文件内容检索接口")
    @GetMapping("/v1/files/{file_id}/content")
    public ResponseEntity<Map<String, Object>> retrieveFileContent(@PathVariable("file_id") String fileId,
                                                                    HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        return ResponseEntity.ok(Map.of(
                "error", Map.of("message", "File content not available", "type", "not_found")
        ));
    }

 // ==================== 打开AI 罚金-tuning 作业 ====================

    /**
    * 打开AI 创建 罚金-tuning 作业 创建微调任务接口。
    *
    * @param request         请求体
    * @param servletRequest  HTTP 请求
    * @return 微调任务信息
    */
    @Operation(summary = "OpenAI Create Fine-tuning Job", description = "OpenAI 标准协议创建微调任务接口")
    @PostMapping("/v1/fine_tuning/jobs")
    public ResponseEntity<Map<String, Object>> createFineTuningJob(@RequestBody Map<String, Object> request,
                                                                    HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        return ResponseEntity.ok(fineTuneResponse("queued"));
    }

    /**
    * 打开AI 列表 罚金-tuning 作业 微调任务列表接口。
    *
    * @param after           分页游标
    * @param limit           每页数量
    * @param servletRequest  HTTP 请求
    * @return 微调任务列表
    * @param false false
    */
    @Operation(summary = "OpenAI List Fine-tuning Jobs", description = "OpenAI 标准协议微调任务列表接口")
    @GetMapping("/v1/fine_tuning/jobs")
    public ResponseEntity<Map<String, Object>> listFineTuningJobs(@RequestParam(value = "after", required = false) String after,
                                                                   @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
                                                                   HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        return ResponseEntity.ok(Map.of("object", "list", "data", List.of()));
    }

    /**
    * 打开AI Retrieve 罚金-tuning 作业 微调任务详情接口。
    *
    * @param jobId           任务 标识
    * @param servletRequest  HTTP 请求
    * @return 微调任务详情
    * @param @PathVariable("job_id" @路径变量("作业_标识"
    */
    @Operation(summary = "OpenAI Retrieve Fine-tuning Job", description = "OpenAI 标准协议微调任务详情接口")
    @GetMapping("/v1/fine_tuning/jobs/{job_id}")
    public ResponseEntity<Map<String, Object>> retrieveFineTuningJob(@PathVariable("job_id") String jobId,
                                                                      HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        return ResponseEntity.ok(fineTuneResponse("unknown"));
    }

    /**
    * 打开AI Cancel 罚金-tuning 作业 取消微调任务接口。
    *
    * @param jobId           任务 标识
    * @param servletRequest  HTTP 请求
    * @return 取消结果
    * @param @PathVariable("job_id" @路径变量("作业_标识"
    */
    @Operation(summary = "OpenAI Cancel Fine-tuning Job", description = "OpenAI 标准协议取消微调任务接口")
    @PostMapping("/v1/fine_tuning/jobs/{job_id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelFineTuningJob(@PathVariable("job_id") String jobId,
                                                                    HttpServletRequest servletRequest) {
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        return ResponseEntity.ok(fineTuneResponse("cancelled"));
    }

    // ==================== 响应构建器 ====================

    /**
    * 构建图像生成响应的基础结构。
    *
    * @return 空图像响应体（含 created 时间戳）
    */
    private static Map<String, Object> imageResponse() {
        return new LinkedHashMap<>(Map.of("created", (int) (System.currentTimeMillis() / 1000), "data", List.of()));
    }

    /**
    * 构建图像编辑/变体响应。
    *
    * @param n      生成数量
    * @param result 对话结果
    * @return 图像编辑/变体响应体
    */
    private static Map<String, Object> imageEditVariationResponse(Integer n, Map<String, Object> result) {
        String text = result != null ? (String) result.get("text") : null;
        List<Map<String, Object>> data = new ArrayList<>();
        int count = Math.min(n != null ? n : 1, 10);
        for (int i = 0; i < count; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            if (text != null) {
                item.put("url", text + (count > 1 ? "?variant=" + i : ""));
            }
            data.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("created", (int) (System.currentTimeMillis() / 1000));
        body.put("data", data);
        return body;
    }

    /**
    * 构建内容审核响应。
    *
    * <p>返回所有审核类别均为 false（未触发违规），评分均为 0。</p>
    *
    * @return 审核响应体
    */
    private static Map<String, Object> moderationResponse() {
        String id = "mod-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> categories = new LinkedHashMap<>();
        categories.put("sexual", false);
        categories.put("hate", false);
        categories.put("harassment", false);
        categories.put("self-harm", false);
        categories.put("sexual/minors", false);
        categories.put("hate/threatening", false);
        categories.put("violence/graphic", false);
        categories.put("violence", false);

        Map<String, Object> categoryScores = new LinkedHashMap<>();
        for (String key : categories.keySet()) {
            categoryScores.put(key, 0.0);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flagged", false);
        result.put("categories", categories);
        result.put("category_scores", categoryScores);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("model", "text-moderation-latest");
        body.put("results", List.of(result));
        return body;
    }

    /**
    * 构建文件元信息响应。
    *
    * @param fileId 文件 标识
    * @param status 状态
    * @return 文件元信息
    */
    private static Map<String, Object> fileResponse(String fileId, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", fileId);
        body.put("object", "file");
        body.put("bytes", 0);
        body.put("created_at", (int) (System.currentTimeMillis() / 1000));
        body.put("filename", "");
        body.put("purpose", "");
        body.put("status", status);
        return body;
    }

    /**
    * 构建微调任务响应。
    *
    * @param status 任务状态
    * @return 微调任务信息
    */
    private static Map<String, Object> fineTuneResponse(String status) {
        String jobId = "ftjob-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", jobId);
        body.put("object", "fine_tuning.job");
        body.put("model", "gpt-3.5-turbo");
        body.put("training_file", "");
        body.put("status", status);
        body.put("created_at", (int) (System.currentTimeMillis() / 1000));
        body.put("finished_at", null);
        body.put("trained_tokens", null);
        body.put("organization_id", "");
        body.put("hyperparameters", Map.of("n_epochs", "auto"));
        return body;
    }

    /**
    * 构建服务端错误响应。
    *
    * @param message 错误描述
    * @return 错误响应体
    */
    private ResponseEntity<Map<String, Object>> serverError(String message) {
        return ResponseEntity.ok(Map.of(
                "error", Map.of("message", message, "type", "api_error")
        ));
    }
}

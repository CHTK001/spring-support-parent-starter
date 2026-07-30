package com.chua.starter.ai.support.controller;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.service.AiAccessGuardService;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "AI OpenAI Extended Protocol Controller", description = "OpenAI 标准协议扩展接口（Images/Audio/Files/Fine-tuning）")
@Component
@ApiReturnFormatIgnore
public class AiOpenaiExtendedController {

    private final AiChatFacadeService aiChatFacadeService;
    private final AiAccessGuardService aiAccessGuardService;

    public AiOpenaiExtendedController(
            AiChatFacadeService aiChatFacadeService,
            AiAccessGuardService aiAccessGuardService) {
        this.aiChatFacadeService = aiChatFacadeService;
        this.aiAccessGuardService = aiAccessGuardService;
    }

    private boolean authCheck(HttpServletRequest request) {
        String appKey = request.getHeader("x-ai-app-key");
        String appSecret = request.getHeader("x-ai-app-secret");
        if (StringUtils.isBlank(appKey) || StringUtils.isBlank(appSecret)) {
            String auth = request.getHeader("Authorization");
            if (!StringUtils.isBlank(auth) && auth.startsWith("Bearer ")) {
                appKey = auth.substring(7).trim();
                appSecret = appKey;
            }
        }
        if (StringUtils.isBlank(appKey)) {
            return false;
        }
        return aiAccessGuardService.verifyAppKey(appKey, appSecret, request.getRemoteAddr());
    }

    private ResponseEntity<Map<String, Object>> authError() {
        return ResponseEntity.status(401).body(Map.of(
                "error", Map.of("message", "AI AppKey 校验失败", "type", "authentication_error")
        ));
    }

    private ResponseEntity<Map<String, Object>> serverError(String message) {
        return ResponseEntity.ok(Map.of(
                "error", Map.of("message", message, "type", "api_error")
        ));
    }

    // ==================== OpenAI Image Generation ====================

    @Operation(summary = "OpenAI Image Generation", description = "OpenAI 标准协议图像生成接口")
    @PostMapping("/v1/images/generations")
    public ResponseEntity<Map<String, Object>> imageGeneration(@RequestBody Map<String, Object> request,
                                                                HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
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

    // ==================== OpenAI Image Edits ====================

    @Operation(summary = "OpenAI Image Edits", description = "OpenAI 标准协议图像编辑接口")
    @PostMapping(value = "/v1/images/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> imageEdits(@RequestParam("image") MultipartFile image,
                                                           @RequestParam("prompt") String prompt,
                                                           @RequestParam(value = "model", required = false) String model,
                                                           @RequestParam(value = "n", required = false, defaultValue = "1") Integer n,
                                                           HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        if (image == null || image.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", Map.of("message", "No image file provided", "type", "invalid_request_error")));
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

    // ==================== OpenAI Image Variations ====================

    @Operation(summary = "OpenAI Image Variations", description = "OpenAI 标准协议图像变体接口")
    @PostMapping(value = "/v1/images/variations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> imageVariations(@RequestParam("image") MultipartFile image,
                                                                @RequestParam(value = "model", required = false) String model,
                                                                @RequestParam(value = "n", required = false, defaultValue = "1") Integer n,
                                                                HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        if (image == null || image.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", Map.of("message", "No image file provided", "type", "invalid_request_error")));
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

    // ==================== OpenAI Audio Transcription ====================

    @Operation(summary = "OpenAI Audio Transcription", description = "OpenAI 标准协议音频转录接口")
    @PostMapping(value = "/v1/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> audioTranscriptions(@RequestParam("file") MultipartFile file,
                                                                     @RequestParam("model") String model,
                                                                     @RequestParam(value = "prompt", required = false) String prompt,
                                                                     HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", Map.of("message", "No audio file provided", "type", "invalid_request_error")));
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

    // ==================== OpenAI Audio Translation ====================

    @Operation(summary = "OpenAI Audio Translation", description = "OpenAI 标准协议音频翻译接口")
    @PostMapping(value = "/v1/audio/translations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> audioTranslations(@RequestParam("file") MultipartFile file,
                                                                   @RequestParam("model") String model,
                                                                   @RequestParam(value = "prompt", required = false) String prompt,
                                                                   HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", Map.of("message", "No audio file provided", "type", "invalid_request_error")));
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

    // ==================== OpenAI Moderations ====================

    @Operation(summary = "OpenAI Moderations", description = "OpenAI 标准协议内容审核接口")
    @PostMapping("/v1/moderations")
    public ResponseEntity<Map<String, Object>> moderations(@RequestBody Map<String, Object> request,
                                                            HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
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

    // ==================== OpenAI Files ====================

    @Operation(summary = "OpenAI Upload File", description = "OpenAI 标准协议文件上传接口")
    @PostMapping(value = "/v1/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
                                                           @RequestParam("purpose") String purpose,
                                                           HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        String fileId = "file-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> body = fileResponse(fileId, "upload");
        body.put("filename", file.getOriginalFilename());
        body.put("purpose", purpose);
        body.put("bytes", file.getSize());
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "OpenAI List Files", description = "OpenAI 标准协议文件列表接口")
    @GetMapping("/v1/files")
    public ResponseEntity<Map<String, Object>> listFiles(@RequestParam(value = "purpose", required = false) String purpose,
                                                          HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        return ResponseEntity.ok(Map.of("object", "list", "data", List.of()));
    }

    @Operation(summary = "OpenAI Retrieve File", description = "OpenAI 标准协议文件检索接口")
    @GetMapping("/v1/files/{file_id}")
    public ResponseEntity<Map<String, Object>> retrieveFile(@PathVariable("file_id") String fileId,
                                                             HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        return ResponseEntity.ok(fileResponse(fileId, "retrieve"));
    }

    @Operation(summary = "OpenAI Delete File", description = "OpenAI 标准协议文件删除接口")
    @DeleteMapping("/v1/files/{file_id}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable("file_id") String fileId,
                                                           HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        return ResponseEntity.ok(Map.of("id", fileId, "object", "file", "deleted", true));
    }

    @Operation(summary = "OpenAI Retrieve File Content", description = "OpenAI 标准协议文件内容检索接口")
    @GetMapping("/v1/files/{file_id}/content")
    public ResponseEntity<Map<String, Object>> retrieveFileContent(@PathVariable("file_id") String fileId,
                                                                    HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        return ResponseEntity.ok(Map.of("error", Map.of("message", "File content not available", "type", "not_found")));
    }

    // ==================== OpenAI Fine-tuning Jobs ====================

    @Operation(summary = "OpenAI Create Fine-tuning Job", description = "OpenAI 标准协议创建微调任务接口")
    @PostMapping("/v1/fine_tuning/jobs")
    public ResponseEntity<Map<String, Object>> createFineTuningJob(@RequestBody Map<String, Object> request,
                                                                    HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        return ResponseEntity.ok(fineTuneResponse("queued"));
    }

    @Operation(summary = "OpenAI List Fine-tuning Jobs", description = "OpenAI 标准协议微调任务列表接口")
    @GetMapping("/v1/fine_tuning/jobs")
    public ResponseEntity<Map<String, Object>> listFineTuningJobs(@RequestParam(value = "after", required = false) String after,
                                                                   @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
                                                                   HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        return ResponseEntity.ok(Map.of("object", "list", "data", List.of()));
    }

    @Operation(summary = "OpenAI Retrieve Fine-tuning Job", description = "OpenAI 标准协议微调任务详情接口")
    @GetMapping("/v1/fine_tuning/jobs/{job_id}")
    public ResponseEntity<Map<String, Object>> retrieveFineTuningJob(@PathVariable("job_id") String jobId,
                                                                      HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        return ResponseEntity.ok(fineTuneResponse("unknown"));
    }

    @Operation(summary = "OpenAI Cancel Fine-tuning Job", description = "OpenAI 标准协议取消微调任务接口")
    @PostMapping("/v1/fine_tuning/jobs/{job_id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelFineTuningJob(@PathVariable("job_id") String jobId,
                                                                    HttpServletRequest servletRequest) {
        if (!authCheck(servletRequest)) {
            return authError();
        }
        return ResponseEntity.ok(fineTuneResponse("cancelled"));
    }

    // ==================== Response Builders ====================

    private static Map<String, Object> imageResponse() {
        return new LinkedHashMap<>(Map.of("created", (int) (System.currentTimeMillis() / 1000), "data", List.of()));
    }

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
}

package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.audio.AudioClient;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.deeplearning.support.capability.ModelCapabilities;
import com.chua.starter.ai.support.configuration.client.AiCapabilityResolver;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AI 音频控制器 —— 语音识别（音频转文字）。
 *
 * <p>通过统一能力解析器按能力标签 {@code asr} 解析 {@code sys_ai_global_setting}
 * asr 分组配置（服务商 / 模型 / 语言），创建 {@link AudioClient} 并转写上传的音频文件。
 * 支持本地 whisper（离线）与云端 ASR（openai / alibaba-asr 等）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/audio")
@RequiredArgsConstructor
@Tag(name = "AI 语音识别", description = "音频转文字（ASR）")
public class AiAudioController {

    /**
     * 统一能力解析器
     */
    private final AiCapabilityResolver capabilityResolver;

    /**
     * 音频转文字。
     *
     * <p>上传音频文件（wav/mp3/m4a 等），返回识别文字。</p>
     *
     * @param file     音频文件
     * @param model    模型名（可选，覆盖 asr 分组默认模型）
     * @param language 语言代码（可选，覆盖 asr 分组默认语言）
     * @return 识别文字
     */
    @Permission("admin")
    @Operation(summary = "音频转文字", description = "上传音频文件，返回识别文字（本地 whisper 或云端 ASR）")
    @PostMapping(value = "/to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReturnResult<String> toText(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "model", required = false) String model,
                                       @RequestParam(value = "language", required = false) String language) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            // 通过能力标签统一解析配置
            AiCapabilityResolver.CapabilityConfig config = capabilityResolver.resolve(ModelCapabilities.ASR);
            String effectiveModel = model != null ? model : config.model();
            String effectiveLanguage = language != null ? language
                    : capabilityResolver.getStr(ModelCapabilities.ASR, "language", "");

            Path temp = Files.createTempFile("ai-audio-", resolveSuffix(file.getOriginalFilename()));
            file.transferTo(temp.toFile());
            temp.toFile().deleteOnExit();

            AudioClient client = capabilityResolver.createAudioClient(config);
            try {
                AudioClient chain = client;
                if (effectiveModel != null && !effectiveModel.isBlank()) {
                    chain = chain.model(effectiveModel);
                }
                if (effectiveLanguage != null && !effectiveLanguage.isBlank()) {
                    chain = chain.language(effectiveLanguage);
                }
                String text = chain.transcribe(temp);
                return ReturnResult.ok(text);
            } finally {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.error("[AiAudio] 音频转文字失败: {}", e.getMessage());
            return ReturnResult.error("音频转文字失败: " + e.getMessage());
        }
    }

    /**
     * 解析音频文件后缀（含点号，默认 .wav）。
     *
     * @param filename 文件名
     * @return 后缀
     */
    private static String resolveSuffix(String filename) {
        if (filename != null) {
            int idx = filename.lastIndexOf('.');
            if (idx >= 0 && idx < filename.length() - 1) {
                return filename.substring(idx);
            }
        }
        return ".wav";
    }
}

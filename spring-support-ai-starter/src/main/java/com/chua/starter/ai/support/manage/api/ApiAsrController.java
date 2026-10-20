package com.chua.starter.ai.support.manage.api;

import com.chua.common.support.ai.audio.AudioClientSetting;
import com.chua.common.support.ai.audio.VirtualClient;
import com.chua.starter.ai.support.configuration.audio.AudioTextRefineService;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 音频识别（ASR）控制器。
 *
 * <p>提供真实转写能力：把上传的音频交给 {@link VirtualClient} SPI（本地 whisper /
 * sensevoice / sherpa 等，或云端 ASR）解码为文本。</p>
 *
 * <p><b>与 {@code /v1/audio/transcriptions} 的区别</b>：后者是 OpenAI 兼容壳，
 * 内部只是把 {@code "Transcribe audio: <文件名>"} 拼成 prompt 丢给聊天模型，
 * 并不调用任何 ASR 引擎 —— 那是占位实现，识别结果不可用。本控制器走
 * {@code asr} 分组的真实配置（provider / 密钥 / 地址 / 默认模型 / 语言），
 * 与前端「音频配置」设置页同源。</p>
 *
 * <p>配置口径：按 {@code asr} 分组读取<b>对当前用户生效</b>的合并值
 * （用户覆盖优先、全局默认兜底），因此每个账号可以用自己的模型与密钥，
 * 不必都由管理员在全局层配置。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/ai/asr")
@RequiredArgsConstructor
public class ApiAsrController {

    /**
     * ASR 设置分组标识（与 {@code AsrGlobalSettingProvider} 一致）。
     */
    private static final String GROUP = "asr";

    /**
     * 用户覆盖层服务（合并视图：用户覆盖 + 全局默认）。
     */
    private final SysAiUserSettingService userSettingService;

    /**
     * 音频管线的文字模型后处理（转写结果矫正）。留空关闭、失败保留原文。
     */
    private final AudioTextRefineService audioTextRefineService;

    /**
     * 转写结果矫正的系统指令。
     *
     * <p>刻意写得很窄：只改「看错了」的地方，不许改写、不许增删信息。
     * 音频转写是事实性产物，模型一旦"顺手润色"，用户拿到的就不再是原话了。</p>
     */
    private static final String CORRECT_SYSTEM_PROMPT =
            "你是语音识别结果矫正助手。只修正识别错误：同音字/近音字、专有名词与术语、标点与断句。"
                    + "不要改写语气，不要增删信息，不要解释，不要输出多余内容。"
                    + "无法确定的地方保持原样。只输出矫正后的文本。";

    /**
     * 上传音频并转写为文本。
     *
     * @param file 音频文件（wav / mp3 / m4a / flac 等，后端按模型要求解码）
     * @param model 覆盖默认模型（留空用 {@code asr.default_model}）
     * @param language 覆盖默认语言（留空用 {@code asr.language}）
     * @return 转写结果：{@code text} 全文、{@code segments} 分段、{@code language} 实际语种
     */
    @PostMapping(value = "/transcribe", consumes = "multipart/form-data")
    public ApiResult<Map<String, Object>> transcribe(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "language", required = false) String language) {

        String userId = AiUserContext.requireUserId();
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "音频文件不能为空");
        }

        Map<String, Object> merged = mergedAsrSettings(userId);
        String provider = text(merged.get("provider"));
        if (provider == null || provider.isBlank()) {
            throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                    "未配置 ASR 服务商，请先到「系统设置 → 音频配置」选择");
        }
        String selectedModel = firstNonBlank(model, text(merged.get("default_model")));
        String selectedLanguage = firstNonBlank(language, text(merged.get("language")));

        AudioClientSetting setting = AudioClientSetting.builder()
                .provider(provider)
                .appKey(text(merged.get("provider_api_key")))
                .baseUrl(text(merged.get("provider_base_url")))
                .model(selectedModel)
                .language(selectedLanguage)
                .build();

        byte[] audio;
        try {
            audio = file.getBytes();
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "读取音频文件失败: " + e.getMessage(), e);
        }

        try (VirtualClient client = VirtualClient.create(setting)) {
            if (client == null) {
                throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                        "未注册 ASR provider: " + provider);
            }
            // transcribe 只接受 Path，字节输入走 audio(byte[]) + transcribe()
            String text = client.audio(audio).transcribe();
            if (text == null || text.isBlank()) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "ASR 返回空结果，请检查模型是否可用");
            }
            // 管线后处理：配了文字模型（asr.corrector_model）才走，留空即关闭。
            // 放在 transcribe 成功之后：主产物已拿到，后处理失败也不会让它丢。
            String refined = audioTextRefineService.refine(
                    text, text(merged.get("corrector_model")), CORRECT_SYSTEM_PROMPT);
            return ApiResult.ok(buildResult(refined, selectedLanguage, provider, selectedModel));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[asr] 转写失败 provider={} model={} userId={}", provider, selectedModel, userId, e);
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "音频识别失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询当前用户生效的 ASR 配置摘要（供页面展示「用哪个模型」）。
     *
     * @return 配置摘要：provider / model / language（密钥不回显）
     */
    @PostMapping("/config")
    public ApiResult<Map<String, Object>> config() {
        String userId = AiUserContext.requireUserId();
        Map<String, Object> merged = mergedAsrSettings(userId);
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("provider", text(merged.get("provider")));
        out.put("model", text(merged.get("default_model")));
        out.put("language", text(merged.get("language")));
        // provider_api_key / provider_base_url 一律不回显：密钥即便已掩码也不下发
        return ApiResult.ok(out);
    }

    /**
     * 读取 {@code asr} 分组对指定用户生效的合并配置。
     *
     * @param userId 用户标识
     * @return field -> value 映射
     */
    private Map<String, Object> mergedAsrSettings(String userId) {
        List<Map<String, Object>> items = userSettingService.listMerged(userId, GROUP);
        Map<String, Object> map = new HashMap<>();
        if (items != null) {
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                Object field = item.get("field");
                if (field == null) {
                    continue;
                }
                map.put(String.valueOf(field), item.get("value"));
            }
        }
        return map;
    }

    /**
     * 组装转写响应。
     *
     * @param text   全文
     * @return 响应体
     */
    private Map<String, Object> buildResult(String text, String language,
                                            String provider, String model) {
        Map<String, Object> out = new LinkedHashMap<>(8);
        out.put("text", text);
        out.put("language", language);
        out.put("provider", provider);
        out.put("model", model);
        out.put("segments", List.of());
        return out;
    }

    /**
     * 取首个非空白值。
     *
     * @param values 候选值
     * @return 首个非空白值；全部空白返回 {@code null}
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 把对象安全转为去空白字符串。
     *
     * @param value 原始值
     * @return 字符串或 {@code null}
     */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}

package com.chua.starter.ai.support.manage.api;

import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.common.support.ai.audio.TextToAudioClientSetting;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本转语音（TTS）控制器，替代 nomicore 的 {@code /api/tts} 契约。
 *
 * <p><b>配置来源与优先级</b>：</p>
 * <ol>
 *   <li>{@code tts} 设置分组中<b>对当前用户生效</b>的合并值（用户覆盖优先、全局默认兜底）；
 *       该分组可在「系统设置 → 音频配置」页修改，按用户隔离。</li>
 *   <li>分组里没有的字段回落到 Spring 静态配置 {@code ai.tts.*}（application.yml）。
 *       保留回落是为了不改既有部署行为——它们只配了 {@code ai.tts.*} 而没碰设置页。</li>
 * </ol>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
public class ApiTtsController {

    /**
     * TTS 设置分组标识（与 {@code TtsGlobalSettingProvider} 一致）。
     */
    private static final String GROUP = "tts";

    /**
     * 用户覆盖层服务（合并视图：用户覆盖 + 全局默认）。
     */
    private final SysAiUserSettingService userSettingService;

    @Value("${ai.tts.provider:}")
    private String provider;

    @Value("${ai.tts.api-key:}")
    private String apiKey;

    @Value("${ai.tts.base-url:}")
    private String baseUrl;

    @Value("${ai.tts.model:}")
    private String defaultModel;

    @Value("${ai.tts.voice:}")
    private String defaultVoice;

    @Value("${ai.tts.format:mp3}")
    private String defaultFormat;

    @PostMapping
    public ResponseEntity<byte[]> tts(@RequestBody Map<String, Object> body,
                                       @RequestParam(required = false) String voice) {
        String text = bodyText(body);
        if (text.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "text 不能为空");
        }

        Map<String, Object> merged = mergedTtsSettings();
        String selectedProvider = firstNonBlank(text(merged.get("provider")), provider);
        if (selectedProvider == null) {
            throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                    "未配置 TTS 服务商，请在「系统设置 → 音频配置」选择，或配置 ai.tts.provider");
        }

        String model = firstNonBlank(text(body.get("model")), text(merged.get("default_model")), defaultModel);
        String selectedVoice = firstNonBlank(text(body.get("voice")), text(voice),
                text(merged.get("tts_voice")), defaultVoice);
        String format = firstNonBlank(text(body.get("format")), text(merged.get("tts_format")), defaultFormat);
        String language = firstNonBlank(text(body.get("language")), text(merged.get("language")), "");
        Double speed = numberValue(merged.get("tts_speed"));

        TextToAudioClientSetting setting = TextToAudioClientSetting.builder()
                .provider(selectedProvider)
                .appKey(firstNonBlank(text(merged.get("provider_api_key")), apiKey))
                .baseUrl(firstNonBlank(text(merged.get("provider_base_url")), baseUrl))
                .model(model)
                .voice(selectedVoice)
                .language(language)
                .format(format)
                .speed(speed)
                .build();

        try (TextToAudioClient client = TextToAudioClient.create(setting)) {
            if (client == null) {
                throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                        "未注册 TTS provider: " + selectedProvider);
            }
            byte[] audio = client.synthesize(text);
            if (audio == null || audio.length == 0) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "TTS 服务返回空音频");
            }
            return ResponseEntity.ok()
                    .contentType(audioMediaType(format))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(audio);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "TTS 合成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询当前用户生效的 TTS 配置摘要（供页面展示「用哪个模型/音色」）。
     *
     * @return 配置摘要：provider / model / voice / format；密钥与地址不回显
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> merged = mergedTtsSettings();
        Map<String, Object> out = new HashMap<>(8);
        out.put("provider", firstNonBlank(text(merged.get("provider")), provider));
        out.put("model", firstNonBlank(text(merged.get("default_model")), defaultModel));
        out.put("voice", firstNonBlank(text(merged.get("tts_voice")), defaultVoice));
        out.put("format", firstNonBlank(text(merged.get("tts_format")), defaultFormat));
        out.put("language", text(merged.get("language")));
        return ResponseEntity.ok(out);
    }

    /**
     * 读取 {@code tts} 分组对当前用户生效的合并配置。
     *
     * <p>未登录时按「全局默认」读（不抛错）：TTS 允许匿名调用是既有契约
     * （{@code /api/tts} 原本没有用户校验），不能因新增分组把它收紧。</p>
     *
     * @return field -> value 映射
     */
    private Map<String, Object> mergedTtsSettings() {
        Map<String, Object> map = new HashMap<>();
        String userId = null;
        try {
            userId = AiUserContext.currentUserId();
        } catch (Exception ignored) {
            // 未登录：按全局默认读，见方法注释
        }
        try {
            List<Map<String, Object>> items = userSettingService.listMerged(userId, GROUP);
            if (items != null) {
                for (Map<String, Object> item : items) {
                    if (item == null) {
                        continue;
                    }
                    Object field = item.get("field");
                    if (field != null) {
                        map.put(String.valueOf(field), item.get("value"));
                    }
                }
            }
        } catch (Exception e) {
            // 读取失败按未配置处理：回落到 ai.tts.* 静态配置
            return map;
        }
        return map;
    }

    private static String bodyText(Map<String, Object> body) {
        Object value = body == null ? null : body.get("text");
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value);
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

    /**
     * 把对象安全转为正数；非法或非正返回 {@code null}（交给客户端用默认值）。
     *
     * @param value 原始值
     * @return 数值或 {@code null}
     */
    private static Double numberValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(String.valueOf(value).trim());
            return parsed > 0D ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static MediaType audioMediaType(String format) {
        if (format == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return switch (format.toLowerCase()) {
            case "wav" -> MediaType.parseMediaType("audio/wav");
            case "mp3" -> MediaType.parseMediaType("audio/mpeg");
            case "ogg" -> MediaType.parseMediaType("audio/ogg");
            case "flac" -> MediaType.parseMediaType("audio/flac");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}

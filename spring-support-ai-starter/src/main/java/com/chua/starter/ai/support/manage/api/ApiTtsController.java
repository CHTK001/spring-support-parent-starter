package com.chua.starter.ai.support.manage.api;

import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.common.support.ai.audio.TextToAudioClientSetting;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 文本转语音（TTS）控制器，替代 nomicore 的 {@code /api/tts} 契约。
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/tts")
public class ApiTtsController {

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
        if (provider == null || provider.isBlank()) {
            throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "未配置 ai.tts.provider，TTS 服务不可用");
        }

        String model = stringValue(body.get("model"), defaultModel);
        String selectedVoice = stringValue(body.get("voice"), stringValue(voice, defaultVoice));
        String format = stringValue(body.get("format"), defaultFormat);
        TextToAudioClientSetting setting = TextToAudioClientSetting.builder()
                .provider(provider)
                .appKey(apiKey)
                .baseUrl(baseUrl)
                .model(model)
                .voice(selectedVoice)
                .language(stringValue(body.get("language"), ""))
                .format(format)
                .build();

        try (TextToAudioClient client = TextToAudioClient.create(setting)) {
            if (client == null) {
                throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                        "未注册 TTS provider: " + provider);
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

package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
 * <p>正常情况下该端点应返回二进制音频流（{@code audio/*}）。本内存后端未接入任何 TTS 引擎，
 * 故仅返回 JSON 错误信封（{@code NO_COMPLETER}，HTTP 409）以符合契约形状，避免返回非 JSON
 * 响应被前端判为契约违规。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
@Slf4j
public class ApiTtsController {

    /**
     * 生成语音（占位实现）。
     *
     * <p>未接入 TTS 引擎，返回 {@code NO_COMPLETER} 错误信封，HTTP 状态 409。</p>
     *
     * @param body    请求体（含 text / voice 等字段，本实现忽略）
     * @param voice   可选音色参数
     * @return JSON 错误信封
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResult<Void>> tts(@RequestBody(required = false) Map<String, Object> body,
                                               @RequestParam(required = false) String voice) {
        String text = body == null ? "" : String.valueOf(body.getOrDefault("text", ""));
        log.warn("[tts] 未接入 TTS 引擎，返回 NO_COMPLETER；text={}, voice={}", text, voice);
        ApiResult<Void> envelope = ApiResult.error(
                ApiErrorCode.NO_COMPLETER.getCode(),
                "当前后端未接入 TTS 引擎，无法生成语音"
        );
        return ResponseEntity.status(HttpStatus.valueOf(ApiErrorCode.NO_COMPLETER.getHttpStatus()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(envelope);
    }
}

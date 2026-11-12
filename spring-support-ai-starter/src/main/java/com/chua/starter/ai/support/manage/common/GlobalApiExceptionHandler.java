package com.chua.starter.ai.support.manage.common;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.regex.Pattern;

/**
 * 全局异常处理器。
 *
 * <p>将所有异常统一转换为前端契约信封 {@link ApiResult}（{@code success:false,code,error}），
 * 并保证响应体始终为 JSON（非 JSON 响应会被前端判为契约违规）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestControllerAdvice("com.chua.starter.ai.support.manage")
public class GlobalApiExceptionHandler {

    /**
     * 沿 cause 链查找限流异常时的最大深度。
     *
     * <p>实测最深 3 层（PipelineException → RuntimeException → RateLimitException），
     * 留 10 层余量，同时避免异常链成环时无限循环。</p>
     */
    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * 处理业务契约异常。
     *
     * @param ex 业务异常
     * @return 错误信封与对应 HTTP 状态码
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> handleApiException(ApiException ex) {
        ApiErrorCode code = ex.getErrorCode();
        log.warn("[api-ex] 业务异常: code={}, message={}", code.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.valueOf(code.getHttpStatus()))
                .body(ApiResult.errorDetail(code.getCode(), ex.getMessage(), ex.getDetail()));
    }

    /**
     * 处理「缺少必填请求参数」。
     *
     * <p>这类错误的成因在**调用方**，必须回 400。此前没有任何分支接住它，
     * 于是落进下面 {@code Exception} 的兜底分支变成 HTTP 500 + {@code INTERNAL_ERROR}：
     * 实测 {@code GET /api/requirements/board} 少传 {@code tag} 时返回 500，
     * 调用方漏参被报成服务端故障，会直接污染错误率监控与告警（4xx 本来不该计入 5xx 指标）。
     * 同族的 {@code /api/requirements}、{@code /tags}、{@code /data-sources} 都是正常 200，
     * 说明只是缺了这条映射，不是接口本身坏了。</p>
     *
     * @param ex 缺少必填参数异常
     * @return 400 与参数错误信封
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("[api-ex] 缺少必填参数: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.error(ApiErrorCode.INVALID_PARAM.getCode(),
                        "缺少必填参数：" + ex.getParameterName()));
    }

    /**
     * 处理未预期异常。
     *
     * <p>上游模型服务限流（OpenAI / Anthropic / 各家兼容接口都会返 429）必须在此分流成
     * 429：它不是「系统内部错误」，用户看到的也应该是「请求太频繁」而不是「系统异常」。
     * 此前没有任何异常能走到 {@link ApiErrorCode#RATE_LIMITED}，限流被这条兜底分支
     * 统一吞成 500，实测表现为：连问第三句时 {@code /ask} 返回 500，引用列表为空。</p>
     *
     * @param ex 异常
     * @return 限流为 429，其余为内部错误信封（HTTP 500）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception ex) {
        Throwable rateLimited = findRateLimitedCause(ex);
        if (rateLimited != null) {
            log.warn("[api-ex] 上游限流: {}", rateLimited.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResult.error(ApiErrorCode.RATE_LIMITED.getCode(),
                            "AI 服务请求过于频繁，请稍后重试；若持续出现请升级 API 套餐"));
        }
        log.error("[api-ex] 未预期异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error(ApiErrorCode.INTERNAL_ERROR.getCode(), ex.getMessage()));
    }

    /**
     * 沿 cause 链查找「上游限流」异常。
     *
     * <p>限流异常总是被多层包装后才冒出来，实测链路为
     * {@code PipelineException → RuntimeException → com.openai.errors.RateLimitException}，
     * 只看最外层会漏掉。</p>
     *
     * @param ex 异常链起点
     * @return 命中的限流异常；未命中返回 null
     */
    private Throwable findRateLimitedCause(Throwable ex) {
        Throwable current = ex;
        for (int depth = 0; depth < MAX_CAUSE_DEPTH && current != null; depth++) {
            if (isRateLimited(current)) {
                return current;
            }
            // 自引用（getCause()==this）会让循环永不退出，这里显式挡住
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }

    /**
     * 判断一个异常是否代表上游限流。
     *
     * <p>刻意不写成 {@code t instanceof com.openai.errors.RateLimitException}：
     * 那会让本类硬依赖某一家供应商的 SDK，换供应商就得改这里。按类名与报文特征识别，
     * 各家 SDK 的限流异常都能命中。</p>
     *
     * @param t 待判断异常
     * @return 命中限流特征返回 true
     */
    private boolean isRateLimited(Throwable t) {
        String className = t.getClass().getName();
        if (className.contains("RateLimit") || className.contains("TooManyRequests")) {
            return true;
        }
        String message = t.getMessage();
        if (message == null) {
            return false;
        }
        // 先认「限流语义」，这一路不依赖具体措辞
        if (message.contains("rate limit") || message.contains("Rate limit")
                || message.contains("Too Many Requests") || message.contains("Too many requests")) {
            return true;
        }
        // 再退一步只认状态码 429。
        // 必须同时排除「第 429 号分片」这类把数字当中文序数用的句子，
        // 否则会把分片读失败误判成限流。
        return message.contains("429") && !CHINESE_ORDINAL_429.matcher(message).find();
    }

    /**
     * 把「429」当中文序数用的句子（分片号、编号、行号等），用于避免误判为限流。
     *
     * <p>覆盖两种语序：「第 429 号分片」与「分片 429 号」——后者最初漏掉了，
     * 被验收的反例用例抓到（误报成 429）。所以只要 429 紧邻中文量词，
     * 就当序号处理，不再要求「第」在前。</p>
     */
    private static final Pattern CHINESE_ORDINAL_429 =
            Pattern.compile("(第\\s*429\\s*(号|分片|条|行|个|页|批|次)"
                    + "|(号|分片|条|行|个|页|批|次)\\s*429\\s*(号|分片|条|行|个|页|批)?)");
}

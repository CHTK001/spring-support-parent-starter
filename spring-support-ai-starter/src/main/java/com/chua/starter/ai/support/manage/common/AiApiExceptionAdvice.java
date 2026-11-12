package com.chua.starter.ai.support.manage.common;

import com.chua.common.support.lang.code.ReturnResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * {@link ApiException} 全局兜底处理器。
 *
 * <p>{@code GlobalApiExceptionHandler} 只覆盖 {@code support.manage} 包，控制台业务控制器位于
 * {@code support.controller} 等包，其抛出的 {@link ApiException} 会被框架的通用运行时异常兜底成
 * 500「系统繁忙」，用户看不到可操作的提示。本处理器按异常自带的状态码返回，优先级高于通用兜底。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AiApiExceptionAdvice {

    /**
     * 处理业务契约异常，保持异常声明的 HTTP 状态与提示语。
     *
     * @param e 业务异常
     * @return 带原始提示语的标准响应体
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ReturnResult<Object>> handleApiException(ApiException e) {
        int httpStatus = e.getErrorCode().getHttpStatus();
        log.warn("[业务异常] code={}, http={}, msg={}", e.getErrorCode().getCode(), httpStatus, e.getMessage());
        // 直接用异常自带的业务码：框架按 HTTP 状态映射会把 403 统一打成 A0403S0000，
        // 那个码是 oauth 表达「未登录/请重新登录」用的，会让越权被前端误判为会话失效
        Map<String, Object> detail = e.getDetail();
        if (detail == null || detail.isEmpty()) {
            return ResponseEntity.status(httpStatus)
                    .body(ReturnResult.error(e.getErrorCode().getCode(), e.getMessage()));
        }
        // 明细不能随信封一起丢：里面是「生效模型 / 脱敏地址 / 失败节点」，
        // 前端靠它折叠出用户能照着改的排查信息。ReturnResult 没有 detail 字段，
        // 这里用它的子类补上，其余字段与 ReturnResult.error 完全一致。
        return ResponseEntity.status(httpStatus)
                .body(ApiErrorResult.withDetail(e.getErrorCode().getCode(), e.getMessage(), detail));
    }

    /**
     * 带结构化排查信息的失败信封。
     *
     * <p>{@link ReturnResult} 只有 code/data/msg，没有 detail；问答失败时
     * {@link ApiException} 携带的明细若随信封一起被丢弃，用户只会看到一句
     * 「模型调用未成功」，无从知道是哪个模型、哪个地址、卡在哪个节点。</p>
     *
     * @since 4.0.0.51
     * @author CH
     */
    @Getter
    public static final class ApiErrorResult extends ReturnResult<Object> {

        private static final long serialVersionUID = 1L;

        /**
         * 结构化排查信息（生效模型 / 脱敏地址 / 失败节点等）
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Map<String, Object> detail;

        /**
         * 构造带明细的失败信封。
         *
         * @param code   机器可读错误码
         * @param msg    人类可读信息
         * @param detail 结构化排查信息
         */
        private ApiErrorResult(String code, String msg, Map<String, Object> detail) {
            super(code, null, msg);
            this.detail = detail;
        }

        /**
         * 构造带明细的失败信封。
         *
         * @param code   机器可读错误码
         * @param msg    人类可读信息
         * @param detail 结构化排查信息
         * @return 失败信封
         */
        public static ApiErrorResult withDetail(String code, String msg, Map<String, Object> detail) {
            return new ApiErrorResult(code, msg, detail);
        }
    }
}

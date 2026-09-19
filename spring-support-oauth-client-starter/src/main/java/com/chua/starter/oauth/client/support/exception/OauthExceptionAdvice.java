package com.chua.starter.oauth.client.support.exception;

import com.chua.common.support.lang.code.ResultCode;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * o认证 异常全局处理器
 * <p>
 * {@link OauthException} 由 权限拦截器 / 令牌类型拦截器 抛出，属于可预期的访问控制结果，
 * 必须按异常自带的 http 状态码返回；否则会被通用 运行时异常 兜底为 500「系统繁忙」，
 * 前端无法区分「未登录」与「无权限」。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class OauthExceptionAdvice {

    /**
     * 处理 o认证 异常
     *
     * @param e o认证 异常
     * @return 带原始提示语的标准响应体
     */
    @ExceptionHandler(OauthException.class)
    public ResponseEntity<ReturnResult<Object>> handleOauthException(OauthException e) {
        int httpStatus = e.getHttpStatus();
        ReturnResult<Object> body = httpStatus == HttpStatus.FORBIDDEN.value()
                // A0403S0000 属于「请重新登录」码段，前端收到会清令牌；只读拦截与角色不足
                // 都是会话有效期间的授权结果，必须用 ACCESS_DENIED 表达，否则越权提示会变成强制登出
                ? ReturnResult.error(ReturnCode.ACCESS_DENIED.getCode(), e.getMessage())
                : ReturnResult.error(ResultCode.transferForHttpCode(httpStatus).getCode(), e.getMessage());
        return ResponseEntity.status(httpStatus).body(body);
    }
}

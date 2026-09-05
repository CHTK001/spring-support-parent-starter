package com.chua.starter.test.config;

import com.chua.starter.oauth.client.support.exception.OauthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 应用级异常映射：{@link OauthException}（权限/角色校验失败）统一返回 403。
 *
 * <p>框架抛出 {@link OauthException} 表示无权限，应用层按需映射为 HTTP 状态码。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@RestControllerAdvice
public class OauthExceptionAdvice {

    @ExceptionHandler(OauthException.class)
    public ResponseEntity<Map<String, Object>> handleOauthException(OauthException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", 403, "message", String.valueOf(ex.getMessage())));
    }
}

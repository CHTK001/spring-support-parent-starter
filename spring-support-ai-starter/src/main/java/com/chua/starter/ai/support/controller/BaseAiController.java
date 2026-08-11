package com.chua.starter.ai.support.controller;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.service.AiAccessGuardService;
import com.chua.starter.ai.support.service.SysAiAppKeyService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 控制器统一基类，封装认证、分组解析、全局开关等公共能力。
 *
 * <p>所有 AI 协议控制器（OpenAI、Claude、Gemini、扩展接口等）均应继承此类，
 * 避免重复实现鉴权与分组解析逻辑。</p>
 *
 * <h3>提供的公共方法</h3>
 * <ul>
 *   <li>{@link #authCheck(HttpServletRequest)} — Bearer Token 鉴权</li>
 *   <li>{@link #resolveGroupId(HttpServletRequest)} — 从 Token 解析分组 ID</li>
 *   <li>{@link #restEnabled()} — 查询 REST 开关状态</li>
 *   <li>{@link #disabledEmitter(String)} — 开关禁用状态 SSE 响应</li>
 *   <li>{@link #authErrorEmitter()} — 认证失败 SSE 响应</li>
 *   <li>{@link #authErrorResponse()} — 认证失败 JSON 响应</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public abstract class BaseAiController {

    /**
     * Bearer Token 前缀
     */
    protected static final String BEARER_PREFIX = "Bearer ";

    /**
     * Bearer Token 前缀长度
     */
    protected static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    /**
     * SSE 事件流超时时间（毫秒）
     */
    protected static final long SSE_TIMEOUT_MILLIS = 30_000L;

    /**
     * 访问守卫服务，提供 AppKey 与 Bearer Token 校验能力
     */
    @Autowired
    protected AiAccessGuardService aiAccessGuardService;

    /**
     * AppKey 服务，提供分组 ID 查询等能力
     */
    @Autowired
    protected SysAiAppKeyService sysAiAppKeyService;

    /**
     * 全局设置服务，提供运行时开关读写能力
     */
    @Autowired
    protected SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * Jackson JSON 序列化器，用于响应构建
     */
    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Bearer Token 鉴权校验。
     *
     * <p>从请求头 {@code Authorization} 中提取 Bearer Token，
     * 委托 {@link AiAccessGuardService#verifyBearerToken(String, String)} 进行校验。</p>
     *
     * @param request HTTP 请求
     * @return 校验通过返回 {@code true}，否则返回 {@code false}
     */
    protected boolean authCheck(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        return aiAccessGuardService.verifyBearerToken(auth, request.getRemoteAddr());
    }

    /**
     * 从 Authorization 请求头中提取 Bearer Token。
     *
     * @param request HTTP 请求
     * @return Bearer Token，无法提取时返回 {@code null}
     */
    private String extractBearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (StringUtils.isBlank(auth) || !auth.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = auth.substring(BEARER_PREFIX_LENGTH).trim();
        if (StringUtils.isBlank(token)) {
            return null;
        }
        return token;
    }

    /**
     * 从 Authorization 请求头中解析 AppKey 所属的分组 ID。
     *
     * <p>提取 Bearer Token 后调用 {@link SysAiAppKeyService#getGroupIdByAppKey(String)}
     * 获取分组 ID，用于后续的模型隔离与限流。</p>
     *
     * @param request HTTP 请求
     * @return 分组 ID，若无法解析则返回 {@code null}
     */
    protected Integer resolveGroupId(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token == null) {
            return null;
        }
        Integer gid = sysAiAppKeyService.getGroupIdByAppKey(token);
        log.debug("resolveGroupId: token={}, groupId={}", token, gid);
        return gid;
    }

    /**
     * 从 Authorization 请求头中提取 Bearer Token 值（即 AppKey）。
     *
     * <p>用于在 {@link com.chua.starter.ai.support.model.AiChatRequest} 的 parameters 中注入
     * {@code _appKey} 字段，供底层服务在用量记录时同步更新 AppKey 的 Token 配额。</p>
     *
     * @param request HTTP 请求
     * @return Bearer Token 字符串，无法提取时返回 {@code null}
     */
    protected String resolveAppKey(HttpServletRequest request) {
        return extractBearerToken(request);
    }

    /**
     * 查询 REST API 开关是否启用。
     *
     * <p>从全局设置中读取 {@code runtime.openaiRestEnabled} 配置项，
     * 未配置时默认启用。</p>
     *
     * @return 启用返回 {@code true}，禁用返回 {@code false}
     */
    protected boolean restEnabled() {
        return sysAiGlobalSettingService.getBoolean("runtime", "openaiRestEnabled", true);
    }

    /**
     * 构建服务禁用状态的 SSE 事件流响应。
     *
     * <p>发送一个 {@code error} 事件后立即发送 {@code [DONE]} 结束标志。</p>
     *
     * @param message 禁用原因描述
     * @return SSE 发射器
     */
    protected SseEmitter disabledEmitter(String message) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("error").data(message, MediaType.TEXT_PLAIN));
                emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    /**
     * 构建认证失败状态的 SSE 事件流响应。
     *
     * <p>发送 {@code "AI AppKey 校验失败"} 错误事件后结束流。</p>
     *
     * @return SSE 发射器
     */
    protected SseEmitter authErrorEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("error").data("AI AppKey 校验失败", MediaType.TEXT_PLAIN));
                emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    /**
     * 构建认证失败的 JSON 格式 HTTP 响应。
     *
     * <p>返回 HTTP 401 状态码，响应体遵循 OpenAI 标准错误格式：</p>
     * <pre>{@code
     * {
     *   "error": {
     *     "message": "AI AppKey 校验失败",
     *     "type": "authentication_error"
     *   }
     * }
     * }</pre>
     *
     * @return 401 响应实体
     */
    protected ResponseEntity<Map<String, Object>> authErrorResponse() {
        return ResponseEntity.status(401).body(Map.of(
                "error", Map.of("message", "AI AppKey 校验失败", "type", "authentication_error")
        ));
    }
}
package com.chua.starter.ai.support.controller;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.manage.LocalTrustAuthFilter;
import com.chua.starter.ai.support.service.AiAccessGuardService;
import com.chua.starter.ai.support.service.AppKeyModelScope;
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
import java.util.Set;
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
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public abstract class BaseAiController {

    /**
     * Bearer 令牌 前缀
     */
    protected static final String BEARER_PREFIX = "Bearer ";

    /**
     * Bearer 令牌 前缀长度
     */
    protected static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    /**
     * SSE 事件流超时时间（毫秒）
     */
    protected static final long SSE_TIMEOUT_MILLIS = 30_000L;

    /**
     * 访问守卫服务，提供 appKey 与 Bearer 令牌 校验能力
     */
    @Autowired
    /**
     * aiaccessguard服务
    */
    protected AiAccessGuardService aiAccessGuardService;

    /**
     * appKey 服务，提供分组 标识 查询等能力
     */
    @Autowired
    /**
     * SYSAIAPP密钥服务
    */
    protected SysAiAppKeyService sysAiAppKeyService;

    /**
     * 全局设置服务，提供运行时开关读写能力
     */
    @Autowired
    /**
     * SYSAI全局设置服务
    */
    protected SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * Jackson JSON 序列化器，用于响应构建
     */
    @Autowired
    /**
     * Objectmapper
    */
    protected ObjectMapper objectMapper;

    /**
     * AppKey 模型范围解析（分组清单 ∩ Key 限定模型），与服务实例端口共用同一份判定
     */
    @Autowired
    /**
     * AppKey模型范围
    */
    protected AppKeyModelScope appKeyModelScope;

    /**
     * Bearer 令牌 鉴权校验。
     *
     * <p>从请求头 {@code Authorization} 中提取 Bearer Token，
     * 委托 {@link AiAccessGuardService#verifyBearerToken(String, String)} 进行校验。</p>
     *
     * @param request HTTP 请求
     * @return 校验通过返回 {@code true}，否则返回 {@code false}
     */
    protected boolean authCheck(HttpServletRequest request) {
 // Tauri / 本地开发模式：本地信任认证过滤器 已置位 nomifun.本地.信任=true，直接放行
        Object trusted = request.getAttribute(LocalTrustAuthFilter.TRUSTED_ATTR);
        if (Boolean.TRUE.equals(trusted)) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        return aiAccessGuardService.verifyBearerToken(auth, request.getRemoteAddr());
    }

    /**
     * 从 授权 请求头中提取 Bearer 令牌。
     *
     * @param request HTTP 请求
     * @return Bearer 令牌，无法提取时返回 {@code null}
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
     * 从 授权 请求头中解析 appKey 所属的分组 标识。
     *
     * <p>提取 Bearer Token 后调用 {@link SysAiAppKeyService#getGroupIdByAppKey(String)}
     * 获取分组 标识，用于后续的模型隔离与限流。</p>
     *
     * @param request HTTP 请求
     * @return 分组 标识，若无法解析则返回 {@code null}
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
     * 从 授权 请求头中提取 Bearer 令牌 值（即 appKey）。
     *
     * <p>用于在 {@link com.chua.starter.ai.support.model.AiChatRequest} 的 parameters 中注入
     * {@code _appKey} 字段，供底层服务在用量记录时同步更新 AppKey 的 Token 配额。</p>
     *
     * @param request HTTP 请求
     * @return Bearer 令牌 字符串，无法提取时返回 {@code null}
     */
    protected String resolveAppKey(HttpServletRequest request) {
        return extractBearerToken(request);
    }

    /**
     * 当前请求的 appKey 允许调用的模型范围。
     *
     * <p>三态语义与 {@link AppKeyModelScope} 一致：{@code null} 不限制、空集一个都不许、
     * 非空只许集合内。以下情况一律返回 {@code null}（不做模型收敛）：</p>
     * <ul>
     *   <li>本地信任模式（Tauri / 本地开发）置位了免鉴权标记；</li>
     *   <li>没带 Bearer 凭证，或凭证不是 appKey（内部调用、老登录态）。</li>
     * </ul>
     *
     * @param request HTTP 请求
     * @return 允许的模型集合；{@code null} 表示不限制
     */
    protected Set<String> requestModelScope(HttpServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute(LocalTrustAuthFilter.TRUSTED_ATTR))) {
            return null;
        }
        String appKey = resolveAppKey(request);
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        SysAiAppKey row;
        try {
            // 实时查：停用 / 改分组 / 改限定模型后必须立刻生效，不能等缓存过期
            row = sysAiAppKeyService.getByAppKeyLive(appKey);
        } catch (Exception e) {
            log.warn("[ai] 读取 appKey 失败，本次不做模型收敛: {}", e.getMessage());
            return null;
        }
        if (row == null) {
            return null;
        }
        return appKeyModelScope.resolve(row);
    }

    /**
     * 校验请求模型是否越过 appKey 的模型范围。
     *
     * <p>越权用 403 而不是 401：凭证是有效的，只是不许用这个模型；401 会让客户端
     * 以为 Key 失效而直接停用配置，排查方向也会跑偏。</p>
     *
     * @param request HTTP 请求
     * @param model   本次请求的模型名，可为 null
     * @return 允许调用返回 {@code null}；越权返回可直接回给客户端的错误文案
     */
    protected String modelScopeViolation(HttpServletRequest request, String model) {
        Set<String> scope = requestModelScope(request);
        if (AppKeyModelScope.allows(scope, model)) {
            return null;
        }
        if (scope.isEmpty()) {
            return "该 AppKey 未分配任何模型，请联系管理员在分组或 Key 上配置可用模型";
        }
        String wanted = model == null || model.isBlank() ? "（未指定模型）" : model.trim();
        return "该 AppKey 不允许调用模型 " + wanted + "，可用模型：" + String.join(",", scope);
    }

    /**
     * 模型越权的 JSON 响应（OpenAI 错误体形状）。
     *
     * @param message 越权说明，不能为 null
     * @return 403 响应实体
     */
    protected ResponseEntity<Map<String, Object>> modelNotAllowedResponse(String message) {
        return ResponseEntity.status(403).body(Map.of(
                "error", Map.of("message", message, "type", "model_not_allowed")));
    }

    /**
     * 模型越权的 SSE 响应（流式请求在开流前就拦下，客户端能立刻拿到原因）。
     *
     * @param message 越权说明，不能为 null
     * @return SSE 发射器
     */
    protected SseEmitter modelNotAllowedEmitter(String message) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .data(Map.of("error", Map.of("message", message, "type", "model_not_allowed")),
                                MediaType.APPLICATION_JSON));
                emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    /**
     * 查询 REST API 开关是否启用。     *
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
     * }</pre>    *   }
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

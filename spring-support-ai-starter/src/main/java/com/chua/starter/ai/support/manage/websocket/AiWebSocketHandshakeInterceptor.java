package com.chua.starter.ai.support.manage.websocket;

import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.Protocol;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * {@code /ws} 握手身份拦截器。
 *
 * <p>过滤器链只覆盖 {@code blockAddress} 里配置的地址，WebSocket 升级请求默认不在其中，
 * 因此升级阶段拿不到登录身份。这里按 REST 侧同一口径自行校验令牌：读 {@code Authorization: Bearer}、
 * 配置里的令牌头与同名查询参数，交给认证协议验证，通过后把归属用户写进握手属性，
 * 供 {@link SysAiWebSocketHandler} 按用户定向投递事件。</p>
 *
 * <p>认证协议未启用（单机/开发形态）时不存在用户维度，连接统一绑定到
 * {@link SysAiWebSocketHandler#AUTH_DISABLED_OWNER} 通道，行为与认证启用前的广播一致。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AiWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    /**
     * Bearer 前缀（与 {@code WebRequest} 取令牌的口径一致）
     */
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        AuthClientProperties properties = OauthClientRuntimeContext.getAuthClientProperties();
        if (properties == null || !properties.isEnable()) {
            attributes.put(AiUserContext.USER_ID_ATTRIBUTE, SysAiWebSocketHandler.AUTH_DISABLED_OWNER);
            UserResume disabledResume = new UserResume();
            disabledResume.setUserId(SysAiWebSocketHandler.AUTH_DISABLED_OWNER);
            attributes.put(AiUserContext.RESUME_ATTRIBUTE, disabledResume);
            return true;
        }
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("[nomifun-ws] 握手被拒绝：非 Servlet 升级请求");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        HttpServletRequest raw = servletRequest.getServletRequest();
        UserResume resume = resolveResume(properties, raw);
        if (resume == null) {
            log.warn("[nomifun-ws] 握手被拒绝：无有效登录身份 uri={}", raw.getRequestURI());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(AiUserContext.USER_ID_ATTRIBUTE, resume.getUserId());
        attributes.put(AiUserContext.RESUME_ATTRIBUTE, resume);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    /**
     * 按认证协议校验升级请求携带的令牌。
     *
     * @param properties 认证配置
     * @param raw        升级 请求
     * @return 用户信息，校验不通过返回 {@code null}
     */
    private UserResume resolveResume(AuthClientProperties properties, HttpServletRequest raw) {
        Protocol protocol = WebRequest.getProtocolExtension(properties);
        if (protocol == null) {
            log.error("[nomifun-ws] 握手无法校验令牌：认证协议未注册");
            return null;
        }
        String token = resolveToken(properties, raw);
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            AuthenticationInformation result = protocol.approve(raw.getCookies(), token, raw.getHeader("x-oauth-type"));
            UserResume resume = result == null ? null : result.getReturnResult();
            if (resume == null || resume.getUserId() == null || resume.getUserId().isBlank()) {
                return null;
            }
            return resume;
        } catch (Exception e) {
            log.warn("[nomifun-ws] 握手令牌校验异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 读取升级请求里的令牌（头优先，其次同名查询参数）。
     *
     * @param properties 认证配置
     * @param raw        升级 请求
     * @return 令牌，未携带返回 {@code null}
     */
    private String resolveToken(AuthClientProperties properties, HttpServletRequest raw) {
        String token = raw.getHeader(properties.getTokenName());
        if (token != null && !token.isBlank()) {
            return token;
        }
        String authorization = raw.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return raw.getParameter(properties.getTokenName());
    }
}

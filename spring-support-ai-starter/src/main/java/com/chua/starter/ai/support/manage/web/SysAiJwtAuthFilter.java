package com.chua.starter.ai.support.manage.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;

/**
 * nomifun 网关 JWT 认证过滤器。
 *
 * <p>三种模式（按优先级）：
 * <ol>
 *   <li><b>local-trust</b>：header {@code x-nomi-local-trust} 非空 → 放行（开发/本地模式）</li>
 *   <li><b>JWT Bearer</b>：header {@code Authorization: Bearer &lt;jwt&gt;} → 验证签名 + 过期时间</li>
 *   <li><b>anonymous</b>：无 token → 放行（允许未登录访问公开页面）</li>
 * </ol>
 *
 * <p>验证通过后，将 {@code sub} claim 写入请求属性 {@code nomifun.user.id}。
 * JWT secret 从系统属性 {@code nomifun.auth.jwt-secret} 读取，默认空（跳过验签，便于联调）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SysAiJwtAuthFilter extends OncePerRequestFilter {

    private static final String SECRET_PROP = "nomifun.auth.jwt-secret";
    private static final String USER_ID_ATTR = "nomifun.user.id";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // local-trust 模式（开发/本地部署）
        if (StringUtils.hasText(request.getHeader("x-nomi-local-trust"))) {
            request.setAttribute("nomifun.auth.mode", "local-trust");
            chain.doFilter(request, response);
            return;
        }

        // JWT Bearer 模式
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            try {
                SignedJWT signedJWT = SignedJWT.parse(token);
                String secret = System.getProperty(SECRET_PROP, "");
                if (StringUtils.hasText(secret)) {
                    JWSVerifier verifier = new MACVerifier(secret);
                    if (!signedJWT.verify(verifier)) {
                        sendJsonError(response, 401, "JWT signature invalid");
                        return;
                    }
                }
                JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
                if (claims.getExpirationTime() != null && claims.getExpirationTime().before(new Date())) {
                    sendJsonError(response, 401, "JWT expired");
                    return;
                }
                String userId = claims.getSubject();
                if (StringUtils.hasText(userId)) {
                    request.setAttribute(USER_ID_ATTR, userId);
                }
                request.setAttribute("nomifun.auth.mode", "jwt");
                log.debug("[nomifun-auth] JWT ok user={} path={}", userId, request.getRequestURI());
            } catch (ParseException | JOSEException e) {
                sendJsonError(response, 401, "JWT invalid: " + e.getMessage());
                return;
            }
        } else {
            request.setAttribute("nomifun.auth.mode", "anonymous");
        }

        chain.doFilter(request, response);
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(Map.of("error", message, "status", status)));
    }
}

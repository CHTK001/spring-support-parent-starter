package com.chua.starter.oauth.client.support.filter;

import com.chua.common.support.network.ProtocolType;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import com.chua.common.support.network.server.filter.ServerFilterConfig;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.Protocol;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 独立 HTTP 服务器（非 Servlet 容器）的 OAuth 鉴权过滤器。
 *
 * <p>供服务管理等自行拉起的 {@code Server}（jdk/nio/aio）使用：挂在文件服务器等网关的
 * 过滤器链上，命中"私有桶/私有前缀"的请求校验 OAuth 令牌，其余请求放行。</p>
 *
 * <h3>与 MVC 侧的区别</h3>
 * <p>MVC 的 {@link AuthFilter} 依赖 {@code HttpServletRequest}（经 {@code AuthSessionUtils}
 * ThreadLocal 取当前请求），无法用于独立服务器；本过滤器直接从
 * {@link ServerRequest} 提取令牌，并复用同一套鉴权协议
 * {@link Protocol#approve(Cookie[], String, String)}，保证与 Web 侧判定一致
 * （成功条件：{@code Information.OK}，即状态码 200）。</p>
 *
 * <h3>启用条件</h3>
 * <ul>
 *   <li>{@code plugin.oauth.enable=true}（复用 OAuth 客户端配置，关闭时本过滤器整体放行）；</li>
 *   <li>且配置了至少一个私有桶（{@link #setPrivateBuckets(Set)}）。</li>
 * </ul>
 *
 * <h3>私有桶来源（优先级从高到低）</h3>
 * <ol>
 *   <li>节点 config JSON：{@code {"privateBuckets": "localFileStorage,minio"}}（服务管理节点启动时下发）；</li>
 *   <li>初始化参数：{@code privateBuckets=localFileStorage,minio}；</li>
 *   <li>环境配置：{@code plugin.oauth.file-storage.private-buckets=localFileStorage,minio}。</li>
 * </ol>
 * <p>{@code *} 表示全部桶私有；一次性链接路径 {@code /ot/**} 本身即凭证，始终放行。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class OAuthServerFilter implements ServerFilter {

    /**
     * 过滤器标识（注册中心/日志使用）
     */
    public static final String FILTER_ID = "oauth";

    /**
     * 默认令牌请求头名（与 {@link AuthClientProperties#getTokenName()} 默认值一致）
     */
    private static final String DEFAULT_TOKEN_NAME = "x-oauth-token";

    /**
     * 子协议请求头（可显式指定协议实现，缺省用客户端配置的协议）
     */
    private static final String SUB_PROTOCOL_HEADER = "x-oauth-protocol";

    /**
     * 私有桶环境配置键
     */
    private static final String PRIVATE_BUCKETS_PROPERTY = "plugin.oauth.file-storage.private-buckets";

    /**
     * Bearer 前缀
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 鉴权成功状态码
     */
    private static final int SUCCESS_CODE = 200;

    /**
     * 一次性链接路径段（本身即凭证，放行）
     */
    private static final String ONE_TIME_SEGMENT = "ot";

    /**
     * 私有桶集合（空 = 过滤器整体放行）
     */
    private volatile Set<String> privateBuckets = Set.of();

    /**
     * 显式启停开关（配合 {@code plugin.oauth.enable}）
     */
    private volatile boolean enabled = true;

    /**
     * 获取过滤器执行顺序：须早于文件存储网关（order=100）完成鉴权。
     *
     * @return 50
     */
    @Override
    public int getOrder() {
        return 50;
    }

    /**
     * 仅对 HTTP 协议生效。
     *
     * @return HTTP
     */
    @Override
    public ProtocolType[] supportProtocols() {
        return new ProtocolType[]{ProtocolType.HTTP};
    }

    /**
     * 是否启用：显式开启、存在私有桶、且 OAuth 客户端已启用（三者同时满足）。
     *
     * @return true 表示参与鉴权
     */
    @Override
    public boolean isEnabled() {
        AuthClientProperties properties = OauthClientRuntimeContext.getAuthClientProperties();
        return enabled
                && !privateBuckets.isEmpty()
                && properties != null
                && properties.isEnable();
    }

    /**
     * 初始化：读取初始化参数中的私有桶，缺省回退环境配置。
     *
     * @param config 过滤器配置
     */
    @Override
    public void init(ServerFilterConfig config) {
        String csv = config == null ? null : config.getInitParameter("privateBuckets");
        if (isBlank(csv)) {
            csv = environmentProperty(PRIVATE_BUCKETS_PROPERTY);
        }
        if (!isBlank(csv)) {
            setPrivateBuckets(parseCsv(csv));
        }
    }

    /**
     * 热更新配置：支持 {@code privateBuckets}（逗号分隔）与 {@code enabled}。
     *
     * @param config 新的参数键值
     */
    @Override
    public void updateConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        Object buckets = config.get("privateBuckets");
        if (buckets != null) {
            setPrivateBuckets(parseCsv(String.valueOf(buckets)));
        }
        Object enable = config.get("enabled");
        if (enable != null) {
            this.enabled = Boolean.parseBoolean(String.valueOf(enable).trim());
        }
    }

    /**
     * 执行鉴权：公共请求放行；私有桶请求校验令牌，失败返回 401。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param chain    过滤器链
     * @throws Exception 处理异常
     */
    @Override
    public void doFilter(ServerRequest request, ServerResponse response, ServerFilterChain chain) throws Exception {
        applyCorsHeaders(request, response);

        String bucket = resolveBucket(request.getPath());
        if (bucket == null || !isPrivate(bucket)) {
            chain.doFilter(request, response);
            return;
        }
        // 预检请求放行给后续过滤器（文件存储网关会回 204 + 允许方法/头）
        if (request.getMethod() == HttpMethod.OPTIONS) {
            chain.doFilter(request, response);
            return;
        }
        // 管理员配置请求放行：由 setConfig 自带的随机管理员令牌门禁，
        // 否则置私有后连"改回公共"都会被本过滤器拦死（锁死）
        if (isAdminConfigRequest(request)) {
            chain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (isBlank(token)) {
            reject(response, 401, "未登录：缺少访问令牌");
            return;
        }
        AuthenticationInformation information;
        try {
            information = authenticate(request, token);
        } catch (Exception e) {
            log.warn("[OAuthServerFilter] 鉴权异常: bucket={} - {}", bucket, e.getMessage());
            reject(response, 503, "鉴权服务不可用");
            return;
        }
        if (!isAuthenticated(information)) {
            reject(response, 401, failureMessage(information));
            return;
        }

        request.setAttribute("oauth.token", token);
        if (information.getReturnResult() != null) {
            request.setAttribute("oauth.user", information.getReturnResult());
        }
        chain.doFilter(request, response);
    }

    /**
     * 是否为管理员配置请求（{@code ?action=setConfig}，由随机管理员令牌门禁）。
     *
     * @param request 当前请求
     * @return true 表示放行
     */
    private boolean isAdminConfigRequest(ServerRequest request) {
        return request.getMethod() == HttpMethod.POST
                && "setConfig".equals(request.getParam("action"));
    }

    /**
     * 设置私有桶集合。
     *
     * @param privateBuckets 桶名集合（空表示全部放行）
     */
    public void setPrivateBuckets(Set<String> privateBuckets) {
        this.privateBuckets = privateBuckets == null ? Set.of() : Set.copyOf(privateBuckets);
    }

    /**
     * 获取私有桶集合。
     *
     * @return 桶名集合（不可变）
     */
    public Set<String> getPrivateBuckets() {
        return privateBuckets;
    }

    /**
     * 设置显式启停开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 调用鉴权协议校验令牌（与 Web 侧 {@code WebRequest} 使用同一协议实现）。
     *
     * @param request 当前请求（提取 Cookie 与子协议）
     * @param token   令牌
     * @return 认证信息，协议未注册时返回 空
     */
    private AuthenticationInformation authenticate(ServerRequest request, String token) {
        Protocol protocol = resolveProtocol();
        if (protocol == null) {
            log.error("[OAuthServerFilter] 鉴权协议未注册，可用协议: {}", ServiceProvider.of(Protocol.class).names());
            return null;
        }
        return protocol.approve(resolveCookies(request), token, request.getHeader(SUB_PROTOCOL_HEADER));
    }

    /**
     * 解析鉴权协议实现：客户端配置协议优先，回退默认扩展。
     *
     * @return 协议实现，均未注册时返回 空
     */
    private Protocol resolveProtocol() {
        AuthClientProperties properties = OauthClientRuntimeContext.getAuthClientProperties();
        String protocol = properties == null || isBlank(properties.getProtocol())
                ? "tcp" : properties.getProtocol();
        Protocol extension = ServiceProvider.of(Protocol.class).getExtension(protocol);
        if (extension == null) {
            extension = ServiceProvider.of(Protocol.class).getExtension("default");
        }
        return extension;
    }

    /**
     * 从请求中提取令牌：自定义头 → Authorization Bearer → 查询参数 → Cookie。
     *
     * @param request 当前请求
     * @return 令牌，未携带返回 空
     */
    private String resolveToken(ServerRequest request) {
        String tokenName = resolveTokenName();
        String token = request.getHeader(tokenName);
        if (!isBlank(token)) {
            return token;
        }
        String authorization = request.getHeader("Authorization");
        if (!isBlank(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        String param = request.getParam(tokenName);
        if (!isBlank(param)) {
            return param;
        }
        for (Cookie cookie : resolveCookies(request)) {
            if (tokenName.equals(cookie.getName()) && !isBlank(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 解析令牌请求头名（跟随 OAuth 客户端配置）。
     *
     * @return 请求头名
     */
    private String resolveTokenName() {
        AuthClientProperties properties = OauthClientRuntimeContext.getAuthClientProperties();
        String tokenName = properties == null ? null : properties.getTokenName();
        return isBlank(tokenName) ? DEFAULT_TOKEN_NAME : tokenName;
    }

    /**
     * 解析请求 Cookie 头为 Servlet Cookie 数组（供鉴权协议识别 {@code x-oauth-cookie}）。
     *
     * @param request 当前请求
     * @return Cookie 数组（不可变场景下可为空数组）
     */
    private Cookie[] resolveCookies(ServerRequest request) {
        String header = request.getHeader("Cookie");
        if (isBlank(header)) {
            return new Cookie[0];
        }
        Set<Cookie> cookies = new LinkedHashSet<>();
        for (String pair : header.split(";")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String name = pair.substring(0, index).trim();
            String value = pair.substring(index + 1).trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                cookies.add(new Cookie(name, value));
            } catch (IllegalArgumentException ignored) {
                // Cookie 名非法（含特殊字符）时跳过，令牌仍可取请求头/参数
            }
        }
        return cookies.toArray(new Cookie[0]);
    }

    /**
     * 提取路径中的桶名（第一段），一次性链接路径返回 空 表示放行。
     *
     * @param path 请求路径
     * @return 桶名，无有效段或为一次性链接时返回 空
     */
    private String resolveBucket(String path) {
        if (isBlank(path)) {
            return null;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        int index = normalized.indexOf('/');
        String segment = index < 0 ? normalized : normalized.substring(0, index);
        if (segment.isEmpty() || ONE_TIME_SEGMENT.equalsIgnoreCase(segment)) {
            return null;
        }
        return segment;
    }

    /**
     * 判断桶是否私有（支持 {@code *} 通配全部桶）。
     *
     * @param bucket 桶名
     * @return true 表示需要鉴权
     */
    private boolean isPrivate(String bucket) {
        Set<String> buckets = privateBuckets;
        return buckets.contains("*") || buckets.contains(bucket);
    }

    /**
     * 判断鉴权结果是否成功（与 {@code WebRequest} 判定一致：状态码 200）。
     *
     * @param information 认证信息
     * @return true 表示通过
     */
    private boolean isAuthenticated(AuthenticationInformation information) {
        return information != null
                && information.getInformation() != null
                && information.getInformation().getCode() == SUCCESS_CODE;
    }

    /**
     * 取鉴权失败原因文案。
     *
     * @param information 认证信息
     * @return 可读文案
     */
    private String failureMessage(AuthenticationInformation information) {
        if (information == null || information.getInformation() == null) {
            return "认证失败：鉴权服务未返回结果";
        }
        String message = information.getInformation().getMessage();
        return isBlank(message) ? "认证失败或令牌已过期" : message;
    }

    /**
     * 跨域头：401/错误响应也须带 CORS 头，否则浏览器读不到失败原因（独立部署必需）。
     *
     * @param request  当前请求
     * @param response 当前响应
     */
    private void applyCorsHeaders(ServerRequest request, ServerResponse response) {
        String origin = request.getHeader("Origin");
        if (isBlank(origin)) {
            return;
        }
        response.setHeader("Access-Control-Allow-Origin", origin);
        // X-FileStorage-ImageOp / -Reason 是「图片处理是否真的执行」的信号：
        // 超像素预算或转码失败时服务端会兜底吐原图，浏览器必须能读到这两个头
        // 才能提示用户，否则会静默拿到未处理的图片。
        response.setHeader("Access-Control-Expose-Headers",
                "Content-Disposition, X-FileStorage-Preview, Content-Range, Accept-Ranges, X-FileStorage-ImageOp, X-FileStorage-ImageOp-Reason");
        response.setHeader("Vary", "Origin");
    }

    /**
     * 输出鉴权失败响应并终止过滤器链。
     *
     * @param response 当前响应
     * @param status   HTTP 状态码
     * @param message  失败文案
     */
    private void reject(ServerResponse response, int status, String message) {
        String body = "{\"code\":\"" + status + "01\",\"msg\":\"" + escape(message) + "\",\"success\":false,\"ok\":false}";
        response.setStatus(status)
                .setContentType("application/json;charset=UTF-8")
                .setBody(body.getBytes(StandardCharsets.UTF_8))
                .end();
    }

    /**
     * 转义 JSON 字符串中的引号与反斜杠。
     *
     * @param text 原始文案
     * @return 转义后的文案
     */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 逗号分隔配置转集合（去空白、去空项、保持顺序）。
     *
     * @param csv 逗号分隔文本
     * @return 集合
     */
    private Set<String> parseCsv(String csv) {
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .forEach(values::add);
        return values;
    }

    /**
     * 读取环境配置（运行时上下文未就绪时返回 空）。
     *
     * @param key 配置键
     * @return 配置值
     */
    private String environmentProperty(String key) {
        return OauthClientRuntimeContext.getEnvironment() == null
                ? null : OauthClientRuntimeContext.getEnvironment().getProperty(key);
    }

    /**
     * 空串判断。
     *
     * @param value 待判断字符串
     * @return 是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

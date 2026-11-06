// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
import com.chua.starter.oauth.client.support.web.WebRequest;
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
       * 跨域来源白名单环境配置键（逗号分隔，支持 {@code *} 表示全部放行）
       *
       * <p><b>存在的理由（修复 CORS 缺陷）</b>：本过滤器原先无条件把请求的
       * {@code Origin} 回显到 {@code Access-Control-Allow-Origin}。虽然此处未同时下发
       * {@code Access-Control-Allow-Credentials}，浏览器因此不会把带凭据的响应交给
       * 恶意页面，但「任意站点都能拿到本服务的跨域响应」本身仍是缺陷：</p>
       * <ul>
       *   <li>一旦后续任何一处补上 {@code Allow-Credentials: true}（这是极常见的
       *       「修前端跨域报错」式改动），立刻升级为可读取用户私有桶文件内容的漏洞；</li>
       *   <li>错误响应体（鉴权失败原因）可被任意站点读取，便于探测；</li>
       *   <li>触发浏览器对不同源发起的预检/实际请求，可用于放大与压测。</li>
       * </ul>
       * <p>现改为白名单：命中才回显，未命中则<b>不下发任何跨域头</b>（而非回显 + 阻断），
       * 让浏览器按标准自己拒绝，行为可预期。配置 {@code *} 可保留旧的宽松行为，
       * 但仅建议用于本地联调。</p>
       */
    private static final String CORS_ALLOWED_ORIGINS_PROPERTY = "plugin.oauth.file-storage.cors-allowed-origins";

      /**
       * 跨域来源白名单（支持 {@code *} 通配）。
       *
       * <p>空集合 = 不允许任何跨域来源（fail-closed）。</p>
       */
    private volatile Set<String> corsAllowedOrigins = Set.of();

      /**
       * Bearer 前缀
       */
    private static final String BEARER_PREFIX = "Bearer ";

      /**
       * 鉴权成功状态码
       */
    private static final int SUCCESS_CODE = 200;

      /**
       * 一次性链接路径段。
       *
       * <p>「消费」（{@code GET /ot/{token}}）免鉴权：令牌本身即凭证。
       * 「签发」（{@code POST /ot}）不豁免：见 {@link #isOneTimeMint}。</p>
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
       * 获取过滤器执行顺序：必须早于所有服务管理插件。
       *
       * <p>文件存储网关自身 order=100，但服务管理描述会用
       * {@code DelegatingServerFilter} 把执行顺序覆盖为节点里的 filter order
       * （默认 0），因此固定 50 会在反应式链中排到文件存储之后，私有桶请求
       * 尚未鉴权就被网关 200 处理。鉴权属于安全门，取接近最小值的最高优先级。</p>
       *
       * @return 最高优先级
       */
    @Override
    public int getOrder() {
        return Integer.MIN_VALUE + 1;
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
          // <b>修复</b>：跨域来源此前无条件回显 Origin，现改为白名单。
          // 未配置时 fail-closed —— 不下发任何跨域头。
        String origins = config == null ? null : config.getInitParameter("corsAllowedOrigins");
        if (isBlank(origins)) {
            origins = environmentProperty(CORS_ALLOWED_ORIGINS_PROPERTY);
        }
        if (!isBlank(origins)) {
            setCorsAllowedOrigins(parseCsv(origins));
        }
        if (corsAllowedOrigins.isEmpty()) {
            log.warn("[OAuthServerFilter] 未配置 {}，跨域请求将不返回任何 CORS 头。"
                      + "若前端需要跨域访问文件存储，请配置为逗号分隔的来源列表（本地联调可用 *）。",
                    CORS_ALLOWED_ORIGINS_PROPERTY);
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
        Object origins = config.get("corsAllowedOrigins");
        if (origins != null) {
            setCorsAllowedOrigins(parseCsv(String.valueOf(origins)));
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
        if (bucket == null && isOneTimeMint(request)) {
              // 签发一次性链接的目标桶在查询参数里，不在路径里。放行整段 /ot
              // 等于把私有桶的「读」通道开给了未认证方：任意人 POST /ot?bucket=<私有桶>&key=<已知 key>
              // 就能换到一条免鉴权的 /ot/{token} 直链。因此签发必须按目标桶鉴权，
              // 只保留「消费」（GET /ot/{token}）免鉴权 —— 那时令牌本身已是凭证，
              // 且只有鉴权通过的人才签得出令牌。
            String mintBucket = request.getParam("bucket");
            if (isBlank(mintBucket)) {
                reject(response, 400, "一次性链接缺少 bucket 参数");
                return;
            }
            if (!isPrivate(mintBucket)) {
                chain.doFilter(request, response);
                return;
            }
            bucket = mintBucket;
        }
          // 链接管理（GET /ot 列举、DELETE /ot/{token} 吊销）既不是签发也不是消费：
          // 列举会返回文件名与对象键，吊销是写操作。缺了这条，任何未认证方
          // GET /ot 就能枚举全部分享链接（文件名/桶/剩余时间），DELETE 还能把别人的链接关掉。
          // 只有在配置了私有桶（即本文件服务器需要鉴权）时才要求令牌，保持纯公共桶部署可用。
        boolean linkAdmin = bucket == null && isOneTimeLinkAdmin(request);
        boolean needsAuth = bucket != null && isPrivate(bucket);
        if (!needsAuth && !(linkAdmin && !privateBuckets.isEmpty())) {
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
       * 是否为一次性链接的「签发」请求（{@code POST /ot?bucket=xxx&key=yyy}）。
       *
       * <p>「消费」（{@code GET /ot/{token}}）不算 —— 它带令牌段，路径首段仍是 {@code ot}，
       * 但方法为 GET，由调用方用方法区分。</p>
       *
       * @param request 当前请求
       * @return true 表示这是签发请求
       */
    private boolean isOneTimeMint(ServerRequest request) {
        if (request.getMethod() != HttpMethod.POST) {
            return false;
        }
        String path = request.getPath();
        if (isBlank(path)) {
            return false;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        int index = normalized.indexOf('/');
        String segment = index < 0 ? normalized : normalized.substring(0, index);
        return ONE_TIME_SEGMENT.equalsIgnoreCase(segment);
    }

      /**
       * 是否为一次性链接的「管理」请求（列举或吊销）。
       *
       * <p>与签发、消费区分开：</p>
       * <ul>
       *   <li>{@code GET /ot} — 列举分享链接（返回文件名、对象键、剩余时间）</li>
       *   <li>{@code DELETE /ot/{token}} — 吊销指定链接</li>
       * </ul>
       * <p>{@code GET /ot/{token}}（消费）<b>不</b>属于管理请求：令牌本身就是凭证，
       * 拿到链接的匿名用户必须能打开，否则分享功能失效。</p>
       *
       * @param request 当前请求
       * @return true 表示这是管理请求
       */
    private boolean isOneTimeLinkAdmin(ServerRequest request) {
        String path = request.getPath();
        if (isBlank(path)) {
            return false;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        int index = normalized.indexOf('/');
        String segment = index < 0 ? normalized : normalized.substring(0, index);
        if (!ONE_TIME_SEGMENT.equalsIgnoreCase(segment)) {
            return false;
        }
        if (request.getMethod() == HttpMethod.DELETE) {
            return true;
        }
        if (request.getMethod() != HttpMethod.GET) {
            return false;
        }
        // GET /ot（含尾斜杠）= 列举；GET /ot/{token} = 消费，必须免鉴权
        String rest = index < 0 ? "" : normalized.substring(index + 1);
        return rest.isEmpty();
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
       * 解析鉴权协议实现（与 Web 侧 {@link WebRequest} 同一解析路径与实例缓存）。
       *
       * <p>协议扩展的构造器需要配置实例（如 {@code StaticProtocol(AuthClientProperties)}），
       * 无参 {@link ServiceProvider#getExtension(String)} 永远构造不出来，会把一切请求
       * 判成「鉴权协议未注册」401（文件服务器网关私有桶全挂即此因）。
       * 统一走 {@link WebRequest#getProtocolExtension}：按协议名+关键配置缓存单例，
       * 构造时携带 {@link AuthClientProperties}。</p>
       *
       * @return 协议实现，均未注册时返回 空
       */
    private Protocol resolveProtocol() {
        return WebRequest.getProtocolExtension(OauthClientRuntimeContext.getAuthClientProperties());
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
        if (isBlank(origin) || !isOriginAllowed(origin)) {
              // 未命中白名单：一个跨域头都不下发，由浏览器按标准拒绝。
              // 比「回显 + 别的手段阻断」更可预期，也避免被误读为「已放行」。
            return;
        }
        response.setHeader("Access-Control-Allow-Origin", origin);
          // X-FileStorage-ImageOp / -Reason 是「图片处理是否真的执行」的信号：
          // 超像素预算或转码失败时服务端会兜底吐原图，浏览器必须能读到这两个头
          // 才能提示用户，否则会静默拿到未处理的图片。
          // X-FileStorage-Convert 同理：?format=pdf/docx/xlsx 转换失败时服务端仍返回
          // 200 + 原始字节并带 Convert: unavailable，前端读不到该头会把 Office 二进制
          // 当成转换结果去解析。X-FileStorage-Client-Renderer 是客户端渲染器标识。
        response.setHeader("Access-Control-Expose-Headers",
                "Content-Disposition, X-FileStorage-Preview, Content-Range, Accept-Ranges, X-FileStorage-ImageOp, X-FileStorage-ImageOp"
                        + "-Reason, X-FileStorage-Convert, X-FileStorage-Client-Renderer");
        response.setHeader("Vary", "Origin");
    }

      /**
       * 判断来源是否在跨域白名单内。
       *
       * <p>比对前做归一化：去尾斜杠、scheme/host 小写。Origin 头由浏览器生成、
       * 不会含路径或大小写抖动，但配置里手写时常带上尾斜杠或大写 scheme，
       * 不归一化会导致「配了却不生效」这类难排查的问题。</p>
       *
       * @param origin 来源
       * @return 允许返回 true
       */
    boolean isOriginAllowed(String origin) {
        Set<String> allowed = this.corsAllowedOrigins;
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        String normalized = normalizeOrigin(origin);
        for (String candidate : allowed) {
            if ("*".equals(candidate) || normalized.equals(normalizeOrigin(candidate))) {
                return true;
            }
        }
        return false;
    }

      /**
       * 归一化 Origin：去尾斜杠并转小写。
       *
       * @param origin 原始值
       * @return 归一化结果
       */
    private String normalizeOrigin(String origin) {
        if (origin == null) {
            return "";
        }
        String value = origin.trim().toLowerCase();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

      /**
       * 设置跨域来源白名单。
       *
       * @param origins 来源集合
       */
    public void setCorsAllowedOrigins(Set<String> origins) {
        this.corsAllowedOrigins = (origins == null || origins.isEmpty())
                ? Set.of()
                : new LinkedHashSet<>(parseCsv(String.join(",", origins)));
    }

      /**
       * 读取当前跨域来源白名单。
       *
       * @return 集合
       */
    public Set<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
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
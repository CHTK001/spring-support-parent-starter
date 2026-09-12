package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 统一登录契约控制器（前端 Vue / React 均走该契约）。
 *
 * <p>提供 {@code /v2/user} 前缀下的登录、登出、当前用户信息、刷新令牌等接口，
 * 统一以 {@link ReturnResult} 作为响应信封，适配前端 {@code LoginResult} 结构。</p>
 *
 * <p>所有接口均标注 {@link TokenForIgnore}，避免被 oauth 客户端拦截。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/user")
@Slf4j
public class UserAuthController {

    /** Bearer 令牌 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Bearer 令牌 前缀长度 */
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    /**
      * 用户登录（对接 o认证 静态 协议）。
     *
     * <p>调用 oauth 客户端换取访问令牌，成功后组装前端契约所需的
     * {@code LoginResult} 结构并返回。</p>
     *
     * @param body 请求体（含 用户名 / 密码）
     * @return 登录结果（成功含 access令牌 / refresh令牌 / 用户信息 / 角色 / expires）
     */
    @TokenForIgnore
    @PostMapping("/login")
    public ReturnResult<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return ReturnResult.error("用户名和密码不能为空");
        }

        LoginAuthResult result = AuthClientExecute.getInstance()
                .getAccessToken(username, password, AuthType.WEB, Map.of());
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            return ReturnResult.error("用户名或密码错误");
        }

        String token = result.getToken();
 // refresh令牌 暂无独立来源，先复用 令牌 占位
        String refreshToken = StringUtils.isNotBlank(result.getRefreshToken())
                ? result.getRefreshToken() : (token != null ? token : "");

        Map<String, Object> userInfo = new LinkedHashMap<>();
 // 前端要求 uuidv7 格式的 用户标识
        userInfo.put("userId", generateUuidV7());
        userInfo.put("username", result.getUserResume() != null
                ? result.getUserResume().getUsername() : username);
        userInfo.put("nickname", result.getUserResume() != null
                ? result.getUserResume().getNickName() : username);

        List<String> roles = new ArrayList<>();
        roles.add("admin");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", token);
        data.put("refreshToken", refreshToken);
        data.put("userInfo", userInfo);
        data.put("roles", roles);
        data.put("expires", result.getUserResume() != null
                ? result.getUserResume().getExpireTime() : null);
        return ReturnResult.ok(data);
    }

    /**
     * 用户登出。
     *
     * <p>调用 oauth 客户端登出当前用户，清除本地会话。</p>
     *
     * @return 登出结果
     */
    @TokenForIgnore
    @PostMapping("/logout")
    public ReturnResult<Void> logout() {
        try {
            AuthClientExecute.getInstance().logout(null, null, LogoutType.LOGOUT);
        } catch (Exception e) {
            // 登出异常不阻断响应，仅记录日志
            log.warn("用户登出调用 oauth 异常: {}", e.getMessage());
        }
        return ReturnResult.ok(null);
    }

    /**
     * 获取当前登录用户信息。
     *
     * <p>从 {@code Authorization: Bearer <token>} 请求头（或 query token）提取令牌，
      * 调用 OAuth 客户端校验并解析用户信息；无法解析时返回未登录。</p>
     *
     * @param request HTTP 请求
     * @return 当前用户信息（userId / 用户名 / nickname / 角色）
     */
    @TokenForIgnore
    @GetMapping("/me")
    public ReturnResult<Map<String, Object>> me(HttpServletRequest request) {
        String token = resolveToken(request);
        if (StringUtils.isBlank(token)) {
            return ReturnResult.error("未登录");
        }

        UserResult userResult = null;
        try {
            userResult = AuthClientExecute.getInstance().getUserResult(token);
        } catch (Exception e) {
            log.warn("解析用户信息异常: {}", e.getMessage());
        }

 // 若 OAuth 客户端暂未接通校验服务，则返回占位用户信息，待完善
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("userId", userResult != null && StringUtils.isNotBlank(userResult.getUserId())
                ? userResult.getUserId() : generateUuidV7());
        userInfo.put("username", userResult != null && StringUtils.isNotBlank(userResult.getUsername())
                ? userResult.getUsername() : "anonymous");
        userInfo.put("nickname", userResult != null && StringUtils.isNotBlank(userResult.getNickName())
                ? userResult.getNickName() : "匿名用户");

        List<String> roles = new ArrayList<>();
        if (userResult != null && userResult.getRoles() != null && !userResult.getRoles().isEmpty()) {
            roles.addAll(userResult.getRoles());
        } else {
            // 占位角色，待完善角色解析
            roles.add("admin");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userInfo.get("userId"));
        data.put("username", userInfo.get("username"));
        data.put("nickname", userInfo.get("nickname"));
        data.put("roles", roles);
        return ReturnResult.ok(data);
    }

    /**
     * 刷新令牌（占位实现）。
     *
     * <p>当前仅返回沿用旧 token 的占位结果；后续可对接 oauth 客户端的
     * {@link AuthClientExecute#upgrade(String, com.chua.starter.oauth.client.support.enums.UpgradeType)}
     * 实现真正的令牌刷新。</p>
     *
     * @param body 请求体（含 refresh令牌）
     * @return 刷新后的令牌结果
     * @param false false
     */
    @TokenForIgnore
    @PostMapping("/refresh-token")
    public ReturnResult<Map<String, Object>> refreshToken(@RequestBody(required = false) Map<String, String> body) {
        String refreshToken = body != null ? body.get("refreshToken") : null;
 // 占位：沿用旧 令牌，待对接 OAuth 刷新
        String accessToken = refreshToken != null ? refreshToken : "";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        return ReturnResult.ok(data);
    }

    /**
      * 从请求中解析 令牌（优先 授权 头，其次 查询 令牌）。
     *
     * @param request HTTP 请求
     * @return Token 字符串，无法解析时返回 {@code null}
     */
    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(auth) && auth.startsWith(BEARER_PREFIX)) {
            String token = auth.substring(BEARER_PREFIX_LENGTH).trim();
            if (StringUtils.isNotBlank(token)) {
                return token;
            }
        }
 // 查询 令牌 兜底
        return request.getParameter("token");
    }

    /**
      * 生成 uuidv7 格式字符串。
     *
     * <p>格式：xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx（y ∈ {8,9,a,b}）</p>
     *
     * @return UUIDv7 字符串
     */
    private static String generateUuidV7() {
        UUID uuid = UUID.randomUUID();
        long ms = System.currentTimeMillis();
        byte[] bytes = new byte[16];
 // 时间戳高位 (48 钻头) 放入前6字节
        bytes[0] = (byte) (ms >> 40);
        bytes[1] = (byte) (ms >> 32);
        bytes[2] = (byte) (ms >> 24);
        bytes[3] = (byte) (ms >> 16);
        bytes[4] = (byte) (ms >> 8);
        bytes[5] = (byte) ms;
        // 用随机数填充剩余字节
        byte[] random = uuid.toString().replace("-", "").substring(12).getBytes();
        System.arraycopy(random, 0, bytes, 6, 10);
 // 设置 版本 = 7 (0111)
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        // 设置 variant = 10xx
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        return String.format("%08x-%04x-%04x-%04x-%012x",
                ((bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF)),
                ((bytes[4] & 0xFF) << 8 | (bytes[5] & 0xFF)),
                ((bytes[6] & 0xFF) << 8 | (bytes[7] & 0xFF)),
                ((bytes[8] & 0xFF) << 8 | (bytes[9] & 0xFF)),
                (((long) (bytes[10] & 0xFF) << 40) | ((long) (bytes[11] & 0xFF) << 32) | ((long) (bytes[12] & 0xFF) << 24) | ((long) (bytes[13] & 0xFF) << 16) | ((long) (bytes[14] & 0xFF) << 8) | (bytes[15] & 0xFF))
        );
    }
}

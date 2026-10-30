package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.manage.store.SysAiCompanionStore;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 系统 / 设置 / webui 凭据 / 鉴权 / 计算机权限 控制器（对接 nomicore 契约）。
 *
 * <p>替代 Rust 后端的 system / settings / webui / auth / computer 命名空间，
 * 向前端 pages/AI（React+Tauri 桌面应用）提供纯 Spring 实现的 /api/* 接口。
 * 客户端设置与伙伴访问令牌均持久化，未实现的宿主能力明确返回 501。</p>
 *
 * <p>隔离口径分三类：出厂重置、WebUI/登录账号的改密改用户名、移动端配对令牌与计算机权限面板都是
 * 主机级动作，仅管理员可用；客户端偏好（通知、主题、字号等）是每个登录用户自己的一份，
 * 因此按用户分键存取，互不覆盖；伙伴远程访问令牌随伙伴归属走——本人可签发与吊销，
 * 管理员可读他人是否已配置但不代他人签发。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiSystemController {

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * Spring 环境（用于读取 活跃 配置文件）
     */
    private final Environment environment;

    /**
     * 伙伴 仓储（远程访问令牌随伙伴归属校验）
     */
    private final SysAiCompanionStore companionStore;

    /**
     * AI 全局配置服务
     */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * AI 用户配置服务
     */
    private final SysAiUserSettingService userSettingService;

    private static final String CLIENT_SETTING_GROUP = "client";
    private static final String SYSTEM_SETTING_GROUP = "system";
    private static final String STORAGE_GENERATION = createStorageGeneration();

    private static final Map<String, Object> CLIENT_SETTING_DEFAULTS = Map.ofEntries(
            Map.entry("notificationEnabled", false),
            Map.entry("cronNotificationEnabled", false),
            Map.entry("keepAwake", false),
            Map.entry("saveUploadToWorkspace", true),
            Map.entry("autoPreviewOfficeFiles", true),
            Map.entry("customTheme", "system"),
            Map.entry("customAccent", "blue"),
            Map.entry("custFontSize", 14),
            Map.entry("custSidebarCollapsed", false),
            Map.entry("custTerminalTheme", "system"),
            Map.entry("custChatWidth", 720),
            Map.entry("language", "zh-CN")
    );

    private static final Map<String, String> CLIENT_SETTING_TYPES = Map.ofEntries(
            Map.entry("notificationEnabled", "BOOLEAN"),
            Map.entry("cronNotificationEnabled", "BOOLEAN"),
            Map.entry("keepAwake", "BOOLEAN"),
            Map.entry("saveUploadToWorkspace", "BOOLEAN"),
            Map.entry("autoPreviewOfficeFiles", "BOOLEAN"),
            Map.entry("customTheme", "STRING"),
            Map.entry("customAccent", "STRING"),
            Map.entry("custFontSize", "NUMBER"),
            Map.entry("custSidebarCollapsed", "BOOLEAN"),
            Map.entry("custTerminalTheme", "STRING"),
            Map.entry("custChatWidth", "NUMBER"),
            Map.entry("language", "STRING")
    );

    // -----------------------------------------------------------------------
 // 系统 命名空间
    // -----------------------------------------------------------------------

    /**
     * 返回运行时真实系统信息。
     *
     * @return 系统信息（缓存目录 / 工作目录 / 日志目录 / 存储代际 / 平台 / 架构）
     */
    @TokenForIgnore
    @GetMapping("/system/info")
    public ApiResult<Map<String, Object>> systemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("cache_dir", resolveCacheDir());
        info.put("work_dir", resolveSetting("work_dir", System.getProperty("user.dir")));
        info.put("log_dir", resolveLogDir());
        info.put("storage_generation", resolveStorageGeneration());
        info.put("platform", detectPlatform());
        info.put("arch", System.getProperty("os.arch"));
        info.put("profile", resolveActiveProfile());
        return ApiResult.ok(info);
    }

    /**
     * 触发出厂重置（当前宿主未提供安全擦除编排）。
    /**
     * 触发出厂重置（当前宿主未提供安全擦除编排）。
     * @return 返回值
    */
    public ApiResult<Void> factoryReset() {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前宿主未实现出厂重置");
    }

    /**
     * 持久化用户选择的目录配置。
     * 角色校验由 @权限 注解经 OAuth 权限拦截器 处理。
     * dev 配置文件 校验在此处直接执行。
     *
     * @param body 请求体（含 work_dir / 缓存_dir / 日志_dir）
     * @return 成功信封
     */
    @Permission(role = {"admin", "superadmin"})
    @PostMapping("/system/work-dir")
    public ApiResult<Void> workDir(@RequestBody Map<String, Object> body) {
 // 仅 dev 配置文件 允许修改系统目录
        String[] profiles = environment.getActiveProfiles();
        boolean isDev = java.util.Arrays.asList(profiles).contains("dev");
        if (!isDev) {
            log.warn("拒绝非 dev profile 修改系统目录: profiles={}", (Object) profiles);
            throw new ApiException(ApiErrorCode.FORBIDDEN, "系统目录仅允许在 dev 模式下修改");
        }
        Object workDir = body.get("work_dir");
        Object cacheDir = body.get("cache_dir");
        Object logDir = body.get("log_dir");
        if ((workDir == null || workDir.toString().isBlank())
                && (cacheDir == null || cacheDir.toString().isBlank())
                && (logDir == null || logDir.toString().isBlank())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "至少需要提供一个目录字段");
        }
        persistDirectorySetting("work_dir", workDir);
        persistDirectorySetting("cache_dir", cacheDir);
        persistDirectorySetting("log_dir", logDir);
        log.info("系统目录配置已持久化 work_dir={} cache_dir={} log_dir={}", workDir, cacheDir, logDir);
        return ApiResult.ok();
    }

    // -----------------------------------------------------------------------
    // Settings 命名空间
    // -----------------------------------------------------------------------

    /**
     * 读取客户端设置，未配置时返回类型化默认值。
     *
     * @param key 设置键；为空时返回全部设置
     * @return 设置值
     */
    @GetMapping("/settings/client")
    public ApiResult<Object> getClientSetting(@RequestParam(required = false) String key) {
        Map<String, Object> settings = resolveClientSettings();
        if (key == null || key.isBlank()) {
            return ApiResult.ok(settings);
        }
        if (!settings.containsKey(key)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的客户端设置: " + key);
        }
        return ApiResult.ok(settings.get(key));
    }

    /**
     * 更新客户端设置。
     *
     * @param body 设置键值对
     * @return 成功信封
     */
    @PutMapping("/settings/client")
    public ApiResult<Void> putClientSetting(@RequestBody Map<String, Object> body) {
        String userId = AiUserContext.requireUserId();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            String type = CLIENT_SETTING_TYPES.get(key);
            if (type == null) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的客户端设置: " + key);
            }
            String value = serializeClientSetting(key, type, entry.getValue());
            userSettingService.upsertValue(userId, CLIENT_SETTING_GROUP, key, value, type, null);
        }
        return ApiResult.ok();
    }

    /**
     * 变更界面语言并推送 WS 事件。
     *
     * @param body 请求体（含 language）
     * @return 成功信封
     */
    @PatchMapping("/settings")
    public ApiResult<Void> patchSettings(@RequestBody Map<String, Object> body) {
        Object language = body.get("language");
        if (language == null || language.toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "language 不能为空");
        }
        userSettingService.upsertValue(AiUserContext.requireUserId(), CLIENT_SETTING_GROUP,
                "language", language.toString(), "STRING", null);
        pushService.push("system-settings:language-changed", Map.of("language", language.toString()));
        return ApiResult.ok();
    }

    // -----------------------------------------------------------------------
 // webui 凭据命名空间（宿主凭据，全部出口在登录态之后，写口仅管理员）
    // -----------------------------------------------------------------------

    /**
     * 修改 webui 管理员密码（当前 OAuth 客户端无对应能力时返回 501）。
     * <p>改动的是宿主 WebUI 管理员凭据，影响整台主机的登录入口，因此仅管理员可用。</p>
     *
     * @param body 请求体（含 新_密码）
     * @return 成功信封
     */
    @Permission("admin")
    @PostMapping("/webui/change-password")
    public ApiResult<Void> webuiChangePassword(@RequestBody Map<String, Object> body) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前 OAuth 客户端未提供宿主凭据更新接口");
    }

    /**
     * 修改 webui 管理员用户名。
     * <p>改动的是宿主 WebUI 管理员账号，仅管理员可用。</p>
     *
     * @param body 请求体（含 新_用户名）
     * @return 新用户名
     */
    @Permission("admin")
    @PostMapping("/webui/change-username")
    public ApiResult<Map<String, Object>> webuiChangeUsername(@RequestBody Map<String, Object> body) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前 OAuth 客户端未提供宿主账号更新接口");
    }

    /**
     * 生成用于移动端配对的二维码令牌。
     * <p>配对后另一台设备即可接入宿主，属主机级动作，因此仅管理员可生成。</p>
     *
     * @return 令牌与过期时间戳
     */
    @Permission("admin")
    @PostMapping("/webui/generate-qr-token")
    public ApiResult<Map<String, Object>> generateQrToken() {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前宿主未实现移动端配对令牌链路");
    }

    /**
     * 查询某伙伴的远程访问令牌是否已配置。
     *
     * @param companionId 伙伴 标识
     * @return 是否已配置
     */
    @GetMapping("/webui/companions/{companionId}/access-token")
    public ApiResult<Map<String, Object>> getCompanionAccessToken(@PathVariable String companionId) {
        companionStore.getCompanion(companionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", companionStore.getConfigValue(companionAccessTokenKey(companionId)) != null);
        return ApiResult.ok(result);
    }

    /**
     * 为某伙伴生成远程访问令牌（仅返回明文一次）。
     * <p>令牌可远程接入该伙伴，因此按伙伴归属放行：仅归属者能签发，管理员对他人伙伴只读。</p>
     *
     * @param companionId 伙伴 标识
     * @return 令牌 / 伙伴 标识 / 可选告警
     */
    @PostMapping("/webui/companions/{companionId}/access-token")
    public ApiResult<Map<String, Object>> mintCompanionAccessToken(@PathVariable String companionId) {
        companionStore.requireWritableCompanion(companionId);
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        companionStore.setConfigValue(companionAccessTokenKey(companionId), sha256(token));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("companion_id", companionId);
        return ApiResult.ok(result);
    }

    /**
     * 吊销某伙伴的远程访问令牌。
     * <p>吊销会让归属者已配对的远程接入立即失效，同样按伙伴归属放行。</p>
     *
     * @param companionId 伙伴 标识
     * @return 是否已配置（吊销后恒为 false）
     */
    @DeleteMapping("/webui/companions/{companionId}/access-token")
    public ApiResult<Map<String, Object>> revokeCompanionAccessToken(@PathVariable String companionId) {
        companionStore.requireWritableCompanion(companionId);
        companionStore.setConfigValue(companionAccessTokenKey(companionId), null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", false);
        return ApiResult.ok(result);
    }

    // -----------------------------------------------------------------------
 // 认证 命名空间（webui 浏览器会话自助改密）
    // -----------------------------------------------------------------------

    /**
     * 返回当前认证状态（基于真实会话身份）。
    /**
     * 返回当前认证状态（基于真实会话身份）。
     * @param request 参数
     * @return 认证状态
    */
    public ApiResult<Map<String, Object>> authStatus(HttpServletRequest request) {
        boolean authenticated = AiUserContext.currentUser() != null;
        if (!authenticated) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring(7).trim();
                if (!token.isBlank()) {
                    try {
                        UserResult user = AuthClientExecute.getInstance().getUserResult(token);
                        authenticated = user != null && user.getUserId() != null;
                    } catch (Exception e) {
                        log.debug("认证状态解析失败: {}", e.getMessage());
                    }
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", authenticated);
        result.put("needs_setup", false);
        return ApiResult.ok(result);
    }

    /**
     * 返回当前登录用户信息。
     * <p>该出口是前端「我是谁」的依据，因此必须走登录态并如实回显会话身份，不能再返回硬编码的管理员。</p>
     *
     * @return 用户信息
     */
    @GetMapping("/auth/user")
    public ApiResult<Map<String, Object>> authUser() {
        UserResume current = AiUserContext.currentUser();
        if (current == null) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "请先登录");
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("user_id", current.getUserId());
        user.put("username", current.getUsername());
        user.put("display_name", current.getNickName() == null
                ? current.getUsername() : current.getNickName());
        user.put("role", AiUserContext.isAdmin() ? "admin" : "user");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("user", user);
        return ApiResult.ok(result);
    }

    /**
     * 校验当前密码并修改登录密码（当前 OAuth 协议无对应能力时返回 501）。
     * <p>契约里改动的是宿主 WebUI 账号，并非当前登录用户自己的口令，因此仅管理员可调用；
     * 登录账号的口令由认证体系管理，本出口不落任何凭据。</p>
     *
     * @param body 请求体（含 当前_密码 / 新_密码）
     * @return 成功信封
     */
    @Permission("admin")
    @PostMapping("/auth/change-password")
    public ApiResult<Void> authChangePassword(@RequestBody Map<String, Object> body) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前 OAuth 协议未提供密码更新接口");
    }

    /**
     * 校验当前密码并修改登录用户名。
     * <p>改动的是宿主 WebUI 账号名，仅管理员可调用。</p>
     *
     * @param body 请求体（含 当前_密码 / 新_用户名）
     * @return 新用户名
     */
    @Permission("admin")
    @PostMapping("/auth/change-username")
    public ApiResult<Map<String, Object>> authChangeUsername(@RequestBody Map<String, Object> body) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前 OAuth 协议未提供用户名更新接口");
    }

    // -----------------------------------------------------------------------
    // Computer 命名空间（宿主未实现时明确返回 501）
    // -----------------------------------------------------------------------

    /**
     * 返回当前进程的辅助功能 / 屏幕录制授权状态。
     *
     * @return 权限状态
     */
    @GetMapping("/computer/permissions")
    public ApiResult<Map<String, Object>> computerPermissions() {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前宿主未实现系统隐私权限探测");
    }

    /**
     * 触发系统授权请求。
     * <p>请求的是宿主进程在操作系统层的授权，影响整台主机，因此仅管理员可触发。</p>
     *
     * @param body 请求体（含 种类）
     * @return 权限状态
     */
    @Permission("admin")
    @PostMapping("/computer/permissions/request")
    public ApiResult<Map<String, Object>> requestComputerPermission(@RequestBody Map<String, Object> body) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前宿主未实现系统隐私权限请求");
    }

    /**
     * 打开系统设置的对应隐私面板。
     * <p>操作的是宿主操作系统的设置面板，因此仅管理员可触发。</p>
     *
     * @param body 请求体（含 种类）
     * @return 成功信封
     */
    @Permission("admin")
    @PostMapping("/computer/permissions/open-settings")
    public ApiResult<Void> openComputerPermissionSettings(@RequestBody Map<String, Object> body) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前宿主未实现系统隐私设置面板打开");
    }

    // -----------------------------------------------------------------------
    // 内部工具方法
    // -----------------------------------------------------------------------

    /**
     * resolve Storage Generation
     * @return 返回值
    */
    private String resolveStorageGeneration() {
        return STORAGE_GENERATION;
    }

    /**
     * create Storage Generation
     * @return 返回值
    */
    private static String createStorageGeneration() {
        String env = System.getenv("STORAGE_GENERATION");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * resolve Client Settings
     * @return 返回值
    */
    private Map<String, Object> resolveClientSettings() {
        String userId = AiUserContext.requireUserId();
        Map<String, Object> result = new LinkedHashMap<>();
        CLIENT_SETTING_DEFAULTS.forEach((key, defaultValue) -> {
            String stored = userSettingService.resolve(userId, CLIENT_SETTING_GROUP, key).orElse(null);
            result.put(key, parseClientSetting(key, stored, defaultValue));
        });
        return result;
    }

    /**
     * serialize Client Setting
     * @param key 字符串
     * @param type 字符串
     * @param value 对象
     * @return 返回值
    */
    private String serializeClientSetting(String key, String type, Object value) {
        if (value == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, key + " 不能为空");
        }
        return switch (type) {
            case "BOOLEAN" -> {
                if (!(value instanceof Boolean)) {
                    throw new ApiException(ApiErrorCode.INVALID_PARAM, key + " 必须是布尔值");
                }
                yield value.toString();
            }
            case "NUMBER" -> {
                try {
                    int number = value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
                    Object defaultValue = CLIENT_SETTING_DEFAULTS.get(key);
                    if (key.equals("custFontSize") && (number < 10 || number > 32)
                            || key.equals("custChatWidth") && (number < 320 || number > 1920)) {
                        throw new ApiException(ApiErrorCode.INVALID_PARAM, key + " 超出允许范围");
                    }
                    if (defaultValue instanceof Integer && number <= 0) {
                        throw new ApiException(ApiErrorCode.INVALID_PARAM, key + " 必须大于 0");
                    }
                    yield Integer.toString(number);
                } catch (NumberFormatException e) {
                    throw new ApiException(ApiErrorCode.INVALID_PARAM, key + " 必须是数字");
                }
            }
            default -> String.valueOf(value);
        };
    }

    /**
     * parse Client Setting
     * @param defaultValue 对象
     * @param key 字符串
     * @param stored 字符串
     * @return 返回值
    */
    private Object parseClientSetting(String key, String stored, Object defaultValue) {
        if (stored == null) {
            return defaultValue;
        }
        String type = CLIENT_SETTING_TYPES.get(key);
        try {
            if ("BOOLEAN".equals(type)) {
                return Boolean.parseBoolean(stored);
            }
            if ("NUMBER".equals(type)) {
                return Integer.valueOf(stored);
            }
            return stored;
        } catch (NumberFormatException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "客户端设置值损坏: " + key);
        }
    }

    /**
     * persist Directory Setting
     * @param field 字符串
     * @param value 对象
     * @return 返回值
    */
    private void persistDirectorySetting(String field, Object value) {
        if (value == null || value.toString().isBlank()) {
            return;
        }
        try {
            String directory = java.nio.file.Path.of(value.toString()).toAbsolutePath().normalize().toString();
            globalSettingService.upsertValue(SYSTEM_SETTING_GROUP, field, directory, "系统目录配置");
        } catch (java.nio.file.InvalidPathException e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, field + " 不是有效目录路径");
        }
    }

    /**
     * resolve Setting
     * @param defaultValue 字符串
     * @param field 字符串
     * @return 返回值
    */
    private String resolveSetting(String field, String defaultValue) {
        return globalSettingService.getValue(SYSTEM_SETTING_GROUP, field).orElse(defaultValue);
    }

    /**
     * companion Access Token Key
     * @param companionId 字符串
     * @return 返回值
    */
    private String companionAccessTokenKey(String companionId) {
        return "companion-access-token:" + companionId;
    }

    /**
     * sha256
     * @param value 字符串
     * @return 返回值
    */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 探测运行平台。
     *
     * @return macos / 窗口 / Linux / other
     */
    private String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos";
        }
        if (os.contains("nux") || os.contains("nix") || os.contains("sunos")) {
            return "linux";
        }
        return "other";
    }

    /**
     * 解析缓存目录（优先环境变量，回退到系统临时目录）。
     *
     * @return 缓存目录真实路径
     */
    private String resolveCacheDir() {
        String env = System.getenv("NOMIFUN_CACHE_DIR");
        String defaultValue = env != null && !env.isBlank()
                ? env : System.getProperty("java.io.tmpdir");
        return resolveSetting("cache_dir", defaultValue);
    }

    /**
     * 解析日志目录（优先环境变量，回退到用户主目录下的隐藏目录）。
     *
     * @return 日志目录真实路径
     */
    private String resolveLogDir() {
        String env = System.getenv("NOMIFUN_LOG_DIR");
        String defaultValue = env != null && !env.isBlank()
                ? env : System.getProperty("user.home") + File.separator + ".nomifun" + File.separator + "logs";
        return resolveSetting("log_dir", defaultValue);
    }

    /**
     * 返回当前激活的 Spring 配置文件（用于前端 dev 模式判断）。
     * @return resolve活跃配置文件的结果
     */
    private String resolveActiveProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length > 0 ? profiles[0] : "default";
    }
}


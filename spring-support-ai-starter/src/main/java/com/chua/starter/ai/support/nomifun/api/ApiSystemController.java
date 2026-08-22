package com.chua.starter.ai.support.nomifun.api;

import com.chua.starter.ai.support.nomifun.common.ApiErrorCode;
import com.chua.starter.ai.support.nomifun.common.ApiException;
import com.chua.starter.ai.support.nomifun.common.ApiResult;
import com.chua.starter.ai.support.nomifun.ApiWebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统 / 设置 / WebUI 凭据 / 鉴权 / 计算机权限 控制器（对接 nomicore 契约）。
 *
 * <p>替代 Rust 后端的 system / settings / webui / auth / computer 命名空间，
 * 向前端 pages/ai（React+Tauri 桌面应用）提供纯 Spring 实现的 /api/* 接口。
 * 客户端设置与伙伴访问令牌使用内存 Map 维护（无持久态，符合契约占位语义）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
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
     * 客户端设置内存存储（key: notificationEnabled|cronNotificationEnabled|keepAwake|
     * saveUploadToWorkspace|autoPreviewOfficeFiles，value: 是否开启）
     */
    private final Map<String, Boolean> clientSettings = new ConcurrentHashMap<>();

    /**
     * 伙伴远程访问令牌内存存储（companionId → token 明文，仅生成一次，再次查询返回已配置）
     */
    private final Map<String, String> companionAccessTokens = new ConcurrentHashMap<>();

    /**
     * 客户端设置支持的键集合
     */
    private static final java.util.Set<String> CLIENT_SETTING_KEYS = java.util.Set.of(
            "notificationEnabled",
            "cronNotificationEnabled",
            "keepAwake",
            "saveUploadToWorkspace",
            "autoPreviewOfficeFiles",
            "customTheme",
            "customAccent",
            "custFontSize",
            "custSidebarCollapsed",
            "custTerminalTheme",
            "custChatWidth"
    );

    // -----------------------------------------------------------------------
    // System 命名空间
    // -----------------------------------------------------------------------

    /**
     * 返回运行时真实系统信息。
     *
     * @return 系统信息（缓存目录 / 工作目录 / 日志目录 / 存储代际 / 平台 / 架构）
     */
    @GetMapping("/system/info")
    public ApiResult<Map<String, Object>> systemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("cache_dir", resolveCacheDir());
        info.put("work_dir", System.getProperty("user.dir"));
        info.put("log_dir", resolveLogDir());
        info.put("storage_generation", resolveStorageGeneration());
        info.put("platform", detectPlatform());
        info.put("arch", System.getProperty("os.arch"));
        return ApiResult.ok(info);
    }

    /**
     * 触发出厂重置（纯后端无副作用实现，返回成功信封）。
     *
     * @return 成功信封
     */
    @PostMapping("/system/factory-reset")
    public ApiResult<Void> factoryReset() {
        log.info("收到出厂重置请求（占位实现，重启后由宿主进程执行实际擦除）");
        return ApiResult.ok();
    }

    /**
     * 持久化用户选择的工作目录（纯后端占位实现，返回成功信封）。
     *
     * @param body 请求体（含 work_dir）
     * @return 成功信封
     */
    @PostMapping("/system/work-dir")
    public ApiResult<Void> workDir(@RequestBody Map<String, Object> body) {
        Object workDir = body.get("work_dir");
        if (workDir == null || workDir.toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "work_dir 不能为空");
        }
        log.info("收到工作目录变更请求：{}（占位实现，重启后由宿主进程应用）", workDir);
        return ApiResult.ok();
    }

    // -----------------------------------------------------------------------
    // Settings 命名空间
    // -----------------------------------------------------------------------

    /**
     * 读取单个客户端设置（未设置默认 false）。
     *
     * @param key 设置键（如 notificationEnabled）
     * @return 布尔值
     */
    @GetMapping("/settings/client")
    public ApiResult<Object> getClientSetting(@RequestParam(required = false) String key) {
        if (key == null || key.isBlank()) {
            return ApiResult.ok(new LinkedHashMap<>(clientSettings));
        }
        return ApiResult.ok(clientSettings.getOrDefault(key, false));
    }

    /**
     * 更新客户端设置（请求体以 key 为字段名，如 {notificationEnabled:true}）。
     *
     * @param body 设置键值对
     * @return 成功信封
     */
    @PutMapping("/settings/client")
    public ApiResult<Void> putClientSetting(@RequestBody Map<String, Object> body) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            clientSettings.put(key, Boolean.TRUE.equals(entry.getValue()));
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
        pushService.push("system-settings:language-changed", Map.of("language", language.toString()));
        return ApiResult.ok();
    }

    // -----------------------------------------------------------------------
    // WebUI 凭据命名空间（本地信任，桌面壳专用）
    // -----------------------------------------------------------------------

    /**
     * 修改 WebUI 管理员密码（纯后端占位实现）。
     *
     * @param body 请求体（含 new_password）
     * @return 成功信封
     */
    @PostMapping("/webui/change-password")
    public ApiResult<Void> webuiChangePassword(@RequestBody Map<String, Object> body) {
        if (body.get("new_password") == null || body.get("new_password").toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "new_password 不能为空");
        }
        log.info("收到 WebUI 密码变更请求（占位实现）");
        return ApiResult.ok();
    }

    /**
     * 修改 WebUI 管理员用户名。
     *
     * @param body 请求体（含 new_username）
     * @return 新用户名
     */
    @PostMapping("/webui/change-username")
    public ApiResult<Map<String, Object>> webuiChangeUsername(@RequestBody Map<String, Object> body) {
        Object newUsername = body.get("new_username");
        if (newUsername == null || newUsername.toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "new_username 不能为空");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", newUsername.toString());
        return ApiResult.ok(result);
    }

    /**
     * 生成用于移动端配对的二维码令牌。
     *
     * @return 令牌与过期时间戳
     */
    @PostMapping("/webui/generate-qr-token")
    public ApiResult<Map<String, Object>> generateQrToken() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", UUID.randomUUID().toString().replace("-", ""));
        // 令牌有效期 10 分钟
        result.put("expires_at_ms", System.currentTimeMillis() + 10 * 60 * 1000L);
        return ApiResult.ok(result);
    }

    /**
     * 查询某伙伴的远程访问令牌是否已配置。
     *
     * @param companionId 伙伴 ID
     * @return 是否已配置
     */
    @GetMapping("/webui/companions/{companionId}/access-token")
    public ApiResult<Map<String, Object>> getCompanionAccessToken(@PathVariable String companionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", companionAccessTokens.containsKey(companionId));
        return ApiResult.ok(result);
    }

    /**
     * 为某伙伴生成远程访问令牌（仅返回明文一次）。
     *
     * @param companionId 伙伴 ID
     * @return 令牌 / 伙伴 ID / 可选告警
     */
    @PostMapping("/webui/companions/{companionId}/access-token")
    public ApiResult<Map<String, Object>> mintCompanionAccessToken(@PathVariable String companionId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        companionAccessTokens.put(companionId, token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("companion_id", companionId);
        return ApiResult.ok(result);
    }

    /**
     * 吊销某伙伴的远程访问令牌。
     *
     * @param companionId 伙伴 ID
     * @return 是否已配置（吊销后恒为 false）
     */
    @DeleteMapping("/webui/companions/{companionId}/access-token")
    public ApiResult<Map<String, Object>> revokeCompanionAccessToken(@PathVariable String companionId) {
        companionAccessTokens.remove(companionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", false);
        return ApiResult.ok(result);
    }

    // -----------------------------------------------------------------------
    // Auth 命名空间（WebUI 浏览器会话自助改密）
    // -----------------------------------------------------------------------

    /**
     * 校验当前密码并修改登录密码（纯后端占位实现）。
     *
     * @param body 请求体（含 current_password / new_password）
     * @return 成功信封
     */
    @PostMapping("/auth/change-password")
    public ApiResult<Void> authChangePassword(@RequestBody Map<String, Object> body) {
        if (body.get("current_password") == null || body.get("current_password").toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "current_password 不能为空");
        }
        if (body.get("new_password") == null || body.get("new_password").toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "new_password 不能为空");
        }
        log.info("收到 WebUI 浏览器会话密码变更请求（占位实现）");
        return ApiResult.ok();
    }

    /**
     * 校验当前密码并修改登录用户名。
     *
     * @param body 请求体（含 current_password / new_username）
     * @return 新用户名
     */
    @PostMapping("/auth/change-username")
    public ApiResult<Map<String, Object>> authChangeUsername(@RequestBody Map<String, Object> body) {
        if (body.get("current_password") == null || body.get("current_password").toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "current_password 不能为空");
        }
        Object newUsername = body.get("new_username");
        if (newUsername == null || newUsername.toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "new_username 不能为空");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", newUsername.toString());
        return ApiResult.ok(result);
    }

    // -----------------------------------------------------------------------
    // Computer 命名空间（macOS TCC 权限探测占位）
    // -----------------------------------------------------------------------

    /**
     * 返回当前进程的辅助功能 / 屏幕录制授权状态（占位：非 macOS 返回 null）。
     *
     * @return 权限状态
     */
    @GetMapping("/computer/permissions")
    public ApiResult<Map<String, Object>> computerPermissions() {
        return ApiResult.ok(buildPermissionStatus());
    }

    /**
     * 触发授权请求（占位：仅返回当前探测状态）。
     *
     * @param body 请求体（含 kind）
     * @return 权限状态
     */
    @PostMapping("/computer/permissions/request")
    public ApiResult<Map<String, Object>> requestComputerPermission(@RequestBody Map<String, Object> body) {
        if (body.get("kind") == null || body.get("kind").toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "kind 不能为空");
        }
        log.info("收到计算机权限请求：{}（占位实现）", body.get("kind"));
        return ApiResult.ok(buildPermissionStatus());
    }

    /**
     * 打开系统设置的对应隐私面板（占位实现）。
     *
     * @param body 请求体（含 kind）
     * @return 成功信封
     */
    @PostMapping("/computer/permissions/open-settings")
    public ApiResult<Void> openComputerPermissionSettings(@RequestBody Map<String, Object> body) {
        if (body.get("kind") == null || body.get("kind").toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "kind 不能为空");
        }
        log.info("收到打开权限设置面板请求：{}（占位实现）", body.get("kind"));
        return ApiResult.ok();
    }

    // -----------------------------------------------------------------------
    // 内部工具方法
    // -----------------------------------------------------------------------

        private String resolveStorageGeneration() {
        String env = System.getenv("STORAGE_GENERATION");
        if (env != null && !env.isBlank()) return env;
        long millis = System.currentTimeMillis();
        byte[] bytes = new byte[16];
        bytes[0] = (byte)(millis >>> 40);
        bytes[1] = (byte)(millis >>> 32);
        bytes[2] = (byte)(millis >>> 24);
        bytes[3] = (byte)(millis >>> 16);
        bytes[4] = (byte)(millis >>> 8);
        bytes[5] = (byte)millis;
        java.util.concurrent.ThreadLocalRandom.current().nextBytes(bytes);
        bytes[6] = (byte)((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte)((bytes[8] & 0x3F) | 0x80);
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-');
            sb.append(hex[(bytes[i] >> 4) & 0xF]).append(hex[bytes[i] & 0xF]);
        }
        return sb.toString();
    }

/**
     * 构造计算机权限状态对象。
     *
     * @return 权限状态（accessibility / screen_recording / platform / app_label）
     */
    private Map<String, Object> buildPermissionStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("accessibility", null);
        status.put("screen_recording", null);
        status.put("platform", detectPlatform());
        status.put("app_label", resolveAppLabel());
        return status;
    }

    /**
     * 探测运行平台。
     *
     * @return macos / windows / linux / other
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
        if (env != null && !env.isBlank()) {
            return env;
        }
        return System.getProperty("java.io.tmpdir");
    }

    /**
     * 解析日志目录（优先环境变量，回退到用户主目录下的隐藏目录）。
     *
     * @return 日志目录真实路径
     */
    private String resolveLogDir() {
        String env = System.getenv("NOMIFUN_LOG_DIR");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return System.getProperty("user.home") + File.separator + ".nomifun" + File.separator + "logs";
    }

    /**
     * 解析存储代际（优先环境变量，回退常量 1）。
     *
     * @return 存储代际字符串
     */
    

    /**
     * 解析应用显示名（优先环境变量，回退默认值）。
     *
     * @return 应用标签
     */
    private String resolveAppLabel() {
        String env = System.getenv("NOMIFUN_APP_LABEL");
        return env != null && !env.isBlank() ? env : "Nomifun";
    }
}


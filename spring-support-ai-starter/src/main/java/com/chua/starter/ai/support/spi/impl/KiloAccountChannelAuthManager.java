package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kilo AI 免费池账号渠道鉴权管理器实现。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code src/kilo.js}，<b>免鉴权渠道</b>——
 * Kilo AI 公开网关的免费模型池，无需 key、无需登录、无需指纹）：</p>
 * <ol>
 *   <li>{@code startLogin}：免鉴权渠道无需绑定账号，直接返回成功句柄。</li>
 *   <li>{@code pollLogin}：立即返回空凭据（无 access_token，无 refresh）。</li>
 *   <li>{@code refresh}：无续期机制，抛 {@code NOT_IMPLEMENTED}。</li>
 *   <li>{@code status}：无凭据可探活，直接判 valid。</li>
 *   <li>{@code checkin}：无签到机制。</li>
 *   <li>{@code listModels}：{@code GET https://api.kilo.ai/api/gateway/models}，
 *       过滤 {@code isFree=true} 或 id 后缀 {@code -free} 的模型。</li>
 *   <li>{@code toRouteTarget}：返回路由目标，baseUrl 为 Kilo 网关，apiKey 为空串（免鉴权）。</li>
 * </ol>
 *
 * <p>⚠️ Kilo 免费池的提示：免费模型的 prompt 可能被上游记录并用于改进服务，
 * 适用于一次性任务，不要发送敏感信息。</p>
 *
 * <p>凭据落库：空 Map（无字段）。provider 配置中 platform=openai，
 * baseUrl=https://api.kilo.ai/api/gateway，appKey 为空。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("kilo")
public class KiloAccountChannelAuthManager implements AccountChannelAuthManager {

    /** Kilo 网关基础地址。 */
    static final String API_BASE = "https://api.kilo.ai/api/gateway";

    /** 模型目录端点。 */
    static final String MODELS_PATH = "/models";

    /** 轮询句柄标识。 */
    static final String HANDLE_KEYLESS = "kilo-keyless";

    @Override
    public String channel() {
        return "kilo";
    }

    @Override
    public String displayName() {
        return "Kilo AI 免费池";
    }

    @Override
    public String loginMethod() {
        return "api-key";
    }

    /**
     * 免鉴权渠道无需绑定账号，直接返回成功句柄。
     *
     * <p>前端据此渲染「Kilo 免费池无需绑定账号，点击确认即可」提示，
     * 用户点击确认后前端调用 {@link #pollLogin} 完成绑定。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", HANDLE_KEYLESS);
        handle.put("loginMethod", "api-key");
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * 免鉴权渠道立即返回空凭据（无字段）。
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || !HANDLE_KEYLESS.equals(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Kilo 登录句柄无效");
        }
        return new LinkedHashMap<>();
    }

    /**
     * 免鉴权渠道无续期机制。
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                "Kilo 免费池无需续期（无凭据，免鉴权）");
    }

    /**
     * 无凭据可探活，直接判 valid。
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        return status("valid", "Kilo 免费池免鉴权，无凭据可探活");
    }

    @Override
    public boolean supportsCheckin() {
        return false;
    }

    /**
     * 拉取 Kilo 网关免费模型列表。
     *
     * <p>{@code GET https://api.kilo.ai/api/gateway/models}，响应为
     * {@code { data: [{ id, ... }] }} 格式。免费判定按 {@code isFree=true}
     * 字段或 id 后缀 {@code -free}（与 our free model 插件口径一致）。
     * 远端拉取失败不致命，返回空列表。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            headers.put("User-Agent", "dsh-our-free-model");
            String response = ChannelHttp.getJson(API_BASE + MODELS_PATH, headers);
            Map<String, Object> payload = parseJson(response);
            Object data = payload.get("data");
            if (!(data instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object idRaw = entry.get("id");
                if (idRaw == null) {
                    continue;
                }
                String id = String.valueOf(idRaw);
                if (!seen.add(id)) {
                    continue;
                }
                Object isFreeRaw = entry.get("isFree");
                boolean isFree = Boolean.TRUE.equals(isFreeRaw)
                        || (isFreeRaw instanceof String s && "true".equalsIgnoreCase(s))
                        || id.endsWith("-free");
                if (!isFree) {
                    continue;
                }
                Map<String, Object> model = new LinkedHashMap<>();
                model.put("id", id);
                Object name = entry.get("name");
                model.put("name", name instanceof String s && !s.isBlank() ? s : id);
                model.put("isFree", true);
                result.add(model);
            }
            return result;
        } catch (ApiException ignored) {
            return List.of();
        }
    }

    @Override
    public RouteTarget toRouteTarget(Map<String, Object> credentials) {
        // Kilo 免鉴权：apiKey 为空串（显式免鉴权），baseUrl 为 Kilo 网关。
        return RouteTarget.builder()
                .provider("openai")
                .baseUrl(API_BASE)
                .apiKey("")
                .authScheme("none")
                .autoSwitchOnRateLimit(true)
                .build();
    }

    // ── 私有辅助 ──

    private static Map<String, Object> status(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    private static Map<String, Object> parseJson(String response) {
        Map<String, Object> map = Json.getJsonObject(response);
        return map != null ? map : new LinkedHashMap<>();
    }

    private static String readString(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }
}

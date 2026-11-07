// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KiloAccountChannelAuthManager} 的免鉴权契约测试。
 *
 * <p>Kilo 免费池既无 API key 也无授权页，必须用 {@code loginMethod=keyless} 表达：
 * 前端据此不渲染任何输入框。历史实现返回 {@code api-key}，会让弹窗要求用户
 * 填一个根本不存在的凭据，看上去像「绑定失败」。</p>
 *
 * <p>不依赖外网：只覆盖本地合同（标识 / 登录方式 / 句柄 / 路由目标），
 * 模型目录拉取需要真实网络，不在本测试范围。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
class KiloAccountChannelAuthManagerTest {

    /** 被测实例（无外部依赖）。 */
    private final KiloAccountChannelAuthManager manager = new KiloAccountChannelAuthManager();

    /**
     * 渠道标识与登录方式必须稳定为 keyless。
     */
    @Test
    @DisplayName("channel/loginMethod/authMethod：免鉴权渠道用 keyless，不得伪装成 api-key")
    void exposesKeylessLoginMethod() {
        assertEquals("kilo", manager.channel());
        assertEquals("keyless", manager.loginMethod());
        assertEquals("keyless", manager.authMethod());
    }

    /**
     * startLogin 直接给出可轮询句柄；pollLogin 立即返回空凭据。
     */
    @Test
    @DisplayName("startLogin/pollLogin：直接给出 keyless 句柄与空凭据")
    void keylessHandshake() {
        Map<String, Object> handle = manager.startLogin();

        assertEquals(KiloAccountChannelAuthManager.HANDLE_KEYLESS, handle.get("handle"));
        assertEquals("keyless", handle.get("loginMethod"));
        assertTrue(handle.get("startedAt") instanceof Long);

        Map<String, Object> credentials = manager.pollLogin(handle);
        assertNotNull(credentials);
        assertTrue(credentials.isEmpty(), "免鉴权渠道不应返回任何凭据字段");
    }

    /**
     * 非本渠道句柄必须拒绝，避免前端拿错句柄时静默成功。
     */
    @Test
    @DisplayName("pollLogin：非法句柄抛 INVALID_PARAM")
    void rejectsUnknownHandle() {
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", "kilo-unknown");

        ApiException error = assertThrows(ApiException.class, () -> manager.pollLogin(handle));

        assertEquals(ApiErrorCode.INVALID_PARAM, error.getErrorCode());
    }

    /**
     * 路由目标必须显式免鉴权（空 key + authScheme=none），否则下游会拼出
     * {@code Authorization: Bearer } 这种空令牌头，被网关判为非法。
     */
    @Test
    @DisplayName("toRouteTarget：baseUrl 指向 Kilo 网关，空 key 且 authScheme=none")
    void routeTargetIsKeyless() {
        RouteTarget target = manager.toRouteTarget(Map.of());

        assertEquals("openai", target.getProvider());
        assertEquals(KiloAccountChannelAuthManager.API_BASE, target.getBaseUrl());
        assertEquals("", target.getApiKey());
        assertEquals("none", target.getAuthScheme());
    }
}

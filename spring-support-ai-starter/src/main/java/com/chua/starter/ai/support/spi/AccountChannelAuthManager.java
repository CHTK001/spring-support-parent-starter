package com.chua.starter.ai.support.spi;

import com.chua.common.support.ai.chat.route.RouteTarget;

import java.util.List;
import java.util.Map;

/**
 * 账号渠道鉴权管理器 SPI。
 *
 * <p>每个账号类渠道（如 Cline、Qoder、Trae、Gemini）一个实现，通过
 * {@code @Spi("channel-id")} 注册，并由
 * {@code META-INF/extensions/com.chua.starter.ai.support.spi.AccountChannelAuthManager}
 * 枚举全部实现类。实现类<b>不是 Spring Bean</b>（由 SPI 框架无参构造创建），
 * 需要注入依赖时由 {@code SpringServiceAutowire} 补装，保持自包含。</p>
 *
 * <p>统一契约：</p>
 * <ul>
 *   <li>{@link #channel()} / {@link #displayName()}：渠道标识与展示名</li>
 *   <li>{@link #startLogin()}：发起登录，返回前端轮询句柄（如设备码）</li>
 *   <li>{@link #pollLogin()}：按句柄轮询登录进度；成功返回凭据 Map，待确认返回 null，
 *       失败抛出异常</li>
 *   <li>{@link #refresh()}：用 refresh_token 换取新访问令牌，返回更新后的凭据 Map</li>
 *   <li>{@link #status()}：当前凭据有效性检查（401 探活或本地到期判定）</li>
 *   <li>{@link #checkin()}：每日签到/领取免费额度（可选，无签到渠道默认 no-op）</li>
 *   <li>{@link #listModels()}：拉取当前账号可用模型列表（可选）</li>
 *   <li>{@link #toRouteTarget()}：把 OAuth 凭据转为路由目标（供免费渠道聚合客户端
 *       按模型路由到本渠道上游，多账号 429 failover）</li>
 * </ul>
 *
 * <p>凭据为不透明 Map（键如 {@code access_token} / {@code refresh_token} /
 * {@code expires_at}），由 {@code SysAiProviderConnection.credentials} JSON 字段持久化，
 * 本模块不感知具体渠道的字段形态。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public interface AccountChannelAuthManager {

    /**
     * 渠道标识（稳定，持久化用），与 {@code @Spi} 别名一致。
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 渠道展示名称。
     *
     * @return 展示名称
     */
    String displayName();

    /**
     * 登录方式描述，供前端渲染对应的绑定 UI。
     *
     * <p>约定词表（实现方必须使用其一）：</p>
     * <ul>
     *   <li>{@code device-code}：设备码（用户打开 verifyUrl 输入 user_code，如 Cline / Qoder）</li>
     *   <li>{@code browser-device}：浏览器设备授权（打开 verifyUrl 完成授权，服务端轮询，如 ZCode）</li>
     *   <li>{@code browser-oauth}：浏览器授权 + 粘贴回调 URL（Gemini / Trae / LobsterAI）</li>
     *   <li>{@code qr-code}：扫码登录（verifyUrl 为二维码 URL，如 Raccoon）</li>
     *   <li>{@code sms}：短信验证码（无 URL，前端渲染手机号 + 验证码输入，如 Loomy）</li>
     *   <li>{@code api-key}：粘贴 API key（无 URL，如 OpenCode，匿名通道 key 为 {@code public}）</li>
     * </ul>
     *
     * @return 登录方式
     */
    default String loginMethod() {
        return "device-code";
    }

    /**
     * 发起登录。
     *
     * <p>对设备码渠道：调用上游 authorize/device 端点，返回包含
     * {@code handle}（轮询句柄，如 device_code）与 {@code verifyUrl}（用户需打开的页面）
     * 的 Map。对回调/验证码类渠道：返回含 {@code handle} 的 Map，具体含义由各渠道自定。</p>
     *
     * @return 登录句柄 Map（至少含 {@code handle} 键）
     * @throws Exception 上游调用失败
     */
    Map<String, Object> startLogin() throws Exception;

    /**
     * 轮询登录进度。
     *
     * @param handle {@link #startLogin()} 返回的句柄 Map
     * @return 成功时返回可直接持久化的凭据 Map（含 {@code access_token} 等键）；
     *         仍待确认时返回 {@code null}；最终失败（过期/拒绝/超过最大失败次数）抛异常
     * @throws Exception 登录失败
     */
    Map<String, Object> pollLogin(Map<String, Object> handle) throws Exception;

    /**
     * 刷新凭据。
     *
     * @param credentials 当前凭据（来自连接表，至少含 {@code refresh_token}）
     * @return 更新后的凭据 Map（新访问令牌及到期时间）
     * @throws Exception 刷新失败（refresh_token 失效等）
     */
    Map<String, Object> refresh(Map<String, Object> credentials) throws Exception;

    /**
     * 凭据状态检查。
     *
     * @param credentials 当前凭据
     * @return 状态描述：{@code valid}（在有效期且探活成功）、{@code expired}（本地判定到期）、
     *         {@code invalid}（探活 401）；不支持探活的渠道默认按到期时间判定
     * @throws Exception 检查失败
     */
    Map<String, Object> status(Map<String, Object> credentials) throws Exception;

    /**
     * 每日签到 / 免费额度领取。
     *
     * <p>默认 no-op（不支持签到渠道直接跳过）。支持签到的渠道实现：调用上游签到端点，
     * 把领取到的额度信息（键如 {@code credits}、{@code message}）并入凭据 Map 一并返回。</p>
     *
     * @param credentials 当前凭据
     * @return 更新后的凭据 Map（并入签到结果）
     * @throws Exception 签到失败
     */
    default Map<String, Object> checkin(Map<String, Object> credentials) throws Exception {
        return credentials;
    }

    /**
     * 是否支持每日签到（自动签到任务据此跳过 no-op 渠道）。
     *
     * @return true 表示支持
     */
    default boolean supportsCheckin() {
        return false;
    }

    /**
     * 拉取当前账号可用模型。
     *
     * @param credentials 当前凭据
     * @return 模型列表，每项含 {@code id} / {@code name}，可选 {@code contextWindow}；
     *         不支持在线拉取的渠道返回默认清单（可为空）
     * @throws Exception 拉取失败
     */
    default List<Map<String, Object>> listModels(Map<String, Object> credentials) throws Exception {
        return List.of();
    }

    /**
     * 把 OAuth 凭据转为路由目标（供免费渠道聚合客户端按模型路由到本渠道上游）。
     *
     * <p>返回 {@code null} 表示本渠道不支持路由（无固定推理端点或需 WASM 签名等）。
     * 实现方应返回 {@link RouteTarget}，其中 {@code apiKey} 取自凭据的
     * {@code access_token}（session / device_token / JWT 等），{@code baseUrl}
     * 为本渠道的 OpenAI 兼容推理端点，认证字段按本渠道要求设置。</p>
     *
     * <p>多账号 failover：{@code collectChannelRoutes} 会为同一渠道的多个连接
     * 各自调用本方法，再把返回的 {@code apiKey} 聚合到同一个 {@code RouteTarget.apiKeys}
     * 列表，触发 429 限流时自动切换到下一个账号。</p>
     *
     * @param credentials 连接凭据（含 {@code access_token} 等）
     * @return 路由目标；不支持路由时返回 {@code null}
     */
    default RouteTarget toRouteTarget(Map<String, Object> credentials) {
        return null;
    }
}

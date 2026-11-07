package com.chua.starter.common.support.channel;

import com.chua.common.support.ai.chat.route.RouteTarget;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 账号类 AI 渠道鉴权管理器 SPI。
 *
 * <p>每个账号类渠道（如 Cline、Qoder、Trae、Gemini）一个实现，通过
 * {@code @Spi("channel-id")} 注册，并由
 * {@code META-INF/extensions/com.chua.starter.common.support.channel.AccountChannelAuthManager}
 * 枚举全部实现类。实现类<b>不是 Spring Bean</b>（由 SPI 框架无参构造创建），
 * 需要注入依赖时由 {@code SpringServiceAutowire} 补装，保持自包含。</p>
 *
 * <p><b>为什么放在 common</b>：渠道能力归账号模块，但实现类与消费方分处
 * {@code spring-support-account-starter} 与 {@code spring-support-ai-starter}。
 * 契约放在任一业务模块都会逼另一边反向依赖，故下沉到两者共同依赖的 common；
 * 两边都只依赖 common，谁实现、谁消费互不感知。</p>
 *
 * <p>免登录 ChatClient 渠道由账号与渠道模块通过 utils 的 {@code ChatClient} SPI 提供，
 * 引入该 starter 即在 classpath 生效，AI 侧不需要写一行渠道代码。</p>
 *
 * <p>统一契约：</p>
 * <ul>
 *   <li>{@link #channel()} / {@link #displayName()}：渠道标识与展示名</li>
 *   <li>{@link #startLogin()} / {@link #pollLogin()} / {@link #refresh()} / {@link #status()}：
 *       绑定 / 轮询 / 刷新 / 探活</li>
 *   <li>{@link #checkin()} / {@link #supportsCheckin()}：每日签到 / 领取免费额度（可选）</li>
 *   <li>{@link #listModels(Map)}：拉取当前账号可用模型列表（可选）</li>
 *   <li>{@link #toRouteTarget(Map)}：把 OAuth 凭据转为路由目标（供免费渠道聚合客户端
 *       按模型路由到本渠道上游，多账号 429 failover）</li>
 * </ul>
 *
 * <p>凭据为不透明 Map（键如 {@code access_token} / {@code refresh_token} /
 * {@code expires_at}），由调用方持久化，本契约不感知具体渠道的字段形态。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public interface AccountChannelAuthManager {

    /**
     * 渠道标识（稳定，持久化 / 路由用），与 {@code @Spi} 别名一致。
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
     * 支持的账号类型。
     *
     * <p>本接口只承载登录态账号，故默认仅 {@code FIXED}。返回字符串而非枚举，
     * 是为了让契约不依赖任何业务模块的账号类型定义。</p>
     *
     * @return 支持的账号类型集合
     */
    default Set<String> accountTypes() {
        return Set.of("FIXED");
    }

    /**
     * 渠道优先级（数值越大越优先）。
     *
     * @return 优先级，默认 {@code 0}
     */
    default int priority() {
        return 0;
    }

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
     *   <li>{@code keyless}：完全免鉴权（无 URL、无凭据，点绑定后直接完成，如 Kilo 免费池）。
     *       前端不得为它渲染任何输入框，也不得要求用户填写 key</li>
     * </ul>
     *
     * @return 登录方式
     */
    default String loginMethod() {
        return "device-code";
    }

    /**
     * 绑定方式：与 {@link #loginMethod()} 语义一致，默认委托过去。
     *
     * @return 登录方式
     */
    default String authMethod() {
        return loginMethod();
    }

    /**
     * 发起登录。
     *
     * <p>对设备码渠道：调用上游 authorize/device 端点，返回包含 {@code handle}（轮询句柄，
     * 如 device_code）与 {@code verifyUrl}（用户需打开的页面）的 Map。对回调 / 验证码类渠道：
     * 返回含 {@code handle} 的 Map，具体含义由各渠道自定。</p>
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
     *         仍待确认时返回 {@code null}；最终失败（过期 / 拒绝 / 超过最大失败次数）抛异常
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
     * <p>默认返回空清单：保持既有实现零改动，支持在线拉取的渠道自行覆盖。</p>
     *
     * @param credentials 当前凭据
     * @return 模型列表（每项含 {@code id} / {@code name}，可选 {@code contextWindow}）
     * @throws Exception 拉取失败
     */
    default List<Map<String, Object>> listModels(Map<String, Object> credentials) throws Exception {
        return List.of();
    }

    /**
     * 把 OAuth 凭据转为路由目标。
     *
     * <p>默认返回 {@code null}：保持既有实现零改动，支持路由的渠道自行覆盖。</p>
     *
     * @param credentials 连接凭据（含 {@code access_token} 等）
     * @return 路由目标；不支持路由时返回 {@code null}
     */
    default RouteTarget toRouteTarget(Map<String, Object> credentials) {
        return null;
    }
}

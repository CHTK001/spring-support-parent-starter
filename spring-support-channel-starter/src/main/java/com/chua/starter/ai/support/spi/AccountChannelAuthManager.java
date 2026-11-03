package com.chua.starter.ai.support.spi;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.starter.channel.spi.AccountType;
import com.chua.starter.channel.spi.ChannelKind;
import com.chua.starter.channel.spi.FixedAccountProvider;
import com.chua.starter.channel.spi.ModelCatalog;
import com.chua.starter.channel.spi.Routable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 账号渠道鉴权管理器 SPI。
 *
 * <p>每个账号类渠道（如 Cline、Qoder、Trae、Gemini）一个实现，通过
 * {@code @Spi("channel-id")} 注册，并由
 * {@code META-INF/extensions/com.chua.starter.ai.support.spi.AccountChannelAuthManager}
 * 枚举全部实现类。实现类<b>不是 Spring Bean</b>（由 SPI 框架无参构造创建），
 * 需要注入依赖时由 {@code SpringServiceAutowire} 补装，保持自包含。</p>
 *
 * <p><b>类型化收敛</b>：本接口是通用渠道 SPI
 * （{@code com.chua.starter.channel.spi.ChannelProvider}）在 AI 渠道上的特化——
 * 继承 {@link FixedAccountProvider}（固定账号绑定能力）、{@link Routable}（产出 LLM 路由目标）
 * 与 {@link ModelCatalog}（在线拉取模型），三者均为 {@code kind=AI} 渠道专属。</p>
 *
 * <p>统一契约（大部分方法已由父接口提供，此处仅补充 AI 语义默认值）：</p>
 * <ul>
 *   <li>{@link #channel()} / {@link #displayName()}：渠道标识与展示名</li>
 *   <li>{@link #startLogin()} / {@link #pollLogin()} / {@link #refresh()} / {@link #status()}：
 *       绑定 / 轮询 / 刷新 / 探活</li>
 *   <li>{@link #checkin()} / {@link #supportsCheckin()}：每日签到 / 领取免费额度（可选）</li>
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
public interface AccountChannelAuthManager extends FixedAccountProvider, Routable, ModelCatalog {

    /**
     * 渠道展示名称。
     *
     * @return 展示名称
     */
    @Override
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
     * AI 渠道种类。
     *
     * @return {@link ChannelKind#AI}
     */
    @Override
    default ChannelKind kind() {
        return ChannelKind.AI;
    }

    /**
     * AI 渠道仅支持固定账号。
     *
     * @return 仅含 {@link AccountType#FIXED} 的集合
     */
    @Override
    default Set<AccountType> accountTypes() {
        return Set.of(AccountType.FIXED);
    }

    /**
     * 绑定方式：与 {@link #loginMethod()} 语义一致，默认委托过去。
     *
     * @return 登录方式
     */
    @Override
    default String authMethod() {
        return loginMethod();
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
    @Override
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
    @Override
    default RouteTarget toRouteTarget(Map<String, Object> credentials) {
        return null;
    }
}

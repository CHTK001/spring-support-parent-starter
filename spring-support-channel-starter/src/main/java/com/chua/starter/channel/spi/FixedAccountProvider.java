package com.chua.starter.channel.spi;

import java.util.Map;

/**
 * 固定账号能力 SPI —— 绑定 / 授权拿长期凭据。
 *
 * <p>对应现有 AI 渠道的绑定部分：发起登录、轮询登录、刷新凭据、探活、签到。
 * 凭据为不透明 Map（键如 {@code access_token} / {@code refresh_token} / {@code expires_at}），
 * 由渠道实现自行决定字段形态，本层不感知。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface FixedAccountProvider extends ChannelProvider {

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
}

package com.chua.starter.channel.tempaccount.model;

import com.chua.common.support.datasearch.sms.model.SmsPhoneInfo;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时身份 —— 一次性的随机虚拟身份（组合资源）。
 *
 * <p>组合关系：一个 {@code SMS} 渠道（接码）+ 一个 {@code EMAIL} 渠道（收信），
 * 可只其一。短信渠道领到一个随机虚拟号码（{@link SmsPhoneInfo}），邮件渠道领到
 * 一个新建临时邮箱地址。</p>
 *
 * <p>身份引用的是「渠道标识」（{@code smsChannel} / {@code emailChannel}，如
 * {@code sms-freephonenum} / {@code email-mail-tm}），轮询时按渠道标识回
 * {@code com.chua.starter.channel.ChannelRegistry} 取实现，从而摆脱对具体数据源的硬编码。</p>
 *
 * <p>消息（短信 / 邮件）以不透明 Map 形态在内存中缓存（倒序：最新在前），并增量落库；
 * 由 {@link com.chua.starter.channel.tempaccount.TempAccountService} 定时轮询抓取，
 * 通过 SSE 实时推送到前端。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@Builder
public class TempIdentity {

    /**
     * 身份标识
     */
    private String id;

    /**
     * 随机虚拟号码（接码），可空
     */
    private SmsPhoneInfo phone;

    /**
     * 短信渠道标识（如 sms-freephonenum），可空
     */
    private String smsChannel;

    /**
     * 短信数据源名称（如 freephonenum），展示用，可空
     */
    private String smsProvider;

    /**
     * 随机临时邮箱地址，可空
     */
    private String email;

    /**
     * 邮件渠道标识（如 email-mail-tm），可空
     */
    private String emailChannel;

    /**
     * 邮件数据源名称（如 mail-tm），展示用，可空
     */
    private String emailProvider;

    /**
     * 归属用户（账号隔离记录用），可空
     */
    private String userId;

    /**
     * 创建时间（毫秒）
     */
    private long createdAt;

    /**
     * 已收到的短信载荷（倒序：最新在前）
     */
    @Builder.Default
    private final List<Map<String, Object>> smsMessages = new ArrayList<>();

    /**
     * 已收到的邮件载荷（倒序：最新在前）
     */
    @Builder.Default
    private final List<Map<String, Object>> emails = new ArrayList<>();

    /**
     * 已见短信指纹（去重，避免重复广播与重复落库）
     */
    @Builder.Default
    private final Set<String> seenSms = ConcurrentHashMap.newKeySet();

    /**
     * 已见邮件指纹（去重）
     */
    @Builder.Default
    private final Set<String> seenEmail = ConcurrentHashMap.newKeySet();

    /**
     * 重建短信身份载荷（传给 {@code TemporaryAccountProvider#fetchMessages}）。
     *
     * @return 身份载荷；无号码时返回空 Map
     */
    public Map<String, Object> smsIdentity() {
        Map<String, Object> identity = new LinkedHashMap<>(2);
        identity.put("phone", phone);
        identity.put("provider", smsProvider);
        return identity;
    }

    /**
     * 重建邮件身份载荷（传给 {@code TemporaryAccountProvider#fetchMessages}）。
     *
     * @return 身份载荷；无邮箱时返回空 Map
     */
    public Map<String, Object> emailIdentity() {
        Map<String, Object> identity = new LinkedHashMap<>(2);
        identity.put("email", email);
        identity.put("provider", emailProvider);
        return identity;
    }
}

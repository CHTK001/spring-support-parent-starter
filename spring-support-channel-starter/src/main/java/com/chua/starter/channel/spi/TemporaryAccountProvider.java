package com.chua.starter.channel.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 临时账号能力 SPI —— 领取虚拟身份 + 收消息（接码 / 收信）。
 *
 * <p>实现方为「一个数据源一个渠道」的薄适配器：短信接码把
 * {@code com.chua.common.support.datasearch.sms.spi.SmsCodeProvider} 包装成
 * {@link ChannelKind#SMS} 渠道，临时邮箱把
 * {@code com.chua.common.support.datasearch.email.spi.EmailProvider} 包装成
 * {@link ChannelKind#EMAIL} 渠道。</p>
 *
 * <p>身份与消息均以不透明 Map 表达，字段形态由各渠道自定；去重由上层编排保证。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface TemporaryAccountProvider extends ChannelProvider {

    /**
     * 领取一个临时身份。
     *
     * <p>短信渠道返回随机虚拟号码画像（键如 {@code phone} / {@code countryCode} /
     * {@code countryName} / {@code smsUrl}）；邮件渠道返回新建邮箱地址（键如 {@code email}）。</p>
     *
     * @return 身份 Map；领取失败返回 {@code null}
     * @throws Exception 领取失败
     */
    Map<String, Object> acquire() throws Exception;

    /**
     * 拉取该身份收到的消息。
     *
     * @param identity {@link #acquire()} 返回的身份 Map
     * @return 消息列表（每项为不透明 Map，短信含 {@code from} / {@code content} / {@code time}，
     *         邮件含 {@code id} / {@code from} / {@code subject} / {@code body} / {@code createdAt}）；
     *         拉取失败返回空列表
     * @throws Exception 拉取失败
     */
    List<Map<String, Object>> fetchMessages(Map<String, Object> identity) throws Exception;

    /**
     * 临时账号能力固定只支持 {@link AccountType#TEMPORARY}。
     *
     * @return 仅含 {@code TEMPORARY} 的集合
     */
    @Override
    default Set<AccountType> accountTypes() {
        return Set.of(AccountType.TEMPORARY);
    }
}

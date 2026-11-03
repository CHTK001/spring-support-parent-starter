package com.chua.starter.channel.spi.impl;

import com.chua.common.support.datasearch.sms.model.SmsMessageInfo;
import com.chua.common.support.datasearch.sms.model.SmsPhoneInfo;
import com.chua.common.support.datasearch.sms.spi.SmsCodeProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.channel.spi.ChannelKind;
import com.chua.starter.channel.spi.TemporaryAccountProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信接码渠道适配器 —— 把一个底层接码数据源包装成一个 {@link ChannelKind#SMS} 渠道。
 *
 * <p>本适配器绑定 FreePhoneNum（{@code freephonenum}）：无 Cloudflare 人机验证，
 * HTTP 直连即可读取真实短信，是当前网络环境下的默认接码源。其余接码源
 * （如 {@code jiemahao}）按同样方式另写一个适配器即可，无需改动编排层。</p>
 *
 * <p>身份载荷：{@code { phone: SmsPhoneInfo, provider: 数据源名 }}；
 * 消息载荷键与前端 {@code TempSmsMessage} 契约一致：
 * {@code phone / from / content / time / verifyCode}。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("sms-freephonenum")
public class SmsChannelAdapter implements TemporaryAccountProvider {

    /**
     * 底层接码数据源名称（{@link SmsCodeProvider#name()}）
     */
    private static final String DELEGATE = "freephonenum";

    /**
     * 渠道标识。
     *
     * @return {@code sms-freephonenum}
     */
    @Override
    public String channel() {
        return "sms-freephonenum";
    }

    /**
     * 渠道展示名称。
     *
     * @return 展示名称
     */
    @Override
    public String displayName() {
        return "接码 - FreePhoneNum";
    }

    /**
     * 渠道种类。
     *
     * @return {@link ChannelKind#SMS}
     */
    @Override
    public ChannelKind kind() {
        return ChannelKind.SMS;
    }

    /**
     * 进入优先级：当前唯一可直连接码源，置为最高档（100）。
     *
     * <p>新增接码源时按稳定性给出更低分值，{@code ChannelRegistry} 会按优先级降序
     * 取第一个作为默认接码渠道。</p>
     *
     * @return {@code 100}
     */
    @Override
    public int priority() {
        return 100;
    }

    /**
     * 领取一个随机虚拟号码。
     *
     * @return 身份载荷；数据源不可用或无可选号码时返回 {@code null}
     */
    @Override
    public Map<String, Object> acquire() {
        SmsCodeProvider delegate = delegate();
        if (delegate == null) {
            return null;
        }
        SmsPhoneInfo phone = delegate.randomPhone();
        if (phone == null) {
            return null;
        }
        Map<String, Object> identity = new LinkedHashMap<>(4);
        identity.put("phone", phone);
        identity.put("provider", delegate.name());
        return identity;
    }

    /**
     * 拉取该号码收到的短信。
     *
     * @param identity {@link #acquire()} 返回的身份载荷
     * @return 短信载荷列表（可能为空）
     */
    @Override
    public List<Map<String, Object>> fetchMessages(Map<String, Object> identity) {
        SmsCodeProvider delegate = delegate();
        if (delegate == null || identity == null || !(identity.get("phone") instanceof SmsPhoneInfo phone)) {
            return List.of();
        }
        List<SmsMessageInfo> messages = delegate.fetchMessages(phone);
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(messages.size());
        for (SmsMessageInfo m : messages) {
            Map<String, Object> item = new LinkedHashMap<>(6);
            item.put("phone", m.getPhone());
            item.put("from", m.getFrom());
            item.put("content", m.getContent());
            item.put("time", m.getTime());
            item.put("verifyCode", m.getVerifyCode());
            out.add(item);
        }
        return out;
    }

    /**
     * 按名称取底层接码数据源。
     *
     * @return 数据源；未注册时返回 {@code null}
     */
    private SmsCodeProvider delegate() {
        return ServiceProvider.of(SmsCodeProvider.class).getExtension(DELEGATE);
    }
}

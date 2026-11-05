// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.channel.spi.impl;

import com.chua.common.support.datasearch.email.model.EmailInfo;
import com.chua.common.support.datasearch.email.spi.EmailProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.channel.spi.ChannelKind;
import com.chua.starter.channel.spi.TemporaryAccountProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件收信渠道适配器 —— 把一个底层临时邮箱数据源包装成一个 {@link ChannelKind#EMAIL} 渠道。
 *
 * <p>本适配器绑定 mail.tm（{@code mail-tm}）：免 API Key，是当前默认收信源。
 * 其余邮箱源（{@code fce} / {@code mailnest} / {@code luckmail}）按同样方式另写适配器即可，
 * 无需改动编排层。</p>
 *
 * <p>身份载荷：{@code { email: 邮箱地址, provider: 数据源名 }}；
 * 消息载荷键与前端 {@code TempEmailMessage} 契约一致：
 * {@code id / from / subject / body / html / createdAt}。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("email-mail-tm")
public class EmailChannelAdapter implements TemporaryAccountProvider {

    /**
     * 底层邮件数据源名称（{@link EmailProvider#name()}）
     */
    private static final String DELEGATE = "mail-tm";

    /**
     * 渠道标识。
     *
     * @return {@code email-mail-tm}
     */
    @Override
    public String channel() {
        return "email-mail-tm";
    }

    /**
     * 渠道展示名称。
     *
     * @return 展示名称
     */
    @Override
    public String displayName() {
        return "收信 - mail.tm";
    }

    /**
     * 渠道种类。
     *
     * @return {@link ChannelKind#EMAIL}
     */
    @Override
    public ChannelKind kind() {
        return ChannelKind.EMAIL;
    }

    /**
     * 进入优先级：当前默认收信源，置为最高档（100）。
     *
     * <p>后续新增邮箱源（fce / mailnest / luckmail）按稳定性给出更低分值，
     * {@code ChannelRegistry} 会按优先级降序取第一个作为默认收信渠道；
     * 也可由前端在创建临时身份时显式指定渠道，实现「指定优先 + 降级」。</p>
     *
     * @return {@code 100}
     */
    @Override
    public int priority() {
        return 100;
    }

    /**
     * 创建一个临时邮箱。
     *
     * @return 身份载荷；数据源不可用或创建失败时返回 {@code null}
     */
    @Override
    public Map<String, Object> acquire() {
        EmailProvider delegate = delegate();
        if (delegate == null) {
            return null;
        }
        String email = delegate.createEmail();
        if (email == null || email.isBlank()) {
            return null;
        }
        Map<String, Object> identity = new LinkedHashMap<>(4);
        identity.put("email", email);
        identity.put("provider", delegate.name());
        return identity;
    }

    /**
     * 拉取该邮箱收到的邮件。
     *
     * @param identity {@link #acquire()} 返回的身份载荷
     * @return 邮件载荷列表（可能为空）
     */
    @Override
    public List<Map<String, Object>> fetchMessages(Map<String, Object> identity) {
        EmailProvider delegate = delegate();
        if (delegate == null || identity == null || !(identity.get("email") instanceof String email)) {
            return List.of();
        }
        List<EmailInfo> emails = delegate.fetchEmails(email);
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(emails.size());
        for (EmailInfo e : emails) {
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("id", e.getId());
            item.put("from", e.getFrom());
            item.put("subject", e.getSubject());
            item.put("body", e.getBody());
            item.put("html", e.getHtml());
            item.put("createdAt", e.getCreatedAt());
            out.add(item);
        }
        return out;
    }

    /**
     * 按名称取底层邮件数据源。
     *
     * @return 数据源；未注册时返回 {@code null}
     */
    private EmailProvider delegate() {
        return ServiceProvider.of(EmailProvider.class).getExtension(DELEGATE);
    }
}
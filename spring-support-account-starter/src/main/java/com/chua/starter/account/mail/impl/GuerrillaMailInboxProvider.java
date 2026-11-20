package com.chua.starter.account.mail.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.mail.MailboxFolder;
import com.chua.starter.account.mail.MailboxMessage;
import com.chua.starter.account.mail.MailboxQuery;
import com.chua.starter.account.mail.temp.GuerrillaMailClient;
import com.chua.starter.account.mail.temp.GuerrillaMailService;
import com.chua.starter.account.mail.temp.TempMailService;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.spi.AccountInboxProvider;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * GuerrillaMail 临时邮箱收信实现。
 *
 * <p>只读来源：GuerrillaMail AJAX 接口没有已读回写与删除邮件的能力，
 * {@link #supportsMarkSeen()} / {@link #supportsDelete()} 保持默认 {@code false}，
 * 界面会禁用对应按钮而不是假装成功。</p>
 *
 * <p>不保存会话令牌：每次取信先建匿名会话，再用地址前缀
 * {@code set_email_user} 恢复同一邮箱；{@code secret} 传空即可。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
@Spi("guerrilla-mail")
public class GuerrillaMailInboxProvider implements AccountInboxProvider {

    /**
     * 收件箱服务端名
     */
    private static final String INBOX = "INBOX";

    /**
     * GuerrillaMail 客户端
     */
    private final GuerrillaMailClient client = new GuerrillaMailClient();

    /**
     * {@inheritDoc}
     */
    @Override
    public String provider() {
        return GuerrillaMailService.PROVIDER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> accountKinds() {
        return Set.of("EMAIL");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(SysAccount account) {
        // 多供应商后各实现只认自己的 tempProvider 标记，避免互相抢占
        return TempMailService.isTempMail(account)
                && GuerrillaMailService.PROVIDER.equalsIgnoreCase(TempMailService.providerOf(account));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MailboxFolder> folders(SysAccount account, String secret) throws Exception {
        MailboxFolder folder = new MailboxFolder();
        folder.setKey(INBOX);
        folder.setName("收件箱");
        folder.setRole(INBOX);
        folder.setTotal(client.checkEmail(sidToken(account)).size());
        folder.setUnread(0);
        return List.of(folder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MailboxMessage> messages(SysAccount account, String secret, MailboxQuery query) throws Exception {
        List<JsonNode> items = client.checkEmail(sidToken(account));
        List<MailboxMessage> result = new ArrayList<>();
        Long since = query == null ? null : query.getSince();
        String keyword = query == null ? null : StringUtils.trimToNull(query.getKeyword());
        for (JsonNode node : items) {
            if (query != null && Boolean.TRUE.equals(query.getUnreadOnly())
                    && node.path("mail_read").asInt(0) != 0) {
                continue;
            }
            if (since != null) {
                Long sentAt = sentAtOf(node);
                if (sentAt == null || sentAt <= since) {
                    continue;
                }
            }
            if (keyword != null && !matches(node, keyword)) {
                continue;
            }
            result.add(toSummary(node));
        }
        int limit = query == null || query.getLimit() == null ? 0 : query.getLimit();
        if (limit > 0 && result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MailboxMessage message(SysAccount account, String secret, String folder, String messageId)
            throws Exception {
        for (JsonNode node : client.checkEmail(sidToken(account))) {
            if (!messageId.equals(node.path("mail_id").asText())) {
                continue;
            }
            MailboxMessage message = toSummary(node);
            message.setContent(node.path("mail_body").asText(""));
            message.setContentType("text");
            return message;
        }
        return null;
    }

    /**
     * 恢复会话：建匿名会话后按地址前缀绑定回原邮箱。
     *
     * @param account 账号
     * @return sid_token
     */
    private String sidToken(SysAccount account) {
        GuerrillaMailClient.Session session = client.createSession();
        client.bindUser(session.getSidToken(), localPart(account));
        return session.getSidToken();
    }

    /**
     * 地址前缀：优先取领取时存的 {@code config.tempLocal}，缺省从地址解析。
     *
     * @param account 账号
     * @return 地址前缀
     */
    private String localPart(SysAccount account) {
        String local = TempMailService.configString(account, "tempLocal");
        if (StringUtils.isNotBlank(local)) {
            return local;
        }
        String address = StringUtils.defaultString(account.getUsername(), "");
        int at = address.indexOf('@');
        if (at <= 0) {
            throw new IllegalStateException("guerrillamail 账号缺少邮箱地址");
        }
        return address.substring(0, at);
    }

    /**
     * 列表节点转视图。
     *
     * @param node GuerrillaMail 邮件节点
     * @return 消息
     */
    private MailboxMessage toSummary(JsonNode node) {
        MailboxMessage message = new MailboxMessage();
        message.setId(node.path("mail_id").asText(""));
        message.setFolder(INBOX);
        message.setSubject(StringUtils.defaultString(node.path("mail_subject").asText(null), "(无主题)"));
        message.setFrom(StringUtils.defaultString(node.path("mail_from").asText(null), ""));
        message.setTo(StringUtils.defaultString(node.path("mail_recipient").asText(null), ""));
        message.setSentAt(sentAtOf(node));
        message.setPreview(StringUtils.defaultString(node.path("mail_excerpt").asText(null), ""));
        message.setUnread(node.path("mail_read").asInt(0) == 0);
        message.setHasAttachment(node.path("att").asInt(0) > 0);
        message.setAttachments(new ArrayList<>());
        return message;
    }

    /**
     * 邮件时间：{@code mail_timestamp} 为秒级 Unix 时间（欢迎邮件为 0）。
     *
     * @param node 邮件节点
     * @return 毫秒；缺失返回 {@code null}
     */
    private Long sentAtOf(JsonNode node) {
        long seconds = node.path("mail_timestamp").asLong(0);
        return seconds > 0 ? seconds * 1000L : null;
    }

    /**
     * 关键字匹配主题 / 发件人 / 摘要。
     *
     * @param node    GuerrillaMail 节点
     * @param keyword 关键字
     * @return 命中返回 {@code true}
     */
    private boolean matches(JsonNode node, String keyword) {
        String needle = keyword.toLowerCase(Locale.ROOT);
        String haystack = (node.path("mail_subject").asText("") + " "
                + node.path("mail_from").asText("") + " "
                + node.path("mail_excerpt").asText("")).toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }
}

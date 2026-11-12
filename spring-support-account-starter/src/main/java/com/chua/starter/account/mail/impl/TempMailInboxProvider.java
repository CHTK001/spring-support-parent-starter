package com.chua.starter.account.mail.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.mail.MailboxFolder;
import com.chua.starter.account.mail.MailboxMessage;
import com.chua.starter.account.mail.MailboxQuery;
import com.chua.starter.account.mail.temp.TempMailClient;
import com.chua.starter.account.mail.temp.TempMailService;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.spi.AccountInboxProvider;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 临时邮箱收信实现（mail.tm）。
 *
 * <p>只读来源：mail.tm 没有已读回写与删除接口，因此
 * {@link #supportsMarkSeen()} / {@link #supportsDelete()} 保持默认 {@code false}，
 * 界面会禁用对应按钮而不是假装成功。</p>
 *
 * <p>token 不落库：每次按 {@code username + secret} 现换，避免临时邮箱
 * 过期后还持有一份无效令牌。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Spi("temp-mail")
public class TempMailInboxProvider implements AccountInboxProvider {

    /**
     * 收件箱服务端名
     */
    private static final String INBOX = "INBOX";

    /**
     * mail.tm 客户端
     */
    private final TempMailClient client = new TempMailClient();

    /**
     * {@inheritDoc}
     */
    @Override
    public String provider() {
        return TempMailService.PROVIDER;
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
        return TempMailService.isTempMail(account);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MailboxFolder> folders(SysAccount account, String secret) throws Exception {
        TempMailClient.Page page = client.listMessages(token(account, secret), 1);
        MailboxFolder folder = new MailboxFolder();
        folder.setKey(INBOX);
        folder.setName("收件箱");
        folder.setRole("INBOX");
        folder.setTotal(page.getTotal());
        folder.setUnread(0);
        return List.of(folder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MailboxMessage> messages(SysAccount account, String secret, MailboxQuery query) throws Exception {
        int pageNo = query == null || query.getOffset() == null ? 1 : query.getOffset() / 20 + 1;
        TempMailClient.Page page = client.listMessages(token(account, secret), pageNo);
        List<MailboxMessage> result = new ArrayList<>();
        for (JsonNode node : page.getItems()) {
            if (query != null && Boolean.TRUE.equals(query.getUnreadOnly()) && node.path("seen").asBoolean(false)) {
                continue;
            }
            String keyword = query == null ? null : StringUtils.trimToNull(query.getKeyword());
            if (keyword != null && !matches(node, keyword)) {
                continue;
            }
            result.add(toSummary(node));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MailboxMessage message(SysAccount account, String secret, String folder, String messageId)
            throws Exception {
        JsonNode node = client.message(token(account, secret), messageId);
        if (node == null) {
            return null;
        }
        MailboxMessage message = toSummary(node);
        String text = node.path("text").asText(null);
        String html = firstHtml(node.path("html"));
        if (StringUtils.isNotBlank(html)) {
            message.setContent(html);
            message.setContentType("html");
        } else {
            message.setContent(StringUtils.defaultString(text, ""));
            message.setContentType("text");
        }
        return message;
    }

    /**
     * 用账号密码换 token。
     *
     * @param account 账号
     * @param secret  明文密码
     * @return token
     */
    private String token(SysAccount account, String secret) {
        String address = account.getUsername();
        if (StringUtils.isBlank(address) || StringUtils.isBlank(secret)) {
            throw new IllegalStateException("临时邮箱缺少地址或密码");
        }
        return client.token(address, secret);
    }

    /**
     * 列表节点转视图。
     *
     * @param node mail.tm 邮件节点
     * @return 消息
     */
    private MailboxMessage toSummary(JsonNode node) {
        MailboxMessage message = new MailboxMessage();
        message.setId(node.path("id").asText(""));
        message.setFolder(INBOX);
        message.setSubject(StringUtils.defaultString(node.path("subject").asText(null), "(无主题)"));
        message.setFrom(fromText(node.path("from")));
        message.setTo(collectionText(node.path("to")));
        message.setCc(collectionText(node.path("cc")));
        message.setSentAt(parseTime(node.path("createdAt").asText(null)));
        String intro = node.path("intro").asText(null);
        message.setPreview(StringUtils.defaultString(intro, ""));
        message.setUnread(!node.path("seen").asBoolean(false));
        message.setHasAttachment(node.path("hasAttachments").asBoolean(false));
        List<String> attachments = new ArrayList<>();
        for (JsonNode item : node.path("attachments")) {
            String filename = item.path("filename").asText(null);
            if (StringUtils.isNotBlank(filename)) {
                attachments.add(filename);
            }
        }
        message.setAttachments(attachments);
        return message;
    }

    /**
     * 发件人展示。
     *
     * @param from 发件人节点
     * @return 展示文本
     */
    private String fromText(JsonNode from) {
        String address = from.path("address").asText("");
        String name = from.path("name").asText("");
        return StringUtils.isBlank(name) ? address : name + " <" + address + ">";
    }

    /**
     * 收件人数组转展示文本。
     *
     * @param array 收件人数组
     * @return 逗号分隔地址
     */
    private String collectionText(JsonNode array) {
        List<String> parts = new ArrayList<>();
        for (JsonNode item : array) {
            String address = item.path("address").asText(null);
            if (StringUtils.isNotBlank(address)) {
                parts.add(address);
            }
        }
        return String.join(", ", parts);
    }

    /**
     * 取第一段 HTML 正文。
     *
     * @param html html 数组
     * @return 第一段；没有返回 {@code null}
     */
    private String firstHtml(JsonNode html) {
        for (JsonNode item : html) {
            String text = item.asText(null);
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    /**
     * 关键字匹配主题 / 发件人 / 摘要。
     *
     * @param node    mail.tm 节点
     * @param keyword 关键字
     * @return 命中返回 {@code true}
     */
    private boolean matches(JsonNode node, String keyword) {
        String needle = keyword.toLowerCase(Locale.ROOT);
        String haystack = (node.path("subject").asText("") + " "
                + node.path("intro").asText("") + " "
                + fromText(node.path("from"))).toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }

    /**
     * ISO-8601 时间转毫秒。
     *
     * @param text 时间串
     * @return 毫秒；解析失败返回 {@code null}
     */
    private Long parseTime(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }
}

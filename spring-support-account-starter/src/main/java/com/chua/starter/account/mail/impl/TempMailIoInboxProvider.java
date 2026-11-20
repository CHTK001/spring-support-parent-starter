package com.chua.starter.account.mail.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.mail.MailboxFolder;
import com.chua.starter.account.mail.MailboxMessage;
import com.chua.starter.account.mail.MailboxQuery;
import com.chua.starter.account.mail.temp.TempMailIoService;
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
 * temp-mail.io 临时邮箱收信实现。
 *
 * <p>只读来源：v3 内部接口没有已读回写与单封删除能力，
 * {@link #supportsMarkSeen()} / {@link #supportsDelete()} 保持默认 {@code false}，
 * 界面会禁用对应按钮而不是假装成功。</p>
 *
 * <p>取信免鉴权（按邮箱地址定位），因此 {@code secret} 传空也能读；
 * token 只在删除整个邮箱时使用。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
@Spi("temp-mail-io")
public class TempMailIoInboxProvider implements AccountInboxProvider {

    /**
     * 收件箱服务端名
     */
    private static final String INBOX = "INBOX";

    /**
     * temp-mail.io 客户端
     */
    private final com.chua.starter.account.mail.temp.TempMailIoClient client =
            new com.chua.starter.account.mail.temp.TempMailIoClient();

    /**
     * {@inheritDoc}
     */
    @Override
    public String provider() {
        return TempMailIoService.PROVIDER;
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
                && TempMailIoService.PROVIDER.equalsIgnoreCase(TempMailService.providerOf(account));
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
        folder.setTotal(client.messages(account.getUsername()).size());
        folder.setUnread(0);
        return List.of(folder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MailboxMessage> messages(SysAccount account, String secret, MailboxQuery query) throws Exception {
        List<JsonNode> items = client.messages(account.getUsername());
        List<MailboxMessage> result = new ArrayList<>();
        Long since = query == null ? null : query.getSince();
        String keyword = query == null ? null : StringUtils.trimToNull(query.getKeyword());
        for (JsonNode node : items) {
            if (since != null) {
                Long createdAt = parseTime(node.path("created_at").asText(null));
                if (createdAt == null || createdAt <= since) {
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
        for (JsonNode node : client.messages(account.getUsername())) {
            if (!messageId.equals(node.path("id").asText())) {
                continue;
            }
            MailboxMessage message = toSummary(node);
            String html = node.path("body_html").asText(null);
            String text = node.path("body_text").asText(null);
            if (StringUtils.isNotBlank(html)) {
                message.setContent(html);
                message.setContentType("html");
            } else {
                message.setContent(StringUtils.defaultString(text, ""));
                message.setContentType("text");
            }
            return message;
        }
        return null;
    }

    /**
     * 列表节点转视图。
     *
     * @param node temp-mail.io 邮件节点
     * @return 消息
     */
    private MailboxMessage toSummary(JsonNode node) {
        MailboxMessage message = new MailboxMessage();
        message.setId(node.path("id").asText(""));
        message.setFolder(INBOX);
        message.setSubject(StringUtils.defaultString(node.path("subject").asText(null), "(无主题)"));
        message.setFrom(addressText(node.path("from")));
        message.setTo(addressText(node.path("to")));
        message.setSentAt(parseTime(node.path("created_at").asText(null)));
        // 列表接口直接带正文，摘要截前 120 字，避免整页正文进列表
        String preview = StringUtils.defaultString(node.path("body_text").asText(null), "");
        // [P3C A20 豁免]
        message.setPreview(preview.length() > 120 ? preview.substring(0, 120) : preview);
        message.setUnread(true);
        List<String> attachments = new ArrayList<>();
        for (JsonNode item : node.path("attachments")) {
            String name = item.path("name").asText(null);
            if (StringUtils.isNotBlank(name)) {
                attachments.add(name);
            }
        }
        message.setAttachments(attachments);
        message.setHasAttachment(!attachments.isEmpty());
        return message;
    }

    /**
     * 发件人 / 收件人字段转展示文本（v3 该字段可能是字符串、对象或数组）。
     *
     * @param node 字段节点
     * @return 展示文本
     */
    private String addressText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : node) {
                String address = addressText(item);
                if (StringUtils.isNotBlank(address)) {
                    parts.add(address);
                }
            }
            return String.join(", ", parts);
        }
        String name = node.path("name").asText("");
        String address = node.path("address").asText("");
        if (StringUtils.isBlank(name)) {
            return address;
        }
        return name + " <" + address + ">";
    }

    /**
     * 关键字匹配主题 / 发件人 / 正文。
     *
     * @param node    temp-mail.io 节点
     * @param keyword 关键字
     * @return 命中返回 {@code true}
     */
    private boolean matches(JsonNode node, String keyword) {
        String needle = keyword.toLowerCase(Locale.ROOT);
        String haystack = (node.path("subject").asText("") + " "
                + node.path("from").asText("") + " "
                + node.path("body_text").asText("")).toLowerCase(Locale.ROOT);
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

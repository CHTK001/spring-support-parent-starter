package com.chua.starter.account.phone;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.mail.MailboxMessage;
import com.chua.starter.account.mail.MailboxQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 临时手机号短信暂存库（进程内单例，非 Spring Bean）。
 *
 * <p>收信 SPI 实现不走 Spring 装配（先例：{@code TempMailInboxProvider}），
 * 而目录轮询器是 Spring Bean，两边都通过 {@link #get()} 拿同一份实例。
 * 数据只存内存：短信是短时效数据，重启清空即可，落库反而引入清理负担。</p>
 *
 * <p>按号码分桶，新短信插到桶首（最新在前）；每桶上限 {@value #MAX_PER_PHONE}
 * 条，超出丢最旧的。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
public final class TempPhoneSmsStore {

    /**
     * 每个号码最多保留多少条短信
     */
    private static final int MAX_PER_PHONE = 200;

    /**
     * 摘要截断长度（列表只给 preview，不拖全文）
     */
    private static final int PREVIEW_LENGTH = 120;

    /**
     * 单例
     */
    private static final TempPhoneSmsStore INSTANCE = new TempPhoneSmsStore();

    /**
     * 号码 -> 短信列表（最新在前）
     */
    private final Map<String, List<MailboxMessage>> inbox = new ConcurrentHashMap<>();

    /**
     * 消息标识发号器
     */
    private final AtomicLong seq = new AtomicLong();

    private TempPhoneSmsStore() {
    }

    /**
     * 取共享实例。
     *
     * @return 暂存库
     */
    public static TempPhoneSmsStore get() {
        return INSTANCE;
    }

    /**
     * 追加一条短信（新短信排最前，超限丢最旧）。
     *
     * @param phone   收件手机号
     * @param from    发件方（号码或签名）
     * @param content 短信正文
     * @param sentAt  发送时间（毫秒）；空取当前时间
     * @return 落库后的消息视图
     */
    public MailboxMessage append(String phone, String from, String content, Long sentAt) {
        String body = StringUtils.defaultString(content, "");
        MailboxMessage message = new MailboxMessage();
        message.setId("sms-" + seq.incrementAndGet());
        message.setFolder("INBOX");
        message.setFrom(StringUtils.defaultString(from, "(未知发件方)"));
        message.setTo(phone);
        message.setSubject(message.getFrom());
        message.setSentAt(sentAt == null ? System.currentTimeMillis() : sentAt);
        message.setPreview(body.length() > PREVIEW_LENGTH ? body.substring(0, PREVIEW_LENGTH) : body);
        message.setContent(body);
        message.setContentType("text");
        message.setUnread(Boolean.FALSE);
        message.setHasAttachment(Boolean.FALSE);
        List<MailboxMessage> bucket = inbox.computeIfAbsent(phone, key -> new CopyOnWriteArrayList<>());
        bucket.add(0, message);
        if (bucket.size() > MAX_PER_PHONE) {
            bucket.remove(bucket.size() - 1);
        }
        return message;
    }

    /**
     * 按查询条件取短信列表（新的在前；列表不带正文，只给摘要）。
     *
     * @param phone 手机号
     * @param query 取信条件；空取默认第一页
     * @return 消息列表
     */
    public List<MailboxMessage> messages(String phone, MailboxQuery query) {
        List<MailboxMessage> bucket = inbox.get(phone);
        if (bucket == null || bucket.isEmpty()) {
            return List.of();
        }
        int limit = query == null || query.getLimit() == null ? 20 : Math.max(1, query.getLimit());
        int offset = query == null || query.getOffset() == null ? 0 : Math.max(0, query.getOffset());
        boolean unreadOnly = query != null && Boolean.TRUE.equals(query.getUnreadOnly());
        Long since = query == null ? null : query.getSince();
        String keyword = query == null ? null : StringUtils.trimToNull(query.getKeyword());
        List<MailboxMessage> result = new ArrayList<>();
        for (MailboxMessage message : bucket) {
            if (unreadOnly && !Boolean.TRUE.equals(message.getUnread())) {
                continue;
            }
            if (since != null && message.getSentAt() != null && message.getSentAt() < since) {
                continue;
            }
            if (keyword != null && !matches(message, keyword)) {
                continue;
            }
            if (offset > 0) {
                offset--;
                continue;
            }
            result.add(summary(message));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    /**
     * 取单条短信详情（含正文）。
     *
     * @param phone     手机号
     * @param messageId 消息标识
     * @return 消息详情；不存在返回 {@code null}
     */
    public MailboxMessage message(String phone, String messageId) {
        List<MailboxMessage> bucket = inbox.get(phone);
        if (bucket == null || StringUtils.isBlank(messageId)) {
            return null;
        }
        for (MailboxMessage message : bucket) {
            if (messageId.equals(message.getId())) {
                return copy(message, true);
            }
        }
        return null;
    }

    /**
     * 号码当前短信总数。
     *
     * @param phone 手机号
     * @return 短信数
     */
    public int count(String phone) {
        List<MailboxMessage> bucket = inbox.get(phone);
        return bucket == null ? 0 : bucket.size();
    }

    /**
     * 列表视图：正文置空，只带摘要（与信箱契约一致）。
     *
     * @param message 原始消息
     * @return 列表视图
     */
    private MailboxMessage summary(MailboxMessage message) {
        return copy(message, false);
    }

    /**
     * 复制消息视图。
     *
     * @param message   原始消息
     * @param withContent 是否带正文
     * @return 副本
     */
    private MailboxMessage copy(MailboxMessage message, boolean withContent) {
        MailboxMessage view = new MailboxMessage();
        view.setId(message.getId());
        view.setFolder(message.getFolder());
        view.setSubject(message.getSubject());
        view.setFrom(message.getFrom());
        view.setTo(message.getTo());
        view.setSentAt(message.getSentAt());
        view.setPreview(message.getPreview());
        view.setContent(withContent ? message.getContent() : "");
        view.setContentType(message.getContentType());
        view.setUnread(message.getUnread());
        view.setHasAttachment(message.getHasAttachment());
        return view;
    }

    /**
     * 关键字匹配发件方 / 正文。
     *
     * @param message 消息
     * @param keyword 关键字
     * @return 命中返回 {@code true}
     */
    private boolean matches(MailboxMessage message, String keyword) {
        String needle = keyword.toLowerCase(Locale.ROOT);
        String haystack = (message.getFrom() + " " + StringUtils.defaultString(message.getContent(), ""))
                .toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }
}

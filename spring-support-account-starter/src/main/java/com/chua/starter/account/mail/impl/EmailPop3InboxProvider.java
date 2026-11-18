package com.chua.starter.account.mail.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.mail.MailboxFolder;
import com.chua.starter.account.mail.MailboxMessage;
import com.chua.starter.account.mail.MailboxQuery;
import com.chua.starter.account.mail.temp.TempMailService;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.spi.AccountInboxProvider;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 邮件收信实现（POP3 / POP3S）。
 *
 * <p>与 {@link EmailInboxProvider} 的 IMAP 实现互斥：仅当 {@code config.inboxMode}
 * 为 {@code pop3} 时才接管账号，避免两个实现对同一 EMAIL 账号抢占。</p>
 *
 * <p>POP3 没有文件夹、UID 与已读/删除标记的持久化，因此：
 * <ul>
 *   <li>{@link #folders} 只返回单一 {@code INBOX}；</li>
 *   <li>{@link MailboxMessage#getId()} 用消息序号，仅在当前连接内稳定；</li>
 *   <li>{@link #supportsMarkSeen()} / {@link #supportsDelete()} 均为 {@code false}，界面据此禁用按钮；</li>
 *   <li>{@code since} 过滤在内存按 {@link Message#getSentDate()} 完成，无服务端日期搜索。</li>
 * </ul></p>
 *
 * <p>主机与端口的解析顺序：{@code config.pop3Host} → 由 {@code endpoint}
 * 推导（{@code smtp.163.com} → {@code pop.163.com}）；端口：{@code config.pop3Port}
 * → SSL 用 995，否则用 110。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Spi("email-pop3")
public class EmailPop3InboxProvider implements AccountInboxProvider {

    /**
     * 支持的账号种类
     */
    private static final String KIND_EMAIL = "EMAIL";

    /**
     * 收件箱服务端名（POP3 仅有一个收件箱）
     */
    private static final String INBOX = "INBOX";

    /**
     * POP3S 默认端口
     */
    private static final int POP3S_PORT = 995;

    /**
     * POP3 默认端口
     */
    private static final int POP3_PORT = 110;

    /**
     * 单次最多取多少封
     */
    private static final int MAX_LIMIT = 100;

    /**
     * 建连超时（毫秒）
     */
    private static final int CONNECT_TIMEOUT = 8000;

    /**
     * 读超时（毫秒）
     */
    private static final int READ_TIMEOUT = 20000;

    /**
     * 列表摘要长度
     */
    private static final int PREVIEW_LENGTH = 160;

    /**
     * 默认取信条数
     */
    private static final int DEFAULT_LIMIT = 20;

    /**
     * 端口取值下界（不含，即最小合法端口）
     */
    private static final int PORT_MIN = 1;

    /**
     * 端口取值上界（含）
     */
    private static final int PORT_MAX = 65535;

    /**
     * HTML 标签
     */
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    /**
     * 连续空白
     */
    private static final Pattern BLANK = Pattern.compile("\\s+");

    /**
     * {@inheritDoc}
     */
    @Override
    public String provider() {
        return "email-pop3";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> accountKinds() {
        return Set.of(KIND_EMAIL);
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅 POP3 接入方式且主机可解析时才接管，与 IMAP 实现互斥。</p>
     */
    @Override
    public boolean supports(SysAccount account) {
        return account != null
                && !TempMailService.isTempMail(account)
                && KIND_EMAIL.equalsIgnoreCase(account.getKind())
                && "pop3".equals(EmailInboxProvider.configString(account, "inboxMode"))
                && StringUtils.isNotBlank(account.getUsername())
                && StringUtils.isNotBlank(pop3Host(account));
    }

    /**
     * {@inheritDoc}
     *
     * <p>POP3 仅有一个 {@code INBOX}，返回单一文件夹视图。</p>
     */
    @Override
    public List<MailboxFolder> folders(SysAccount account, String secret) throws Exception {
        Store store = open(account, secret);
        Folder inbox = null;
        try {
            List<MailboxFolder> result = new ArrayList<>();
            inbox = store.getFolder(INBOX);
            if (inbox != null && inbox.exists()) {
                inbox.open(Folder.READ_ONLY);
                MailboxFolder view = new MailboxFolder();
                view.setKey(inbox.getFullName());
                view.setName("收件箱");
                view.setRole("INBOX");
                try {
                    view.setTotal(inbox.getMessageCount());
                    view.setUnread(inbox.getUnreadMessageCount());
                } catch (MessagingException e) {
                    view.setTotal(0);
                    view.setUnread(0);
                }
                result.add(view);
            }
            return result;
        } finally {
            close(inbox);
            close(store);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>POP3 无服务端日期搜索，{@link MailboxQuery#getSince()} 在内存按
     * {@link Message#getSentDate()} 过滤；无 UID，按消息序号倒序排列。</p>
     */
    @Override
    public List<MailboxMessage> messages(SysAccount account, String secret, MailboxQuery query) throws Exception {
        MailboxQuery effective = query == null ? new MailboxQuery() : query;
        String folderName = StringUtils.isBlank(effective.getFolder()) ? INBOX : effective.getFolder().trim();
        int limit = clampLimit(effective.getLimit());
        int offset = effective.getOffset() == null ? 0 : Math.max(0, effective.getOffset());
        String keyword = StringUtils.trimToNull(effective.getKeyword());
        Long since = effective.getSince();
        boolean unreadOnly = Boolean.TRUE.equals(effective.getUnreadOnly());

        Store store = open(account, secret);
        Folder folder = null;
        try {
            folder = store.getFolder(folderName);
            if (folder == null || !folder.exists()) {
                throw new MessagingException("邮箱文件夹不存在: " + folderName);
            }
            folder.open(Folder.READ_ONLY);
            Message[] all = folder.getMessages();
            List<Message> picked = new ArrayList<>(all.length);
            for (Message msg : all) {
                if (since != null) {
                    Date sent = msg.getSentDate();
                    if (sent == null || sent.getTime() < since) {
                        continue;
                    }
                }
                if (unreadOnly && msg.isSet(Flags.Flag.SEEN)) {
                    continue;
                }
                picked.add(msg);
            }
            // POP3 无服务端排序，按消息序号倒序（最新在前），序号在会话内稳定
            picked.sort((left, right) -> Integer.compare(messageNumber(right), messageNumber(left)));
            int from = Math.min(offset, picked.size());
            int to = Math.min(from + limit, picked.size());
            List<MailboxMessage> result = new ArrayList<>();
            for (int i = from; i < to; i++) {
                Message msg = picked.get(i);
                MailboxMessage view = toView(msg, folder, false);
                if (keyword != null && !matches(view, keyword)) {
                    continue;
                }
                result.add(view);
            }
            return result;
        } finally {
            close(folder);
            close(store);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>POP3 无 UID，按消息序号定位单封邮件。</p>
     */
    @Override
    public MailboxMessage message(SysAccount account, String secret, String folder, String messageId) throws Exception {
        String folderName = StringUtils.isBlank(folder) ? INBOX : folder.trim();
        String id = StringUtils.trimToNull(messageId);
        if (id == null) {
            return null;
        }
        Store store = open(account, secret);
        Folder current = null;
        try {
            current = store.getFolder(folderName);
            if (current == null || !current.exists()) {
                return null;
            }
            current.open(Folder.READ_ONLY);
            int number;
            try {
                number = Integer.parseInt(id);
            } catch (NumberFormatException e) {
                return null;
            }
            if (number < 1 || number > current.getMessageCount()) {
                return null;
            }
            Message msg = current.getMessage(number);
            return msg == null ? null : toView(msg, current, true);
        } finally {
            close(current);
            close(store);
        }
    }

    /**
     * 打开 Store。
     *
     * @param account 账号
     * @param secret  明文凭据
     * @return 已连接的 Store（调用方负责关闭）
     * @throws MessagingException 连接失败
     */
    private Store open(SysAccount account, String secret) throws MessagingException {
        return openStore(account, secret);
    }

    /**
     * 建立 POP3 / POP3S 连接。
     *
     * <p>收信与「账号凭据校验」共用同一套主机 / 端口 / 加密推导。</p>
     *
     * @param account 账号
     * @param secret  明文凭据
     * @return 已连接的 Store（调用方负责关闭）
     * @throws MessagingException 连接失败
     */
    static Store openStore(SysAccount account, String secret) throws MessagingException {
        String host = pop3Host(account);
        boolean ssl = EmailInboxProvider.ssl(account);
        String protocol = ssl ? "pop3s" : "pop3";
        int port = pop3Port(account);
        String prefix = "mail." + protocol + ".";

        Properties props = new Properties();
        props.setProperty(prefix + "host", host);
        props.setProperty(prefix + "port", String.valueOf(port));
        props.setProperty(prefix + "connectiontimeout", String.valueOf(CONNECT_TIMEOUT));
        props.setProperty(prefix + "timeout", String.valueOf(READ_TIMEOUT));

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(host, port, account.getUsername(), secret == null ? "" : secret);
        return store;
    }

    /**
     * 把邮件转成视图。
     *
     * @param msg         邮件
     * @param folder      所在文件夹（已打开）
     * @param withContent 是否读取正文
     * @return 视图
     * @throws MessagingException 协议异常
     * @throws IOException        读取正文异常
     */
    private MailboxMessage toView(Message msg, Folder folder, boolean withContent)
            throws MessagingException, IOException {
        MailboxMessage view = new MailboxMessage();
        view.setId(idOf(msg));
        view.setFolder(folder.getFullName());
        view.setSubject(msg.getSubject());
        view.setFrom(addresses(msg.getFrom()));
        view.setTo(addresses(msg.getRecipients(Message.RecipientType.TO)));
        view.setCc(addresses(msg.getRecipients(Message.RecipientType.CC)));
        Date sent = msg.getSentDate();
        if (sent == null) {
            sent = msg.getReceivedDate();
        }
        view.setSentAt(sent == null ? null : sent.getTime());
        view.setUnread(!msg.isSet(Flags.Flag.SEEN));

        Body body = new Body();
        readBody(msg, body);
        String rich = body.html != null ? body.html : body.text;
        view.setContentType(body.html != null ? "html" : "text");
        view.setPreview(summary(rich));
        view.setContent(withContent ? rich : null);
        view.setAttachments(body.attachments);
        view.setHasAttachment(!body.attachments.isEmpty());
        return view;
    }

    /**
     * 消息标识：POP3 无 UID，用消息序号。
     *
     * @param msg 邮件
     * @return 标识
     * @throws MessagingException 协议异常
     */
    private String idOf(Message msg) throws MessagingException {
        return String.valueOf(msg.getMessageNumber());
    }

    /**
     * 递归解析正文与附件名。
     *
     * @param part 邮件部件
     * @param body 采集结果
     * @throws MessagingException 协议异常
     * @throws IOException        读取异常
     */
    private void readBody(Part part, Body body) throws MessagingException, IOException {
        String fileName = part.getFileName();
        if (StringUtils.isNotBlank(fileName)) {
            body.attachments.add(fileName);
        }
        if (part.isMimeType("text/html")) {
            if (body.html == null) {
                body.html = String.valueOf(part.getContent());
            }
            return;
        }
        if (part.isMimeType("text/plain")) {
            if (body.text == null) {
                body.text = String.valueOf(part.getContent());
            }
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                readBody(child, body);
            }
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            Object nested = part.getContent();
            if (nested instanceof Part nestedPart) {
                readBody(nestedPart, body);
            }
        }
    }

    /**
     * 地址数组转文本。
     *
     * @param addresses 地址数组
     * @return 逗号分隔文本
     */
    private String addresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        List<String> values = new ArrayList<>(addresses.length);
        for (Address address : addresses) {
            if (address instanceof InternetAddress internet) {
                String personal = internet.getPersonal();
                values.add(StringUtils.isBlank(personal)
                        ? internet.getAddress()
                        : personal + " <" + internet.getAddress() + ">");
            } else if (address != null) {
                values.add(address.toString());
            }
        }
        return String.join(", ", values);
    }

    /**
     * 生成纯文本摘要（去标签、压空白、截断）。
     *
     * @param value 正文
     * @return 摘要
     */
    private String summary(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String text = TAG.matcher(value).replaceAll(" ");
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        text = BLANK.matcher(text).replaceAll(" ").trim();
        return text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "…" : text;
    }

    /**
     * 忽略大小写包含。
     *
     * @param value 文本
     * @param lower 小写关键字
     * @return 包含返回 {@code true}
     */
    private boolean contains(String value, String lower) {
        return value != null && value.toLowerCase().contains(lower);
    }

    /**
     * 关键字匹配（主题 / 发件人 / 收件人 / 摘要）。
     *
     * @param view    消息视图
     * @param keyword 关键字（小写）
     * @return 匹配返回 {@code true}
     */
    private boolean matches(MailboxMessage view, String keyword) {
        String lower = keyword.toLowerCase();
        return contains(view.getSubject(), lower)
                || contains(view.getFrom(), lower)
                || contains(view.getTo(), lower)
                || contains(view.getPreview(), lower);
    }

    /**
     * 取信条数上限保护。
     *
     * @param limit 请求条数
     * @return 1..{@value #MAX_LIMIT} 之间的条数
     */
    private int clampLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 取消息序号（POP3 用序号作为消息标识，仅在当前连接内稳定）。
     *
     * @param msg 邮件
     * @return 序号
     */
    private static int messageNumber(Message msg) {
        return msg.getMessageNumber();
    }
    /**
     * POP3 主机（收信、凭据校验与连通性探测同一口径）。
     *
     * @param account 账号
     * @return 主机；无法解析返回 {@code null}
     */
    public static String pop3Host(SysAccount account) {
        String explicit = EmailInboxProvider.configString(account, "pop3Host");
        if (explicit != null) {
            return explicit;
        }
        String endpoint = StringUtils.trimToNull(account.getEndpoint());
        if (endpoint == null) {
            return null;
        }
        String host = endpoint.replaceFirst("^[a-zA-Z]+://", "").split("/")[0].split(":")[0].toLowerCase();
        if (host.startsWith("smtp.") || host.startsWith("imap.") || host.startsWith("mail.")) {
            return "pop." + host.substring(host.indexOf('.') + 1);
        }
        if (host.startsWith("pop.") || host.startsWith("pop3.")) {
            return host;
        }
        // IP / localhost / 单段主机名：直接按原样使用
        if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}") || host.equals("localhost") || !host.contains(".")) {
            return host;
        }
        return "pop." + host;
    }

    /**
     * POP3 端口（收信、凭据校验与连通性探测同一口径）。
     *
     * @param account 账号
     * @return 端口
     */
    public static int pop3Port(SysAccount account) {
        Object explicit = account.getConfig() == null ? null : account.getConfig().get("pop3Port");
        if (explicit != null) {
            try {
                int port = Integer.parseInt(String.valueOf(explicit).trim());
                if (port >= PORT_MIN && port <= PORT_MAX) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
                // 落到默认端口
            }
        }
        return EmailInboxProvider.ssl(account) ? POP3S_PORT : POP3_PORT;
    }

    /**
     * 关闭文件夹（忽略异常）。
     *
     * @param folder 文件夹
     */
    private void close(Folder folder) {
        if (folder == null || !folder.isOpen()) {
            return;
        }
        try {
            folder.close(false);
        } catch (MessagingException ignored) {
            // 关闭失败不影响结果
        }
    }

    /**
     * 关闭连接（忽略异常）。
     *
     * @param store 连接
     */
    private void close(Store store) {
        if (store == null || !store.isConnected()) {
            return;
        }
        try {
            store.close();
        } catch (MessagingException ignored) {
            // 关闭失败不影响结果
        }
    }

    /**
     * 正文采集载体。
     */
    private static final class Body {

        /**
         * 纯文本正文
         */
        private String text;

        /**
         * HTML 正文
         */
        private String html;

        /**
         * 附件名
         */
        private final List<String> attachments = new ArrayList<>();
    }
}

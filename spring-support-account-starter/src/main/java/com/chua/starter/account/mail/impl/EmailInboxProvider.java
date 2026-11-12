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
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FlagTerm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 邮件收信实现（IMAP / IMAPS）。
 *
 * <p>为什么不用 {@code EmailClient#fetch()}：那个方法只回主题 / 发件人 / 正文，
 * 没有文件夹、UID 与已读标记，界面做「收件箱 / 已发送」切换和分页时需要这些信息；
 * 且它把异常吞掉后返回空列表，会让用户误以为「没有邮件」。这里直接用
 * {@code jakarta.mail} 打开 Store，异常原样抛出，由编排层翻译成可读提示。</p>
 *
 * <p>主机与端口的解析顺序：{@code config.imapHost} → 由 {@code endpoint}
 * （SMTP 主机）推导（{@code smtp.qq.com} → {@code imap.qq.com}）；
 * 端口：{@code config.imapPort} → SSL 用 993，否则用 143。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Spi("email-imap")
public class EmailInboxProvider implements AccountInboxProvider {

    /**
     * 支持的账号种类
     */
    private static final String KIND_EMAIL = "EMAIL";

    /**
     * 收件箱服务端名（所有 IMAP 服务商统一）
     */
    private static final String INBOX = "INBOX";

    /**
     * IMAPS 默认端口
     */
    private static final int IMAPS_PORT = 993;

    /**
     * IMAP 默认端口
     */
    private static final int IMAP_PORT = 143;

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
     * 文件夹角色 → 界面名
     */
    private static final Map<String, String> ROLE_LABELS = Map.of(
            "INBOX", "收件箱",
            "SENT", "已发送",
            "DRAFT", "草稿箱",
            "JUNK", "垃圾邮件",
            "TRASH", "已删除");

    /**
     * 文件夹角色顺序（界面展示顺序）
     */
    private static final List<String> ROLE_ORDER = List.of("INBOX", "SENT", "DRAFT", "JUNK", "TRASH");

    /**
     * {@inheritDoc}
     */
    @Override
    public String provider() {
        return "email-imap";
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
     */
    @Override
    public boolean supports(SysAccount account) {
        return account != null
                && !TempMailService.isTempMail(account)
                && KIND_EMAIL.equalsIgnoreCase(account.getKind())
                && StringUtils.isNotBlank(account.getUsername())
                && StringUtils.isNotBlank(imapHost(account));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MailboxFolder> folders(SysAccount account, String secret) throws Exception {
        Store store = open(account, secret);
        Folder current = null;
        try {
            Map<String, Folder> candidates = discover(store);
            List<MailboxFolder> result = new ArrayList<>();
            for (String role : ROLE_ORDER) {
                Folder folder = candidates.get(role);
                if (folder == null) {
                    continue;
                }
                MailboxFolder view = new MailboxFolder();
                view.setKey(folder.getFullName());
                view.setName(ROLE_LABELS.getOrDefault(role, role));
                view.setRole(role);
                try {
                    folder.open(Folder.READ_ONLY);
                    current = folder;
                    view.setTotal(folder.getMessageCount());
                    view.setUnread(folder.getUnreadMessageCount());
                } catch (MessagingException e) {
                    // 单个文件夹打不开不影响其它文件夹展示
                    view.setTotal(0);
                    view.setUnread(0);
                } finally {
                    close(current);
                    current = null;
                }
                result.add(view);
            }
            return result;
        } finally {
            close(current);
            close(store);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MailboxMessage> messages(SysAccount account, String secret, MailboxQuery query) throws Exception {
        MailboxQuery effective = query == null ? new MailboxQuery() : query;
        String folderName = StringUtils.isBlank(effective.getFolder()) ? INBOX : effective.getFolder().trim();
        int limit = clampLimit(effective.getLimit());
        int offset = effective.getOffset() == null ? 0 : Math.max(0, effective.getOffset());
        String keyword = StringUtils.trimToNull(effective.getKeyword());
        boolean unreadOnly = Boolean.TRUE.equals(effective.getUnreadOnly());
        boolean preferUnread = Boolean.TRUE.equals(effective.getPreferUnread());

        Store store = open(account, secret);
        Folder folder = null;
        try {
            folder = store.getFolder(folderName);
            if (folder == null || !folder.exists()) {
                throw new MessagingException("邮箱文件夹不存在: " + folderName);
            }
            folder.open(Folder.READ_ONLY);
            // 未读优先只在首屏启用：翻页必须保持稳定顺序，否则「加载更多」会重复或漏信
            List<Message> picked;
            if (unreadOnly) {
                picked = unreadPage(folder, offset, limit);
            } else if (preferUnread && offset == 0) {
                picked = unreadFirstPage(folder, limit);
            } else {
                picked = latestPage(folder, offset, limit);
            }
            List<MailboxMessage> result = new ArrayList<>(picked.size());
            for (Message message : picked) {
                MailboxMessage view = toView(message, folder, false);
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
     * 取最新一页（按邮件序号倒序，最新在前）。
     *
     * @param folder 已打开的文件夹
     * @param offset 跳过多少封
     * @param limit  取多少封
     * @return 邮件（最新在前）
     * @throws MessagingException 协议异常
     */
    private List<Message> latestPage(Folder folder, int offset, int limit) throws MessagingException {
        int total = folder.getMessageCount();
        int end = total - offset;
        if (end < 1) {
            return List.of();
        }
        int start = Math.max(1, end - limit + 1);
        return descending(Arrays.asList(folder.getMessages(start, end)));
    }

    /**
     * 未读分页：只返回未读邮件，按序号倒序。
     *
     * <p>用服务端 {@code SEARCH UNSEEN} 而不是「先取一页再过滤」，后者会让
     * 收件箱里未读很多但位置靠前时，第二页出现重复或漏信。</p>
     *
     * @param folder 已打开的文件夹
     * @param offset 跳过多少封未读
     * @param limit  取多少封
     * @return 未读邮件（最新在前）
     */
    private List<Message> unreadPage(Folder folder, int offset, int limit) {
        List<Message> unseen = descending(unseen(folder));
        if (offset >= unseen.size()) {
            return List.of();
        }
        return unseen.subList(offset, Math.min(unseen.size(), offset + limit));
    }

    /**
     * 未读优先首屏：未读在前按时间倒序，不足一页用最新已读补足。
     *
     * <p>这就是「进入收信箱即优先收信」：打开抽屉先看到新的通知，
     * 而不是被一堆已读订阅邮件顶到下面。</p>
     *
     * @param folder 已打开的文件夹
     * @param limit  取多少封
     * @return 邮件（未读在前）
     */
    private List<Message> unreadFirstPage(Folder folder, int limit) {
        List<Message> picked = new ArrayList<>(limit);
        Set<Integer> taken = new HashSet<>();
        for (Message message : descending(unseen(folder))) {
            if (picked.size() >= limit) {
                return picked;
            }
            picked.add(message);
            taken.add(numberOf(message));
        }
        try {
            for (Message message : latestPage(folder, 0, limit)) {
                if (picked.size() >= limit) {
                    break;
                }
                if (taken.add(numberOf(message))) {
                    picked.add(message);
                }
            }
        } catch (MessagingException e) {
            // 补足已读失败不影响已取到的未读列表
            return picked;
        }
        return picked;
    }

    /**
     * 未读邮件集合（服务端 SEARCH UNSEEN，不支持搜索时回退逐个读标记）。
     *
     * @param folder 已打开的文件夹
     * @return 未读邮件
     */
    private List<Message> unseen(Folder folder) {
        try {
            Message[] found = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            return Arrays.asList(found);
        } catch (MessagingException | RuntimeException e) {
            List<Message> fallback = new ArrayList<>();
            try {
                for (Message message : folder.getMessages()) {
                    if (!message.isSet(Flags.Flag.SEEN)) {
                        fallback.add(message);
                    }
                }
            } catch (MessagingException ignored) {
                return List.of();
            }
            return fallback;
        }
    }

    /**
     * 按邮件序号倒序（最新在前）。
     *
     * @param messages 邮件
     * @return 倒序列表
     */
    private List<Message> descending(List<Message> messages) {
        List<Message> list = new ArrayList<>(messages);
        list.sort((left, right) -> numberOf(right) - numberOf(left));
        return list;
    }

    /**
     * 邮件序号（读取失败按 0 处理，仅用于排序与去重）。
     *
     * @param message 邮件
     * @return 序号
     */
    private int numberOf(Message message) {
        return message.getMessageNumber();
    }

    /**
     * {@inheritDoc}
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
            Message msg = findMessage(current, id);
            return msg == null ? null : toView(msg, current, true);
        } finally {
            close(current);
            close(store);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsMarkSeen() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsDelete() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean markSeen(SysAccount account, String secret, String folder, String messageId, boolean seen)
            throws Exception {
        String folderName = StringUtils.isBlank(folder) ? INBOX : folder.trim();
        String id = StringUtils.trimToNull(messageId);
        if (id == null) {
            return false;
        }
        Store store = open(account, secret);
        Folder current = null;
        try {
            current = store.getFolder(folderName);
            if (current == null || !current.exists()) {
                return false;
            }
            // 改标记必须读写模式：READ_ONLY 下 setFlag 会被服务端拒绝
            current.open(Folder.READ_WRITE);
            Message msg = findMessage(current, id);
            if (msg == null) {
                return false;
            }
            msg.setFlag(Flags.Flag.SEEN, seen);
            return true;
        } finally {
            // 标记靠 IMAP STORE 即时生效，这里不需要 expunge（避免误删别的 DELETED 邮件）
            close(current);
            close(store);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(SysAccount account, String secret, String folder, String messageId) throws Exception {
        String folderName = StringUtils.isBlank(folder) ? INBOX : folder.trim();
        String id = StringUtils.trimToNull(messageId);
        if (id == null) {
            return false;
        }
        Store store = open(account, secret);
        Folder current = null;
        boolean deleted = false;
        try {
            current = store.getFolder(folderName);
            if (current == null || !current.exists()) {
                return false;
            }
            current.open(Folder.READ_WRITE);
            Message msg = findMessage(current, id);
            if (msg == null) {
                return false;
            }
            msg.setFlag(Flags.Flag.DELETED, true);
            deleted = true;
        } finally {
            // 只有真的打了删除标记才 expunge；中途失败保持原样，避免误删相邻邮件
            closeExpunge(current, deleted);
            close(store);
        }
        return deleted;
    }

    /**
     * 按消息标识取邮件：UID 优先，其次按序号回退。
     *
     * @param folder    已打开的文件夹
     * @param messageId 消息标识
     * @return 邮件；不存在返回 {@code null}
     * @throws MessagingException 协议异常
     */
    private Message findMessage(Folder folder, String messageId) throws MessagingException {
        if (folder instanceof UIDFolder uidFolder) {
            try {
                Message byUid = uidFolder.getMessageByUID(Long.parseLong(messageId));
                if (byUid != null) {
                    return byUid;
                }
            } catch (NumberFormatException | MessagingException ignored) {
                // 序号形式的标识，走下面的回退
            }
        }
        try {
            int number = Integer.parseInt(messageId);
            if (number >= 1 && number <= folder.getMessageCount()) {
                return folder.getMessage(number);
            }
        } catch (NumberFormatException ignored) {
            // 无法解析的标识
        }
        return null;
    }

    /**
     * 发现可用文件夹（按角色去重）。
     *
     * @param store 已连接的 Store
     * @return 角色 → 文件夹
     * @throws MessagingException 协议异常
     */
    private Map<String, Folder> discover(Store store) throws MessagingException {
        Map<String, Folder> result = new LinkedHashMap<>();
        Folder inbox = store.getFolder(INBOX);
        if (inbox != null && inbox.exists()) {
            result.put("INBOX", inbox);
        }
        Folder defaultFolder = store.getDefaultFolder();
        Folder[] all = defaultFolder == null ? null : defaultFolder.list("*");
        if (all == null) {
            return result;
        }
        for (Folder folder : all) {
            String role = roleOf(folder.getFullName());
            if (role != null && !result.containsKey(role)) {
                result.put(role, folder);
            }
        }
        return result;
    }

    /**
     * 按服务端文件夹名判断角色。
     *
     * @param name 服务端文件夹名
     * @return 角色；无关文件夹返回 {@code null}
     */
    private String roleOf(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        String lower = name.toLowerCase();
        if (INBOX.equalsIgnoreCase(name)) {
            return "INBOX";
        }
        if (lower.contains("sent") || name.contains("已发送")) {
            return "SENT";
        }
        if (lower.contains("draft") || name.contains("草稿")) {
            return "DRAFT";
        }
        if (lower.contains("junk") || lower.contains("spam") || name.contains("垃圾")) {
            return "JUNK";
        }
        if (lower.contains("trash") || lower.contains("deleted") || name.contains("已删除")) {
            return "TRASH";
        }
        return null;
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
     * 建立 IMAP / IMAPS 连接。
     *
     * <p>收信与「账号凭据校验」共用同一套主机 / 端口 / 加密推导，
     * 避免两处口径漂移（校验通过但打不开收件箱，或反之）。</p>
     *
     * @param account 账号
     * @param secret  明文凭据
     * @return 已连接的 Store（调用方负责关闭）
     * @throws MessagingException 连接失败
     */
    static Store openStore(SysAccount account, String secret) throws MessagingException {
        String host = imapHost(account);
        boolean ssl = ssl(account);
        String protocol = ssl ? "imaps" : "imap";
        int port = imapPort(account);
        String prefix = "mail." + protocol + ".";

        Properties props = new Properties();
        props.setProperty(prefix + "host", host);
        props.setProperty(prefix + "port", String.valueOf(port));
        props.setProperty(prefix + "connectiontimeout", String.valueOf(CONNECT_TIMEOUT));
        props.setProperty(prefix + "timeout", String.valueOf(READ_TIMEOUT));
        props.setProperty(prefix + "writetimeout", String.valueOf(READ_TIMEOUT));
        if (ssl) {
            props.setProperty(prefix + "ssl.enable", "true");
        }
        props.setProperty("mail.imap.partialfetch", "false");
        // 列信 / 看详情都要读正文，默认的 BODY[] 会让服务端把邮件标成 \Seen：
        // 一次「打开收信箱」就会把整页未读清零，未读角标、未读优先、未读过滤全部失真。
        // BODY.PEEK 只取内容不改标记，改标记统一由 markSeen 显式执行。
        props.setProperty(prefix + "peek", "true");

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
        view.setId(idOf(msg, folder));
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
     * 消息标识：IMAP 用 UID（跨连接稳定），不支持时回退序号。
     *
     * @param msg    邮件
     * @param folder 文件夹
     * @return 标识
     * @throws MessagingException 协议异常
     */
    private String idOf(Message msg, Folder folder) throws MessagingException {
        if (folder instanceof UIDFolder uidFolder) {
            long uid = uidFolder.getUID(msg);
            if (uid > 0) {
                return String.valueOf(uid);
            }
        }
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
     * IMAP 主机（包内共享：收信与凭据校验同一口径）。
     *
     * @param account 账号
     * @return 主机；无法解析返回 {@code null}
     */
    static String imapHost(SysAccount account) {
        String explicit = configString(account, "imapHost");
        if (explicit != null) {
            return explicit;
        }
        String endpoint = StringUtils.trimToNull(account.getEndpoint());
        if (endpoint == null) {
            return null;
        }
        String host = endpoint.replaceFirst("^[a-zA-Z]+://", "").split("/")[0].split(":")[0].toLowerCase();
        if (host.startsWith("smtp.") || host.startsWith("pop.") || host.startsWith("pop3.")
                || host.startsWith("mail.")) {
            return "imap." + host.substring(host.indexOf('.') + 1);
        }
        // IP / localhost / 单段主机名：不能再拼 imap. 前缀（会变成 imap.127.0.0.1 这类必然失败的名字），
        // 直接按原样使用——内网邮件服务器与本地测试都走这条路径。
        if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}") || host.equals("localhost") || !host.contains(".")) {
            return host;
        }
        return "imap." + host;
    }

    /**
     * IMAP 端口（包内共享：收信与凭据校验同一口径）。
     *
     * @param account 账号
     * @return 端口
     */
    static int imapPort(SysAccount account) {
        Object explicit = account.getConfig() == null ? null : account.getConfig().get("imapPort");
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
        return ssl(account) ? IMAPS_PORT : IMAP_PORT;
    }

    /**
     * 是否使用 SSL。
     *
     * @param account 账号
     * @return SSL 返回 {@code true}（未配置时默认 true）
     */
    /**
     * 是否使用 SSL（包内共享）。
     *
     * @param account 账号
     * @return SSL 返回 {@code true}（未配置时默认 true）
     */
    static boolean ssl(SysAccount account) {
        Object ssl = account.getConfig() == null ? null : account.getConfig().get("ssl");
        return ssl == null || Boolean.parseBoolean(String.valueOf(ssl));
    }

    /**
     * 读取配置字符串。
     *
     * @param account 账号
     * @param key     配置键
     * @return 非空字符串；不存在返回 {@code null}
     */
    /**
     * 读取配置字符串（包内共享）。
     *
     * @param account 账号
     * @param key     配置键
     * @return 非空字符串；不存在返回 {@code null}
     */
    static String configString(SysAccount account, String key) {
        Object value = account.getConfig() == null ? null : account.getConfig().get(key);
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
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
     * 关闭文件夹，按需提交删除标记（忽略异常）。
     *
     * @param folder  文件夹
     * @param expunge {@code true} 时提交删除（真正从服务端移除）
     */
    private void closeExpunge(Folder folder, boolean expunge) {
        if (folder == null || !folder.isOpen()) {
            return;
        }
        try {
            folder.close(expunge);
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

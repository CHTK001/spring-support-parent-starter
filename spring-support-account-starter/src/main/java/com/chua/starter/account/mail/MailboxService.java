package com.chua.starter.account.mail;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.message.MessageEnvironment;
import com.chua.common.support.task.message.MessagePush;
import com.chua.common.support.task.message.MessageRequest;
import com.chua.common.support.task.message.MessageResponse;
import com.chua.common.support.utils.StringUtils;
import com.chua.email.support.EmailPush;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.mail.impl.EmailInboxProvider;
import com.chua.starter.account.mail.impl.EmailPop3InboxProvider;
import com.chua.starter.account.mail.temp.TempMailService;
import com.chua.starter.account.manage.service.ManageAccountService;
import com.chua.starter.account.manage.service.SysAccountSecretService;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.manage.store.SysAccountSecret;
import com.chua.starter.account.sendlog.SendLogService;
import com.chua.starter.account.sendlog.store.SysAccountSendLog;
import com.chua.starter.account.spi.AccountInboxProvider;
import com.chua.starter.account.spi.AccountSelection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收 / 发信箱编排服务。
 *
 * <p>收信走 {@link AccountInboxProvider} SPI（邮件由 {@code email-imap} 实现提供），
 * 发信统一走 utils 的 {@link MessagePush} SPI：邮件用 {@link EmailPush}（按账号的
 * SMTP 配置注入 {@link MessageEnvironment}），短信按账号厂商选
 * {@code alibaba-sms} / {@code tencent-sms} / {@code baidu-sms}。</p>
 *
 * <p>本服务不落任何信箱数据：所有消息都实时来自收信实现，账号表只存凭据与主机配置。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailboxService {

    /**
     * 邮件账号种类
     */
    private static final String KIND_EMAIL = AccountKind.EMAIL;

    /**
     * 短信账号种类
     */
    private static final String KIND_SMS = AccountKind.SMS;

    /**
     * 邮件发送通道的 MessagePush 标识
     */
    private static final String EMAIL_PROVIDER = "email";

    /**
     * 邮件默认 SMTP 端口（SSL / SMTPS）
     */
    private static final int DEFAULT_SMTP_SSL_PORT = 465;

    /**
     * 收取最近 30 天邮件的毫秒数（config.receiveRange=30d 时的 since 兜底值）
     */
    private static final long RECENT_30_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    /**
     * 端口取值下界（不含，即最小合法端口）
     */
    private static final int PORT_MIN = 1;

    /**
     * 端口取值上界（含）
     */
    private static final int PORT_MAX = 65535;

    /**
     * 统一账号服务
     */
    private final ManageAccountService accountService;

    /**
     * 账号密钥库（账号 config.secretId 引用时从这里取凭据）
     */
    private final SysAccountSecretService secretService;

    /**
     * 业务配置选定的账号（可选）。
     *
     * <p>配置管理为邮件 / 短信指定了具体账号时优先使用它；没有实现时退回
     * {@link ManageAccountService#resolveDefault(String)}，不改变既有部署的行为。</p>
     */
    private final ObjectProvider<AccountSelection> accountSelection;

    /**
     * 统一发送日志：每次真实发送（成功或失败）异步落一条通道级日志
     */
    private final SendLogService sendLogService;

    /**
     * 信箱概览：能否收信 / 发信、可用文件夹。
     *
     * @param accountId 账号标识
     * @return 概览
     */
    public MailboxOverview overview(String accountId) {
        SysAccount account = requireInboxAccount(accountId);
        MailboxOverview view = new MailboxOverview();
        view.setAccountId(account.getAccountId());
        view.setKind(account.getKind());
        view.setAccountName(account.getName());
        view.setAddress(account.getUsername());
        view.setFolders(new ArrayList<>());

        AccountInboxProvider provider = resolveProvider(account);
        view.setProvider(provider == null ? null : provider.provider());
        view.setSupported(provider != null);
        view.setSendable(isSendable(account));
        view.setModifiable(provider != null && provider.supportsMarkSeen());
        view.setDeletable(provider != null && provider.supportsDelete());
        if (provider == null) {
            view.setMessage(unavailableReason(account));
            return view;
        }
        try {
            view.setFolders(provider.folders(account, accountService.resolveSecret(accountId)));
        } catch (Exception e) {
            view.setMessage("连接收信服务失败：" + rootMessage(e));
        }
        return view;
    }

    /**
     * 取信列表。
     *
     * @param accountId 账号标识
     * @param query     取信条件
     * @return 消息列表
     */
    public List<MailboxMessage> messages(String accountId, MailboxQuery query) {
        SysAccount account = requireInboxAccount(accountId);
        MailboxQuery effective = applyReceiveRange(account, query);
        AccountInboxProvider provider = requireProvider(account);
        try {
            return provider.messages(account, accountService.resolveSecret(accountId), effective);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "收取邮件失败：" + rootMessage(e), e);
        }
    }

    /**
     * 取信列表（系统级，不校验当前用户归属）。
     *
     * <p>给新邮件轮询这类系统任务用：任务线程没有登录态，
     * {@link #messages(String, MailboxQuery)} 的可见性断言会把个人账号全部挡掉。
     * 界面取信仍请用 {@link #messages(String, MailboxQuery)}。</p>
     *
     * @param accountId 账号标识
     * @param query     取信条件
     * @return 消息列表
     */
    public List<MailboxMessage> messagesInternal(String accountId, MailboxQuery query) {
        SysAccount account = requireInboxAccountInternal(accountId);
        MailboxQuery effective = applyReceiveRange(account, query);
        AccountInboxProvider provider = requireProvider(account);
        try {
            return provider.messages(account, accountService.resolveSecret(accountId), effective);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "收取邮件失败：" + rootMessage(e), e);
        }
    }

    /**
     * 落实「收取范围」：账号配置了 {@code config.receiveRange} 且调用方未显式指定
     * {@code since} 时，按配置补上起始时间；显式 since（如轮询器）优先，不覆盖。</p>
     *
     * @param account 账号
     * @param query   调用方传入的取信条件（可为 {@code null}）
     * @return 已落实收取范围的取信条件
     */
    private MailboxQuery applyReceiveRange(SysAccount account, MailboxQuery query) {
        MailboxQuery effective = query == null ? new MailboxQuery() : query;
        if (effective.getSince() != null) {
            return effective;
        }
        Map<String, Object> config = account.getConfig();
        if (config == null || !"30d".equals(String.valueOf(config.get("receiveRange")))) {
            return effective;
        }
        effective.setSince(System.currentTimeMillis() - RECENT_30_DAYS_MS);
        return effective;
    }

    /**
     * 取单封邮件详情。
     *
     * @param accountId 账号标识
     * @param folder    服务端文件夹名
     * @param messageId 消息标识
     * @return 消息详情
     */
    public MailboxMessage message(String accountId, String folder, String messageId) {
        SysAccount account = requireInboxAccount(accountId);
        AccountInboxProvider provider = requireProvider(account);
        try {
            MailboxMessage message = provider.message(
                    account, accountService.resolveSecret(accountId), folder, messageId);
            if (message == null) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "邮件不存在或已删除");
            }
            return message;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "读取邮件失败：" + rootMessage(e), e);
        }
    }

    /**
     * 设置已读 / 未读。
     *
     * @param accountId 账号标识
     * @param folder    服务端文件夹名
     * @param messageId 消息标识
     * @param seen      {@code true} 标记为已读
     * @return 是否写入成功
     */
    public boolean markSeen(String accountId, String folder, String messageId, boolean seen) {
        SysAccount account = requireInboxAccount(accountId);
        AccountInboxProvider provider = requireProvider(account);
        try {
            return provider.markSeen(
                    account, accountService.resolveSecret(accountId), folder, messageId, seen);
        } catch (UnsupportedOperationException e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, e.getMessage());
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "设置已读标记失败：" + rootMessage(e), e);
        }
    }

    /**
     * 删除单封邮件。
     *
     * @param accountId 账号标识
     * @param folder    服务端文件夹名
     * @param messageId 消息标识
     * @return 是否删除成功
     */
    public boolean delete(String accountId, String folder, String messageId) {
        SysAccount account = requireInboxAccount(accountId);
        AccountInboxProvider provider = requireProvider(account);
        try {
            return provider.delete(account, accountService.resolveSecret(accountId), folder, messageId);
        } catch (UnsupportedOperationException e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, e.getMessage());
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "删除邮件失败：" + rootMessage(e), e);
        }
    }

    /**
     * 发信（邮件 / 短信统一入口）。
     *
     * @param accountId 账号标识
     * @param request   发信内容
     * @return 发送结果（{@code success} / {@code provider} / {@code messageId} / {@code message}）
     */
    public Map<String, Object> send(String accountId, MailboxSendRequest request) {
        return doSend(requireAccount(accountId), request);
    }

    /**
     * 用指定种类的<b>默认账号</b>发信（系统级发送入口）。
     *
     * <p>验证码、通知、调度告警这些场景既没有「用户选中的账号」，也常常没有登录态：
     * 由 {@link ManageAccountService#resolveDefault(String)} 解析该种类的默认账号
     * （部署级优先），再走同一套发信实现。账号模块因此成为这些发送业务唯一的口径。</p>
     *
     * @param kind    账号种类（{@code EMAIL} / {@code SMS}）
     * @param request 发信内容
     * @return 发送结果（{@code success} / {@code provider} / {@code messageId} / {@code message}）
     */
    public Map<String, Object> sendByKind(String kind, MailboxSendRequest request) {
        SysAccount account = defaultAccount(kind);
        if (account == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, missingAccountMessage(kind));
        }
        return doSend(account, request);
    }

    /**
     * 取指定种类的默认账号（部署级优先，不校验当前用户归属）。
     *
     * <p>配置管理里为该种类指定了账号（经 {@link AccountSelection} 读取）时优先用它；
     * 指定值缺失、已停用或种类不匹配时回退默认解析，避免配置失效导致发送整体不可用。</p>
     *
     * @param kind 账号种类
     * @return 默认账号（敏感字段已清空）；没有可用账号返回 {@code null}
     */
    public SysAccount defaultAccount(String kind) {
        SysAccount selected = selectedAccount(kind);
        return selected != null ? selected : accountService.resolveDefault(kind);
    }

    /**
     * 取业务配置选定的账号。
     *
     * @param kind 账号种类
     * @return 账号（敏感字段已清空）；未配置或不可用返回 {@code null}
     */
    private SysAccount selectedAccount(String kind) {
        AccountSelection selection = accountSelection.getIfAvailable();
        if (selection == null) {
            return null;
        }
        String accountId;
        try {
            accountId = selection.selectAccountId(kind);
        } catch (Exception e) {
            log.warn("[mailbox] 读取账号选择失败，回退默认账号: kind={}, error={}", kind, e.getMessage());
            return null;
        }
        if (StringUtils.isBlank(accountId)) {
            return null;
        }
        SysAccount account = accountService.getInternal(accountId.trim());
        if (account == null
                || !StringUtils.equalsIgnoreCase(kind, account.getKind())
                || !Integer.valueOf(1).equals(account.getStatus())) {
            log.warn("[mailbox] 配置选定的账号不可用，回退默认账号: kind={}, accountId={}", kind, accountId);
            return null;
        }
        // getInternal 返回的是原始实体（含密文），对外只给清空敏感字段的视图
        account.setSecret(null);
        return account;
    }

    /**
     * 指定种类是否具备发信能力（默认账号存在且 SMTP / 厂商配置完整）。
     *
     * <p>给验证码渠道和调度告警做「是否已配置」判断用：不再让调用方去读
     * {@code sys_setting} 里的 smtp / sms 分组。</p>
     *
     * @param kind 账号种类
     * @return 可发信返回 {@code true}
     */
    public boolean sendable(String kind) {
        SysAccount account = defaultAccount(kind);
        return account != null && isSendable(account);
    }

    /**
     * 取邮件默认账号的连通性探测目标（系统级，不校验当前用户归属）。
     *
     * <p>给「邮箱服务器连通性检测」用：调用方拿到目标后自己完成一次 SMTP / IMAP 握手，
     * 主机、端口、加密方式与明文凭据全部由本方法解析，避免探测端再写一套推导规则。</p>
     *
     * @return 探测目标；没有可用的邮件默认账号返回 {@code null}
     */
    public MailProbeTarget mailProbeTarget() {
        SysAccount account = defaultAccount(KIND_EMAIL);
        if (account == null) {
            return null;
        }
        MailProbeTarget target = new MailProbeTarget();
        target.setAccountId(account.getAccountId());
        target.setAccountName(StringUtils.defaultString(account.getName(), account.getUsername()));
        target.setHost(smtpHost(account));
        target.setPort(smtpPort(account));
        target.setUsername(account.getUsername());
        target.setPassword(StringUtils.defaultString(
                accountService.resolveSecret(account.getAccountId()), ""));
        target.setSsl(ssl(account));
        // 与 emailPush 同一口径：只有非隐式 TLS 的端口才谈得上 STARTTLS 升级
        target.setStarttls(!ssl(account));
        target.setImapHost(EmailInboxProvider.imapHost(account));
        target.setImapPort(EmailInboxProvider.imapPort(account));
        target.setPop3Host(EmailPop3InboxProvider.pop3Host(account));
        target.setPop3Port(EmailPop3InboxProvider.pop3Port(account));
        return target;
    }

    /**
     * 发信实现（邮件 / 短信统一入口）。
     *
     * @param account 账号
     * @param request 发信内容
     * @return 发送结果
     */
    private Map<String, Object> doSend(SysAccount account, MailboxSendRequest request) {
        String accountId = account.getAccountId();
        if (request == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "发信内容不能为空");
        }
        if (StringUtils.isBlank(request.getTo())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "收件人不能为空");
        }
        if (TempMailService.isTempMail(account)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "临时邮箱是只读收信来源，不支持发信");
        }
        boolean email = KIND_EMAIL.equalsIgnoreCase(account.getKind());
        boolean sms = KIND_SMS.equalsIgnoreCase(account.getKind());
        if (!email && !sms) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号种类没有发信箱");
        }
        // 通道级完整日志：成功与失败都异步落一条（sys_account_send_log），不阻塞发送主流程
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = email
                    ? sendMail(account, accountService.resolveSecret(accountId), request)
                    : sendSms(account, accountService.resolveSecret(accountId), request);
            sendLogService.record(sendLog(account, request, start, result, null));
            return result;
        } catch (RuntimeException e) {
            sendLogService.record(sendLog(account, request, start, null, e));
            throw e;
        }
    }

    /**
     * 组装一条通道级发送日志。
     *
     * @param account 发信账号
     * @param request 发信内容
     * @param start   发送起始毫秒（算耗时）
     * @param result  发送结果；抛异常时为 {@code null}
     * @param error   发送异常；正常返回时为 {@code null}
     * @return 待异步写入的日志实体
     */
    private SysAccountSendLog sendLog(SysAccount account, MailboxSendRequest request, long start,
                                      Map<String, Object> result, RuntimeException error) {
        SysAccountSendLog entity = new SysAccountSendLog();
        entity.setSendLogTime(LocalDateTime.now());
        entity.setSendLogKind(String.valueOf(account.getKind()).toUpperCase());
        entity.setSendLogAccountId(account.getAccountId());
        entity.setSendLogAccountName(truncate(
                StringUtils.defaultString(account.getName(), account.getUsername()), 128));
        entity.setSendLogProvider(providerOf(account));
        entity.setSendLogTarget(truncate(request.getTo(), 255));
        entity.setSendLogCc(request.getCc() == null ? null : truncate(String.join(",", request.getCc()), 512));
        entity.setSendLogSubject(truncate(request.getSubject(), 512));
        entity.setSendLogContent(request.getContent());
        entity.setSendLogTemplateCode(truncate(request.getTemplateCode(), 128));
        entity.setSendLogCostMs(System.currentTimeMillis() - start);
        entity.setSendLogSource(truncate(request.getSource(), 64));
        if (error != null) {
            entity.setSendLogSuccess(false);
            entity.setSendLogError(truncate(rootMessage(error), 2000));
            return entity;
        }
        boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
        entity.setSendLogSuccess(success);
        entity.setSendLogMessageId(truncate(asString(result == null ? null : result.get("messageId")), 255));
        if (!success) {
            entity.setSendLogError(truncate(
                    result == null ? "通道无返回" : asString(result.get("message")), 2000));
        }
        return entity;
    }

    /**
     * 发送通道标识（日志用）。
     *
     * @param account 发信账号
     * @return {@code email} / {@code alibaba-sms} 等通道别名
     */
    private String providerOf(SysAccount account) {
        if (KIND_EMAIL.equalsIgnoreCase(account.getKind())) {
            return EMAIL_PROVIDER;
        }
        String alias = smsAlias(account.getVendor());
        return alias == null ? "sms" : alias;
    }

    /**
     * 安全转字符串。
     *
     * @param value 值
     * @return 字符串；{@code null} 透传
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 按长度截断文本（对齐日志列宽）。
     *
     * @param value 原文
     * @param max   上限
     * @return 原文或截断后的文本
     */
    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /**
     * 「缺少可用账号」的提示文案。
     *
     * @param kind 账号种类
     * @return 提示
     */
    private String missingAccountMessage(String kind) {
        return "未配置可用的" + (KIND_EMAIL.equalsIgnoreCase(kind) ? "邮件" : "短信")
                + "账号，请先在账号管理里添加并设为默认";
    }

    /**
     * 发送邮件（MessagePush 在 email 通道上的实现）。
     *
     * @param account 账号
     * @param secret  明文凭据
     * @param request 发信内容
     * @return 发送结果
     */
    private Map<String, Object> sendMail(SysAccount account, String secret, MailboxSendRequest request) {
        MessagePush push = emailPush(account, secret);
        MessageRequest message = MessageRequest.builder()
                .to(request.getTo().trim())
                .cc(request.getCc())
                .subject(StringUtils.defaultString(request.getSubject(), ""))
                .content(StringUtils.defaultString(request.getContent(), ""))
                .contentType("html".equalsIgnoreCase(request.getContentType()) ? "html" : "text")
                .build();
        try {
            return result(push.getProvider(), push.send(message));
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "邮件发送失败：" + rootMessage(e), e);
        }
    }

    /**
     * 组装邮件发送通道（把账号的 SMTP 配置翻译成 {@link EmailPush} 的环境参数）。
     *
     * @param account 邮件账号
     * @param secret  明文凭据
     * @return 已注入该账号 SMTP 配置的通道
     */
    private MessagePush emailPush(SysAccount account, String secret) {
        String host = smtpHost(account);
        if (StringUtils.isBlank(host)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "邮箱账号缺少 SMTP 主机（服务地址）");
        }
        int port = smtpPort(account);
        String username = account.getUsername();
        if (StringUtils.isBlank(username)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "邮箱账号缺少邮箱地址（登录名）");
        }
        String from = configString(account, "from");
        if (StringUtils.isBlank(from)) {
            from = username;
        }
        boolean ssl = ssl(account);

        MessageEnvironment environment = new MessageEnvironment()
                .set("smtp.host", host)
                .set("smtp.port", String.valueOf(port))
                .set("smtp.username", username)
                .set("smtp.password", secret == null ? "" : secret)
                .set("smtp.from", from)
                .set("smtp.auth", "true");
        // EmailPush 按 smtp.ssl.enable / smtp.starttls 两个键决定加密方式，两者互斥
        if (ssl && port == DEFAULT_SMTP_SSL_PORT) {
            environment.set("smtp.ssl.enable", "true");
            environment.set("smtp.starttls", "false");
        } else {
            environment.set("smtp.ssl.enable", "false");
            environment.set("smtp.starttls", String.valueOf(ssl));
        }

        // 邮件能力由 utils 的发信 SPI 提供，本模块不直连 SMTP
        return new EmailPush(environment);
    }

    /**
     * 发送短信（按厂商选 MessagePush 实现）。
     *
     * @param account 账号
     * @param secret  明文凭据
     * @param request 发信内容
     * @return 发送结果
     */
    private Map<String, Object> sendSms(SysAccount account, String secret, MailboxSendRequest request) {
        String alias = smsAlias(account.getVendor());
        if (alias == null) {
            throw unsupportedVendor(account.getVendor());
        }
        MessagePush push = createSmsPush(alias, account, secret);
        if (push == null) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "当前部署未安装短信通道：" + alias);
        }
        // 运营商模板码：请求显式指定优先，其次取账号配置的默认模板
        String templateCode = StringUtils.trimToNull(request.getTemplateCode());
        if (templateCode == null) {
            templateCode = configString(account, "templateCode");
        }
        MessageRequest message = MessageRequest.builder()
                .to(request.getTo().trim())
                .subject(StringUtils.defaultString(request.getSubject(), ""))
                .content(StringUtils.defaultString(request.getContent(), ""))
                .templateId(templateCode)
                .templateParams(request.getTemplateParams())
                .build();
        try {
            // 短信实现统一读 MessageRequest 的 templateId + templateParams（sendTemplate 需要
            // 实现已注册模板元数据，运营商实现多数没有，会误报「模板不存在」）
            return result(push.getProvider(), push.send(message));
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "短信发送失败：" + rootMessage(e), e);
        }
    }

    /**
     * 解析账号对应的短信通道（按 vendor 查 MessagePush SPI，不绑定任何厂商）。
     *
     * @param accountId 账号标识；为空时取短信默认账号
     * @return 已注入该账号密钥 / 签名 / 模板码的通道
     */
    public MessagePush smsPush(String accountId) {
        SysAccount account = resolveAccount(accountId, KIND_SMS);
        if (!KIND_SMS.equalsIgnoreCase(account.getKind())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号不是短信账号");
        }
        String alias = smsAlias(account.getVendor());
        if (alias == null) {
            throw unsupportedVendor(account.getVendor());
        }
        MessagePush push = createSmsPush(alias, account, accountService.resolveSecret(account.getAccountId()));
        if (push == null) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "当前部署未安装短信通道：" + alias);
        }
        return push;
    }

    /**
     * 解析邮件发送通道（与 {@link #smsPush(String)} 对称）。
     *
     * <p>给「消息模板推送」这类需要自己逐接收人发送、并自行落日志的调用方用：
     * 拿到通道实例后由调用方决定发给谁、发几次。</p>
     *
     * @param accountId 账号标识；为空时取邮件默认账号
     * @return 已注入该账号 SMTP 配置的通道
     */
    public MessagePush mailPush(String accountId) {
        SysAccount account = resolveAccount(accountId, KIND_EMAIL);
        if (!KIND_EMAIL.equalsIgnoreCase(account.getKind())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号不是邮件账号");
        }
        return emailPush(account, accountService.resolveSecret(account.getAccountId()));
    }

    /**
     * 解析发信账号：显式指定标识优先，未指定时取该种类的默认账号。
     *
     * <p>显式指定的账号仍走归属可见性断言；默认账号来自
     * {@link ManageAccountService#resolveDefault(String)}，用于无用户上下文的系统级发送。</p>
     *
     * @param accountId 账号标识（可空）
     * @param kind      账号种类
     * @return 账号
     */
    private SysAccount resolveAccount(String accountId, String kind) {
        if (StringUtils.isNotBlank(accountId)) {
            return requireAccount(accountId);
        }
        SysAccount account = defaultAccount(kind);
        if (account == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, missingAccountMessage(kind));
        }
        return account;
    }

    /**
     * 组装发送结果。
     *
     * @param provider 通道标识
     * @param response 发信响应
     * @return 结果映射
     */
    private Map<String, Object> result(String provider, MessageResponse response) {
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("success", response == null || response.isSuccess());
        result.put("provider", provider);
        result.put("messageId", response == null ? null : response.getMessageId());
        result.put("message", response == null ? null : response.getErrorMessage());
        return result;
    }

    /**
     * 是否具备发信能力。
     *
     * @param account 账号
     * @return 可发信返回 {@code true}
     */
    private boolean isSendable(SysAccount account) {
        if (TempMailService.isTempMail(account)) {
            return false;
        }
        // 手机号两类（临时 / 常规）只收不发：即使 vendor 误配成短信厂商也不放开发送
        if (AccountKind.PHONE.equalsIgnoreCase(account.getKind())
                || AccountKind.TEMP_PHONE.equalsIgnoreCase(account.getKind())) {
            return false;
        }
        if (KIND_EMAIL.equalsIgnoreCase(account.getKind())) {
            return StringUtils.isNotBlank(smtpHost(account)) && StringUtils.isNotBlank(account.getUsername());
        }
        String alias = smsAlias(account.getVendor());
        if (alias == null) {
            return false;
        }
        try {
            return MessagePush.create(alias) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 解析收信实现。
     *
     * @param account 账号
     * @return 收信实现；未安装返回 {@code null}
     */
    private AccountInboxProvider resolveProvider(SysAccount account) {
        Map<String, AccountInboxProvider> providers =
                ServiceProvider.of(AccountInboxProvider.class).list();
        String kind = String.valueOf(account.getKind()).toUpperCase();
        String preferred = preferredProvider(account);
        Set<String> seen = new LinkedHashSet<>();
        // 第一轮：按接入方式优先选匹配的收信实现，避免 IMAP / POP3 对同一账号抢占。
        // SPI 注册 key 统一大写（@Spi 别名经 buildDefinitionAlias 转大写），
        // 这里必须大小写不敏感匹配，否则第一轮永远落空、POP3 账号可能被 IMAP 注册抢占。
        if (preferred != null) {
            for (Map.Entry<String, AccountInboxProvider> entry : providers.entrySet()) {
                if (!preferred.equalsIgnoreCase(entry.getKey())) {
                    continue;
                }
                AccountInboxProvider candidate = entry.getValue();
                if (candidate != null && seen.add(candidate.provider())
                        && candidate.accountKinds().contains(kind) && candidate.supports(account)) {
                    return candidate;
                }
            }
        }
        // 第二轮：任意匹配的实现（向后兼容，如仅安装其中一种协议实现）
        for (AccountInboxProvider provider : providers.values()) {
            if (provider == null || !seen.add(provider.provider())) {
                continue;
            }
            if (provider.accountKinds().contains(kind) && provider.supports(account)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 按账号接入方式选优先的收信实现标识。
     *
     * <p>{@code config.inboxMode} 为 {@code pop3} 时选 {@code email-pop3}，其余均选
     * {@code email-imap}，保证 IMAP 与 POP3 对同一 EMAIL 账号互斥。</p>
     *
     * @param account 账号
     * @return 收信实现标识
     */
    private String preferredProvider(SysAccount account) {
        Object mode = account.getConfig() == null ? null : account.getConfig().get("inboxMode");
        return "pop3".equals(String.valueOf(mode)) ? "email-pop3" : "email-imap";
    }

    /**
     * 要求存在收信实现。
     *
     * @param account 账号
     * @return 收信实现
     * @throws ApiException 未安装时抛出
     */
    private AccountInboxProvider requireProvider(SysAccount account) {
        AccountInboxProvider provider = resolveProvider(account);
        if (provider != null) {
            return provider;
        }
        throw new ApiException(ApiErrorCode.INVALID_PARAM, unavailableReason(account));
    }

    /**
     * 不可用原因。
     *
     * @param account 账号
     * @return 人类可读原因
     */
    private String unavailableReason(SysAccount account) {
        if (KIND_EMAIL.equalsIgnoreCase(account.getKind())) {
            if (StringUtils.isBlank(account.getUsername())) {
                return "邮箱账号缺少邮箱地址，无法登录收信服务";
            }
            if (StringUtils.isBlank(imapHostHint(account))) {
                return "邮箱账号缺少服务地址（SMTP / IMAP 主机），无法定位收信服务";
            }
        }
        return "当前部署未安装「" + account.getKind() + "」收信实现";
    }

    /**
     * IMAP 主机提示（与收信实现同口径，仅用于说明原因）。
     *
     * @param account 账号
     * @return 主机；无法解析返回 {@code null}
     */
    private String imapHostHint(SysAccount account) {
        String explicit = configString(account, "imapHost");
        return explicit != null ? explicit : StringUtils.trimToNull(account.getEndpoint());
    }

    /**
     * 选择短信 MessagePush 通道。
     *
     * @param vendor 厂商
     * @return 通道标识；不支持返回 {@code null}
     */
    private String smsAlias(String vendor) {
        String value = vendor == null ? "" : vendor.trim().toLowerCase();
        if (value.isEmpty()) {
            return null;
        }
        // 中文厂商名与英文别名一视同仁：阿里云 / 腾讯云 / 百度云在账号里也常见
        if (value.contains("ali") || value.contains("阿里")) {
            return "alibaba-sms";
        }
        if (value.contains("tencent") || value.contains("qcloud") || value.contains("腾讯")) {
            return "tencent-sms";
        }
        if (value.contains("baidu") || value.contains("百度")) {
            return "baidu-sms";
        }
        return null;
    }

    /**
     * 构造「短信厂商不受支持」异常：带上账号实际填写的 vendor，便于用户定位问题。
     *
     * @param vendor 账号填写的厂商
     * @return 可直接抛出的异常
     */
    private ApiException unsupportedVendor(String vendor) {
        String shown = StringUtils.isBlank(vendor) ? "（未填写）" : vendor.trim();
        return new ApiException(ApiErrorCode.INVALID_PARAM,
                "短信账号的服务商「" + shown + "」不受支持；请填写阿里云 / 腾讯云 / 百度云（aliyun / tencent / baidu）");
    }

    /**
     * 创建短信通道，并按账号配置注入环境。
     *
     * <p>短信实现若提供 {@code (MessageEnvironment)} 构造器，则按账号的密钥 / 签名 /
     * 模板码注入；否则回退到实现自己的全局配置。两种情况的失败都会带上上游原因。</p>
     *
     * @param alias   通道标识
     * @param account 账号
     * @param secret  明文凭据
     * @return 通道实例；未安装返回 {@code null}
     */
    private MessagePush createSmsPush(String alias, SysAccount account, String secret) {
        MessagePush push;
        try {
            push = MessagePush.create(alias);
        } catch (RuntimeException e) {
            log.warn("[account-mailbox] 短信通道解析失败: alias={}, error={}", alias, e.getMessage());
            return null;
        }
        if (push == null) {
            return null;
        }
        // 账号可以引用密钥库里的密钥（config.secretId）；没引用时沿用账号自带的
        // username/config.accessKey + secret，保持旧数据可用。
        String accessKey = StringUtils.defaultString(
                StringUtils.defaultString(configString(account, "accessKey"), account.getUsername()), "");
        String secretKey = secret == null ? "" : secret;
        String region = StringUtils.defaultString(configString(account, "region"), "");
        String secretId = configString(account, "secretId");
        if (StringUtils.isNotBlank(secretId)) {
            SysAccountSecret ref = secretService.requireVisible(secretId);
            accessKey = StringUtils.defaultString(ref.getAccessKey(), accessKey);
            secretKey = StringUtils.defaultString(secretService.resolveSecret(secretId), secretKey);
            region = StringUtils.defaultString(ref.getRegion(), region);
        }
        MessageEnvironment environment = new MessageEnvironment()
                .set("sms.secretKey", secretKey)
                .set("sms.accessKey", accessKey)
                .set("sms.signName", StringUtils.defaultString(configString(account, "signName"), ""))
                .set("sms.templateCode", StringUtils.defaultString(configString(account, "templateCode"), ""))
                .set("sms.appId", StringUtils.defaultString(configString(account, "appId"), ""))
                .set("sms.regionId", region);
        try {
            Constructor<?> constructor = push.getClass().getConstructor(MessageEnvironment.class);
            return (MessagePush) constructor.newInstance(environment);
        } catch (ReflectiveOperationException e) {
            return push;
        }
    }

    /**
     * 要求账号存在（系统级：不校验当前用户归属）且种类支持收信箱。
     *
     * @param accountId 账号标识
     * @return 账号（邮件 / 短信）
     */
    private SysAccount requireInboxAccountInternal(String accountId) {
        if (StringUtils.isBlank(accountId)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "账号标识不能为空");
        }
        SysAccount account = accountService.getInternal(accountId);
        if (account == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "账号不存在");
        }
        String kind = String.valueOf(account.getKind()).toUpperCase();
        if (!KIND_EMAIL.equals(kind) && !KIND_SMS.equals(kind) && !AccountKind.TEMP_PHONE.equals(kind)
                && !AccountKind.PHONE.equals(kind)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号种类没有收信箱");
        }
        return account;
    }

    /**
     * 要求账号支持收信箱。
     *
     * @param accountId 账号标识
     * @return 账号（邮件 / 短信）
     */
    private SysAccount requireInboxAccount(String accountId) {
        SysAccount account = requireAccount(accountId);
        String kind = String.valueOf(account.getKind()).toUpperCase();
        // 临时手机号是只读收信来源（本地目录轮询），与邮件 / 短信共用信箱入口
        if (!KIND_EMAIL.equals(kind) && !KIND_SMS.equals(kind) && !AccountKind.TEMP_PHONE.equals(kind)
                && !AccountKind.PHONE.equals(kind)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号种类没有收信箱");
        }
        return account;
    }

    /**
     * 要求账号存在且当前用户可见。
     *
     * @param accountId 账号标识
     * @return 账号
     */
    private SysAccount requireAccount(String accountId) {
        if (StringUtils.isBlank(accountId)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "账号标识不能为空");
        }
        SysAccount account = accountService.get(accountId);
        if (account == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "账号不存在或无权访问");
        }
        return account;
    }

    /**
     * SMTP 主机。
     *
     * @param account 账号
     * @return 主机；未配置返回 {@code null}
     */
    private String smtpHost(SysAccount account) {
        String explicit = configString(account, "smtpHost");
        if (explicit != null) {
            return explicit;
        }
        String endpoint = StringUtils.trimToNull(account.getEndpoint());
        if (endpoint == null) {
            return null;
        }
        String host = endpoint.replaceFirst("^[a-zA-Z]+://", "").split("/")[0].split(":")[0];
        return StringUtils.trimToNull(host);
    }

    /**
     * SMTP 端口。
     *
     * @param account 账号
     * @return 端口
     */
    private int smtpPort(SysAccount account) {
        Object value = account.getConfig() == null ? null : account.getConfig().get("port");
        if (value != null) {
            try {
                int port = Integer.parseInt(String.valueOf(value).trim());
                if (port >= PORT_MIN && port <= PORT_MAX) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
                // 落到默认端口
            }
        }
        return DEFAULT_SMTP_SSL_PORT;
    }

    /**
     * 是否使用 SSL。
     *
     * @param account 账号
     * @return 未配置时默认 {@code true}
     */
    private boolean ssl(SysAccount account) {
        Object value = account.getConfig() == null ? null : account.getConfig().get("ssl");
        return value == null || Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 读取配置字符串。
     *
     * @param account 账号
     * @param key     配置键
     * @return 非空字符串；不存在返回 {@code null}
     */
    private String configString(SysAccount account, String key) {
        Object value = account.getConfig() == null ? null : account.getConfig().get(key);
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
    }

    /**
     * 取最内层异常信息。
     *
     * @param error 异常
     * @return 可读信息
     */
    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return StringUtils.isBlank(message) ? cause.getClass().getSimpleName() : message;
    }
}

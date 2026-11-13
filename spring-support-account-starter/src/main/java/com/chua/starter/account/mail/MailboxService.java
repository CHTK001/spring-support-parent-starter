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
import com.chua.starter.account.mail.temp.TempMailService;
import com.chua.starter.account.manage.service.ManageAccountService;
import com.chua.starter.account.manage.service.SysAccountSecretService;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.manage.store.SysAccountSecret;
import com.chua.starter.account.spi.AccountInboxProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
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
        AccountInboxProvider provider = requireProvider(account);
        try {
            return provider.messages(account, accountService.resolveSecret(accountId), query);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "收取邮件失败：" + rootMessage(e), e);
        }
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
        SysAccount account = requireAccount(accountId);
        if (request == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "发信内容不能为空");
        }
        if (StringUtils.isBlank(request.getTo())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "收件人不能为空");
        }
        if (TempMailService.isTempMail(account)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "临时邮箱是只读收信来源，不支持发信");
        }
        if (KIND_EMAIL.equalsIgnoreCase(account.getKind())) {
            return sendMail(account, accountService.resolveSecret(accountId), request);
        }
        if (KIND_SMS.equalsIgnoreCase(account.getKind())) {
            return sendSms(account, accountService.resolveSecret(accountId), request);
        }
        throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号种类没有发信箱");
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

        // 声明为 MessagePush 契约调用：邮件能力由 utils 的发信 SPI 提供，本模块不直连 SMTP
        MessagePush push = new EmailPush(environment);
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
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "短信账号未填写受支持的服务商（aliyun / tencent / baidu）");
        }
        MessagePush push = createSmsPush(alias, account, secret);
        if (push == null) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "当前部署未安装短信通道：" + alias);
        }
        MessageRequest message = MessageRequest.builder()
                .to(request.getTo().trim())
                .subject(StringUtils.defaultString(request.getSubject(), ""))
                .content(StringUtils.defaultString(request.getContent(), ""))
                .templateId(request.getTemplateCode())
                .templateParams(request.getTemplateParams())
                .build();
        try {
            String templateCode = StringUtils.trimToNull(request.getTemplateCode());
            if (templateCode == null) {
                templateCode = configString(account, "templateCode");
            }
            MessageResponse response;
            if (templateCode == null) {
                response = push.send(message);
            } else {
                Map<String, String> params = request.getTemplateParams() == null
                        ? Map.of()
                        : request.getTemplateParams();
                response = push.sendTemplate(templateCode, params, message);
            }
            return result(push.getProvider(), response);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "短信发送失败：" + rootMessage(e), e);
        }
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
        Set<String> seen = new LinkedHashSet<>();
        for (AccountInboxProvider provider : providers.values()) {
            if (provider == null || !seen.add(provider.provider())) {
                continue;
            }
            if (provider.accountKinds().contains(String.valueOf(account.getKind()).toUpperCase())
                    && provider.supports(account)) {
                return provider;
            }
        }
        return null;
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
        if (value.contains("ali")) {
            return "alibaba-sms";
        }
        if (value.contains("tencent") || value.contains("qcloud")) {
            return "tencent-sms";
        }
        if (value.contains("baidu")) {
            return "baidu-sms";
        }
        return null;
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
     * 要求账号支持收信箱。
     *
     * @param accountId 账号标识
     * @return 账号（邮件 / 短信）
     */
    private SysAccount requireInboxAccount(String accountId) {
        SysAccount account = requireAccount(accountId);
        String kind = String.valueOf(account.getKind()).toUpperCase();
        if (!KIND_EMAIL.equals(kind) && !KIND_SMS.equals(kind)) {
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

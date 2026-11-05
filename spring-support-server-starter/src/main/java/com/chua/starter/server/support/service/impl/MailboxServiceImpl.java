// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.email.support.client.EmailClient;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.mapper.MsServerIdentityMapper;
import com.chua.starter.server.support.model.vo.MailMessageVO;
import com.chua.starter.server.support.service.MailboxService;
import com.chua.starter.server.support.util.CredentialCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 邮箱账号(MAILBOX 身份)读取服务实现
 * <p>
 * 邮箱身份约定：{@code msServerIdentityUsername}=邮箱地址、{@code msServerIdentityPassword}=IMAP 授权码(加密存储)、
 * {@code msServerIdentityImapHost/Port}=IMAP 服务器(可空，空则按邮箱域名推断 imap.<域名>，端口默认 993)。
 * 通过 {@code utils-support-email-starter} 的 email客户端 建立 IMAP 连接拉取收件箱。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailboxServiceImpl implements MailboxService {

    /**
     * 身份 映射器
    */
    private final MsServerIdentityMapper identityMapper;
    /**
     * 凭据加解密器
    */
    private final CredentialCipher cipher;

    /**
     * 收件箱默认文件夹
    */
    private static final String DEFAULT_FOLDER = "INBOX";
    /**
     * 默认 IMAP 端口
    */
    private static final int DEFAULT_IMAP_PORT = 993;
    /**
     * 列表正文截断长度
    */
    private static final int CONTENT_TRUNCATE = 400;

    @Override
    public ReturnResult<List<MailMessageVO>> list(Long identityId, String folder, int limit) {
        ResolvedMail cred = resolve(identityId);
        if (cred == null) {
            return ReturnResult.error("邮箱身份不存在或非 MAILBOX 类型: " + identityId);
        }
        try {
            EmailClient client = EmailClient.builder()
                    .imapHost(cred.imapHost)
                    .imapPort(cred.imapPort)
                    .username(cred.email)
                    .password(cred.authCode)
                    .build();
            List<Map<String, Object>> emails = client.fetch()
                    .folder(folder == null || folder.isBlank() ? DEFAULT_FOLDER : folder)
                    .limit(Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100)))
                    .exec();
            List<MailMessageVO> result = new ArrayList<>(emails.size());
            for (Map<String, Object> email : emails) {
                String content = email.get("content") == null ? "" : email.get("content").toString();
                result.add(new MailMessageVO(str(email.get("subject")), str(email.get("from")),
                        date(email.get("date")), size(email.get("size")),
                        content.length() > CONTENT_TRUNCATE ? content.substring(0, CONTENT_TRUNCATE) : content));
            }
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.warn("[mailbox] 读取收件箱失败 identityId={} - {}", identityId, e.getMessage());
            return ReturnResult.error("读取收件箱失败: " + safeMsg(e));
        }
    }
    @Override
    public ReturnResult<MailMessageVO> detail(Long identityId, String folder, String subject) {
        ResolvedMail cred = resolve(identityId);
        if (cred == null) {
            return ReturnResult.error("邮箱身份不存在或非 MAILBOX 类型: " + identityId);
        }
        try {
            EmailClient client = EmailClient.builder()
                    .imapHost(cred.imapHost)
                    .imapPort(cred.imapPort)
                    .username(cred.email)
                    .password(cred.authCode)
                    .build();
            List<Map<String, Object>> emails = client.fetch()
                    .folder(folder == null || folder.isBlank() ? DEFAULT_FOLDER : folder)
                    .limit(100)
                    .exec();
            String keyword = subject;
            Map<String, Object> hit = null;
            if (keyword != null && !keyword.isBlank()) {
                for (Map<String, Object> email : emails) {
                    Object s = email.get("subject");
                    if (s != null && s.toString().contains(keyword)) {
                        hit = email;
                        break;
                    }
                }
            }
            if (hit == null && !emails.isEmpty()) {
                hit = emails.getFirst();
            }
            if (hit == null) {
                return ReturnResult.error("邮箱为空或未找到邮件");
            }
            return ReturnResult.ok(new MailMessageVO(str(hit.get("subject")), str(hit.get("from")),
                    date(hit.get("date")), size(hit.get("size")),
                    hit.get("content") == null ? "" : hit.get("content").toString()));
        } catch (Exception e) {
            log.warn("[mailbox] 读取邮件详情失败 identityId={} - {}", identityId, e.getMessage());
            return ReturnResult.error("读取邮件详情失败: " + safeMsg(e));
        }
    }

    /**
     * 解析邮箱身份 → IMAP 连接信息。
     * @param identityId identityid
     * @return resolve的结果
     */
    private ResolvedMail resolve(Long identityId) {
        if (identityId == null) {
            return null;
        }
        MsServerIdentity identity = identityMapper.selectById(identityId);
        if (identity == null || !"MAILBOX".equals(identity.getMsServerIdentityAuthType())) {
            return null;
        }
        String email = identity.getMsServerIdentityUsername();
        if (email == null || !email.contains("@")) {
            return null;
        }
        String authCode = cipher.decrypt(identity.getMsServerIdentityPassword());
        String imapHost = identity.getMsServerIdentityImapHost();
        if (imapHost == null || imapHost.isBlank()) {
            imapHost = "imap." + email.substring(email.indexOf('@') + 1);
        }
        int port = identity.getMsServerIdentityImapPort() == null
                ? DEFAULT_IMAP_PORT : identity.getMsServerIdentityImapPort();
        return new ResolvedMail(email, authCode, imapHost, port);
    }

    /**
     * IMAP 载荷取字符串（缺失时为空串，保持历史出参一致）
     *
     * @param raw 原始值
     * @return 字符串值
     */
    private static String str(Object raw) {
        return raw == null ? "" : raw.toString();
    }

    /**
     * IMAP 载荷取发送时间
     *
     * @param raw 原始值
     * @return 时间，非 Date 或缺失时为 {@code null}
     */
    private static Date date(Object raw) {
        return raw instanceof Date d ? d : null;
    }

    /**
     * IMAP 载荷取邮件字节数
     *
     * @param raw 原始值
     * @return 字节数，缺失时为 {@code null}
     */
    private static Integer size(Object raw) {
        return raw instanceof Number n ? n.intValue() : null;
    }

    /**
     * 安全提取异常消息。
     *
     * @param e 异常
     * @return 消息(XxxException 取后半段)
     */
    private static String safeMsg(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        int idx = msg.lastIndexOf(':');
        return idx >= 0 && idx < msg.length() - 1 ? msg.substring(idx + 1).trim() : msg.trim();
    }

    /**
     * 解析后的邮箱连接信息
     * @param email email
     * @param authCode 认证编码
     * @param imapHost imap主机
     * @param imapPort imap端口
     * @return ResolvedMail的结果
     */
    private record ResolvedMail(String email, String authCode, String imapHost, int imapPort) {
    }
}
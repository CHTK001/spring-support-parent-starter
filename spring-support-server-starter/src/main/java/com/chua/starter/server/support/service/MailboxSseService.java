package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.datasearch.email.spi.EmailProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.mapper.MsServerIdentityMapper;
import com.chua.starter.server.support.util.CredentialCipher;
import com.chua.starter.sse.support.socket.SseSocketSessionTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 邮箱账号(MAILBOX) SSE 实时推送服务
 * <p>
 * 管理邮箱身份的 SSE 连接：前端通过 /sse/连接 建立连接后，注册到本服务；
 * 服务定时轮询邮箱(IMAP 真实邮箱 / 临时邮箱 API)检测新邮件，通过 sseSocket会话template 推送到前端。
 * SSE 未启用（无 sseSocket会话template Bean）时推送静默跳过。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class MailboxSseService {

    /** SSE 会话模板(全局 Socket 基础设施；未启用 SSE 时为空) */
    private final ObjectProvider<SseSocketSessionTemplate> sseSocketSessionTemplateProvider;
    /** 身份 映射器 */
    private final MsServerIdentityMapper identityMapper;
    /** 凭据加解密器 */
    private final CredentialCipher cipher;

    /**
     * 构造
     *
     * @param sseSocketSessionTemplateProvider SSE 会话模板提供器
     * @param identityMapper                  身份 映射器
     * @param cipher                          凭据加解密器
     */
    public MailboxSseService(ObjectProvider<SseSocketSessionTemplate> sseSocketSessionTemplateProvider,
                             MsServerIdentityMapper identityMapper,
                             CredentialCipher cipher) {
        this.sseSocketSessionTemplateProvider = sseSocketSessionTemplateProvider;
        this.identityMapper = identityMapper;
        this.cipher = cipher;
    }

    /** 轮询间隔(毫秒) */
    private static final long POLL_INTERVAL_MS = 10_000;
    /** 单次推送最大新邮件数 */
    private static final int MAX_PUSH_PER_TICK = 20;

    /** 客户端标识 → 邮箱身份 标识 映射 */
    private final Map<String, Long> clientIdentityMap = new ConcurrentHashMap<>();
    /** 邮箱身份 标识 → 已推送邮件 标识 集合(检测新邮件) */
    private final Map<Long, Set<String>> seenEmailIds = new ConcurrentHashMap<>();

    /**
     * 注册邮箱 SSE 连接(前端打开邮件箱时调用)。
     *
     * @param clientId   前端 SSE 连接 客户端标识
     * @param identityId 邮箱身份 标识
     */
    public void register(String clientId, Long identityId) {
        if (clientId == null || identityId == null) {
            return;
        }
        clientIdentityMap.put(clientId, identityId);
        seenEmailIds.remove(identityId);
        log.info("[mailbox-sse] 注册邮箱推送: clientId={}, identityId={}", clientId, identityId);
    }

    /**
     * 注销邮箱 SSE 连接(前端关闭邮件箱时调用)。
     *
     * @param clientId 前端 SSE 连接 客户端标识
     */
    public void unregister(String clientId) {
        if (clientId == null) {
            return;
        }
        Long removed = clientIdentityMap.remove(clientId);
        if (removed != null) {
            log.info("[mailbox-sse] 注销邮箱推送: clientId={}, identityId={}", clientId, removed);
        }
    }

    /**
     * 定时轮询所有已注册的邮箱身份，检测新邮件并推送到对应前端。
     */
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void pollAndPush() {
        if (clientIdentityMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Long> entry : clientIdentityMap.entrySet()) {
            String clientId = entry.getKey();
            Long identityId = entry.getValue();
            try {
                /**
                 * push新emails。
                 * @param clientId 客户端标识
                 * @param identityId identityid
                 */
                pushNewEmails(clientId, identityId);
            } catch (Exception e) {
                log.warn("[mailbox-sse] 轮询推送异常 identityId={} - {}", identityId, e.getMessage());
            }
        }
    }

    /**
     * 推送指定身份的新邮件到对应前端。
     * @param clientId 客户端标识
     * @param identityId identityid
     */
    private void pushNewEmails(String clientId, Long identityId) {
        MsServerIdentity identity = identityMapper.selectById(identityId);
        if (identity == null || !"MAILBOX".equals(identity.getMsServerIdentityAuthType())) {
            return;
        }
        String email = identity.getMsServerIdentityUsername();
        if (email == null || !email.contains("@")) {
            return;
        }
        List<Map<String, Object>> newEmails = fetchNewEmails(identity, email);
        if (newEmails.isEmpty()) {
            return;
        }
        String payload = serializeEmails(newEmails);
        SseSocketSessionTemplate sseTemplate = sseSocketSessionTemplateProvider.getIfAvailable();
        if (sseTemplate == null) {
            log.debug("[mailbox-sse] SSE 未启用，跳过邮件推送到 clientId={}", clientId);
            return;
        }
        sseTemplate.send(clientId, "mailbox", payload);
        log.debug("[mailbox-sse] 推送 {} 封新邮件到 clientId={}", newEmails.size(), clientId);
    }

    /**
     * 拉取新邮件(真实邮箱走 IMAP / 临时邮箱走 API)，过滤已推送。
     * @param identity identity
     * @param email email
     * @return 获取新emails的结果
     */
    private List<Map<String, Object>> fetchNewEmails(MsServerIdentity identity, String email) {
        String source = identity.getMsServerIdentityEmailSource();
        List<Map<String, Object>> emails;
        if ("TEMP".equals(source)) {
            emails = fetchTempEmails(identity.getMsServerIdentityProvider(), email);
        } else {
            emails = fetchImapEmails(identity, email);
        }
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        Set<String> seen = seenEmailIds.computeIfAbsent(identity.getId(), k -> ConcurrentHashMap.newKeySet());
        List<Map<String, Object>> newOnes = new ArrayList<>();
        synchronized (seen) {
            for (Map<String, Object> e : emails) {
                String id = e.get("id") == null ? null : e.get("id").toString();
                if (id == null) {
                    id = (e.get("subject") == null ? "" : e.get("subject").toString()) + "|"
                            + (e.get("date") == null ? "" : e.get("date").toString());
                }
                if (seen.add(id)) {
                    newOnes.add(e);
                }
            }
            if (seen.size() > 1000) {
                List<String> list = new ArrayList<>(seen);
                seen.clear();
                seen.addAll(list.subList(list.size() - 500, list.size()));
            }
        }
        return newOnes.size() > MAX_PUSH_PER_TICK ? newOnes.subList(0, MAX_PUSH_PER_TICK) : newOnes;
    }

    /**
     * 拉取临时邮箱邮件(走 email提供者 API)。
     * @param provider 提供者
     * @param email email
     * @return 获取tempemails的结果
     */
    private List<Map<String, Object>> fetchTempEmails(String provider, String email) {
        if (provider == null || provider.isBlank()) {
            return List.of();
        }
        try {
            EmailProvider emailProvider = ServiceProvider.of(EmailProvider.class).getExtension(provider);
            if (emailProvider == null) {
                log.warn("[mailbox-sse] 未找到临时邮箱服务商: {}", provider);
                return List.of();
            }
            List<?> rawEmails = emailProvider.fetchEmails(email);
            if (rawEmails == null) {
                return List.of();
            }
            return rawEmails.stream().filter(e -> e instanceof Map).map(e -> (Map<String, Object>) e).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[mailbox-sse] 临时邮箱拉取失败 provider={} - {}", provider, e.getMessage());
            return List.of();
        }
    }

    /**
     * 拉取真实邮箱邮件(走 IMAP email客户端)。
     * @param identity identity
     * @param email email
     * @return 获取imapemails的结果
     */
    private List<Map<String, Object>> fetchImapEmails(MsServerIdentity identity, String email) {
        try {
            String authCode = cipher.decrypt(identity.getMsServerIdentityPassword());
            String imapHost = identity.getMsServerIdentityImapHost();
            if (imapHost == null || imapHost.isBlank()) {
                imapHost = "imap." + email.substring(email.indexOf('@') + 1);
            }
            int port = identity.getMsServerIdentityImapPort() == null ? 993 : identity.getMsServerIdentityImapPort();
            com.chua.email.support.client.EmailClient client = com.chua.email.support.client.EmailClient.builder()
                    .imapHost(imapHost).imapPort(port).username(email).password(authCode).build();
            return client.fetch().folder("INBOX").limit(50).exec();
        } catch (Exception e) {
            log.warn("[mailbox-sse] IMAP 拉取失败 email={} - {}", email, e.getMessage());
            return List.of();
        }
    }

    /**
     * 序列化邮件列表为 JSON 字符串。
     * @param emails emails
     * @return serializeEmails的结果
     */
    private String serializeEmails(List<Map<String, Object>> emails) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(emails);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 获取当前注册的连接数。
     *
     * @return 连接数
     */
    public int getConnectionCount() {
        return clientIdentityMap.size();
    }
}

package com.chua.starter.grok;

import com.chua.common.support.datasearch.email.model.EmailInfo;
import com.chua.common.support.datasearch.email.spi.EmailProvider;
import com.chua.common.support.spi.ServiceProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Grok 账号注册服务。
 *
 * <p>全自动注册流程：创建临时邮箱 → 发送验证码 → 解 Turnstile → 提交注册 → 提取 SSO token。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class GrokRegisterService {

    private static final String SITE_URL = "https://accounts.x.ai";
    private static final String SITE_KEY = "0x4AAAAAAAhr9JGVDZbrZOo0";
    private static final int MAX_EMAIL_POLL = 12;
    private static final int EMAIL_POLL_INTERVAL_MS = 5000;
    private static final int MAX_TURNSPILE_RETRY = 3;

    private final EmailProvider emailProvider;
    private final CaptchaSolver captchaSolver;

    public GrokRegisterService(EmailProvider emailProvider, CaptchaSolver captchaSolver) {
        this.emailProvider = emailProvider;
        this.captchaSolver = captchaSolver;
    }

    /**
     * 执行完整注册流程。
     *
     * @param providerName 邮箱提供商名称（fce/mail-tm/luckmail）
     * @return 注册结果；失败返回 null
     */
    public GrokAccount register(String providerName) {
        return register(providerName, null);
    }

    /**
     * 执行完整注册流程。
     *
     * @param providerName 邮箱提供商名称
     * @param proxyUrl      代理地址（可选）
     * @return 注册结果
     */
    public GrokAccount register(String providerName, String proxyUrl) {
        try {
            // 1. 创建临时邮箱
            String email = createEmail(providerName);
            if (email == null) {
                log.warn("[GrokRegister] 创建邮箱失败");
                return null;
            }
            log.info("[GrokRegister] 创建邮箱成功: {}", email);

            // 2. 发送验证码
            if (!sendVerificationCode(email, proxyUrl)) {
                log.warn("[GrokRegister] 发送验证码失败: {}", email);
                return null;
            }

            // 3. 等待并收取验证码
            String code = pollVerificationCode(email);
            if (code == null) {
                log.warn("[GrokRegister] 未收到验证码: {}", email);
                return null;
            }
            log.info("[GrokRegister] 收到验证码: {} @ {}", code, email);

            // 4. 解 Turnstile
            String turnstileToken = solveTurnstile(proxyUrl);
            if (turnstileToken == null) {
                log.warn("[GrokRegister] 解 Turnstile 失败");
                return null;
            }
            log.info("[GrokRegister] Turnstile 解决成功");

            // 5. 提交注册
            GrokAccount account = submitRegistration(email, code, turnstileToken, proxyUrl);
            if (account != null) {
                log.info("[GrokRegister] 注册成功: {} | SSO: {}...", email, account.ssoToken().substring(0, Math.min(8, account.ssoToken().length())));
            }
            return account;
        } catch (Exception e) {
            log.error("[GrokRegister] 注册异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 批量注册多个账号。
     *
     * @param count       注册数量
     * @param providerName 邮箱提供商
     * @return 注册的账号列表
     */
    public java.util.List<GrokAccount> registerBatch(int count, String providerName) {
        java.util.List<GrokAccount> accounts = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            GrokAccount account = register(providerName);
            if (account != null) {
                accounts.add(account);
            }
            // 错峰注册
            sleepRandom(2000, 5000);
        }
        return accounts;
    }

    private String createEmail(String providerName) {
        try {
            EmailProvider provider = ServiceProvider.of(EmailProvider.class).getExtension(providerName);
            if (provider == null) {
                log.warn("[GrokRegister] 未找到邮箱提供商: {}", providerName);
                return null;
            }
            return provider.createEmail();
        } catch (Exception e) {
            log.error("[GrokRegister] 创建邮箱异常: {}", e.getMessage());
            return null;
        }
    }

    private boolean sendVerificationCode(String email, String proxyUrl) {
        try {
            String url = SITE_URL + "/auth_mgmt.AuthManagement/CreateEmailValidationCode";
            byte[] data = encodeGrpcMessage(1, email);
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("content-type", "application/grpc-web+proto");
            headers.put("x-grpc-web", "1");
            headers.put("x-user-agent", "connect-es/2.1.1");
            headers.put("origin", SITE_URL);
            headers.put("referer", SITE_URL + "/sign-up");
            
            String resp = com.chua.common.support.network.client.HttpClientFactory.of(url)
                    .headers(headers)
                    .proxy(proxyUrl)
                    .post(data)
                    .getBodyString();
            return resp != null;
        } catch (Exception e) {
            log.warn("[GrokRegister] 发送验证码异常: {}", e.getMessage());
            return false;
        }
    }

    private String pollVerificationCode(String email) {
        for (int i = 0; i < MAX_EMAIL_POLL; i++) {
            try {
                EmailInfo firstEmail = emailProvider.fetchFirstEmail(email);
                if (firstEmail != null && firstEmail.getBody() != null) {
                    String otp = FceEmailProvider.extractOtp(firstEmail.getBody());
                    if (otp != null) {
                        return otp;
                    }
                    // 尝试从 subject 提取
                    otp = FceEmailProvider.extractOtp(firstEmail.getSubject());
                    if (otp != null) {
                        return otp;
                    }
                }
            } catch (Exception e) {
                log.warn("[GrokRegister] 收取邮件异常: {}", e.getMessage());
            }
            sleepRandom(EMAIL_POLL_INTERVAL_MS / 2, EMAIL_POLL_INTERVAL_MS);
        }
        return null;
    }

    private String solveTurnstile(String proxyUrl) {
        if (captchaSolver == null) {
            log.warn("[GrokRegister] 未配置验证码求解器");
            return null;
        }
        for (int attempt = 1; attempt <= MAX_TURNSPILE_RETRY; attempt++) {
            try {
                String token = captchaSolver.solve(SITE_URL, SITE_KEY, proxyUrl);
                if (token != null && token.length() > 50) {
                    return token;
                }
                log.warn("[GrokRegister] Turnstile 求解第 {} 次失败", attempt);
                sleepRandom(2000, 3000);
            } catch (Exception e) {
                log.warn("[GrokRegister] Turnstile 求解异常: {}", e.getMessage());
            }
        }
        return null;
    }

    private GrokAccount submitRegistration(String email, String code, String turnstileToken, String proxyUrl) {
        try {
            String password = randomPassword(12);
            String url = SITE_URL + "/sign-up";
            
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("content-type", "application/json");
            headers.put("accept", "application/json");
            headers.put("origin", SITE_URL);
            headers.put("referer", SITE_URL + "/sign-up");
            
            String payload = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"turnstileToken\":\"%s\",\"emailCode\":\"%s\"}",
                email, password, turnstileToken, code
            );
            
            String resp = com.chua.common.support.network.client.HttpClientFactory.of(url)
                    .headers(headers)
                    .proxy(proxyUrl)
                    .post(payload)
                    .getBodyString();
            
            if (resp != null && resp.contains("sso")) {
                // 从响应中提取 SSO token
                com.fasterxml.jackson.databind.JsonNode root = 
                    com.fasterxml.jackson.databind.ObjectMapper.class.newInstance().readTree(resp);
                String ssoToken = extractSsoToken(root);
                if (ssoToken != null) {
                    return new GrokAccount(email, password, ssoToken);
                }
            }
            
            log.warn("[GrokRegister] 注册响应异常: {}", resp);
            return null;
        } catch (Exception e) {
            log.error("[GrokRegister] 提交注册异常: {}", e.getMessage());
            return null;
        }
    }

    private String extractSsoToken(com.fasterxml.jackson.databind.JsonNode root) {
        // 尝试多种路径提取 SSO token
        if (root.has("sso")) {
            return root.path("sso").asText();
        }
        if (root.has("data") && root.path("data").has("sso")) {
            return root.path("data").path("sso").asText();
        }
        return null;
    }

    private byte[] encodeGrpcMessage(int fieldId, String value) {
        byte[] valueBytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = valueBytes.length;
        byte[] payload = new byte[1 + 1 + length];
        // field tag: (fieldId << 3) | wire type (2 = length-delimited)
        payload[0] = (byte) ((fieldId << 3) | 2);
        // length
        payload[1] = (byte) length;
        // value
        System.arraycopy(valueBytes, 0, payload, 2, length);
        // wrapper: 0 + varint length
        byte[] result = new byte[1 + 4 + payload.length];
        result[0] = 0;
        // write length as big-endian int
        result[1] = (byte) ((payload.length >> 24) & 0xFF);
        result[2] = (byte) ((payload.length >> 16) & 0xFF);
        result[3] = (byte) ((payload.length >> 8) & 0xFF);
        result[4] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, result, 5, payload.length);
        return result;
    }

    private String randomPassword(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void sleepRandom(long minMs, long maxMs) {
        long sleep = minMs + (long) (Math.random() * (maxMs - minMs));
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

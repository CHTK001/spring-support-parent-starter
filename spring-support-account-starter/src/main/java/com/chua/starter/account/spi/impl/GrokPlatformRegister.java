package com.chua.starter.account.spi.impl;

import com.chua.common.support.datasearch.email.model.EmailInfo;
import com.chua.common.support.datasearch.email.spi.EmailProvider;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.Account;
import com.chua.starter.account.spi.PlatformRegister;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grok (xAI) 平台注册实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi("grok")
public class GrokPlatformRegister implements PlatformRegister {

    private static final String SITE_URL = "https://accounts.x.ai";
    private static final String SITE_KEY = "0x4AAAAAAAhr9JGVDZbrZOo0";
    private static final int MAX_EMAIL_POLL = 12;
    private static final int EMAIL_POLL_INTERVAL_MS = 5000;

    private final CaptchaSolver captchaSolver;

    public GrokPlatformRegister(CaptchaSolver captchaSolver) {
        this.captchaSolver = captchaSolver;
    }

    @Override
    public String platformName() {
        return "grok";
    }

    @Override
    public Account register(String email, String emailProvider, String proxyUrl) {
        try {
            // 1. 发送验证码
            if (!sendVerificationCode(email, proxyUrl)) {
                log.warn("[Grok] 发送验证码失败: {}", email);
                return null;
            }

            // 2. 等待并收取验证码
            String code = pollVerificationCode(email, emailProvider);
            if (code == null) {
                log.warn("[Grok] 未收到验证码: {}", email);
                return null;
            }
            log.info("[Grok] 收到验证码: {} @ {}", code, email);

            // 3. 解 Turnstile
            String turnstileToken = solveTurnstile(proxyUrl);
            if (turnstileToken == null) {
                log.warn("[Grok] 解 Turnstile 失败");
                return null;
            }
            log.info("[Grok] Turnstile 解决成功");

            // 4. 提交注册
            return submitRegistration(email, code, turnstileToken, proxyUrl);
        } catch (Exception e) {
            log.error("[Grok] 注册异常: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<Account> registerBatch(int count, String emailProvider, String proxyUrl) {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // 创建临时邮箱
            String email = createEmail(emailProvider);
            if (email == null) {
                log.warn("[Grok] 创建邮箱失败，跳过");
                continue;
            }
            log.info("[Grok] 创建邮箱: {}", email);

            Account account = register(email, emailProvider, proxyUrl);
            if (account != null) {
                accounts.add(account);
            }
            sleepRandom(2000, 5000);
        }
        return accounts;
    }

    @Override
    public boolean validate(Account account) {
        if (account == null || account.getToken() == null) {
            return false;
        }
        try {
            String resp = HttpClientFactory.of(SITE_URL + "/api/v1/me")
                    .header("Authorization", "Bearer " + account.getToken())
                    .get()
                    .getBodyString();
            return resp != null && !resp.contains("error");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Account refreshToken(Account account) {
        // Grok SSO token 一般不需要刷新，直接返回
        return account;
    }

    // ============ 内部方法 ============

    private String createEmail(String providerName) {
        try {
            EmailProvider provider = ServiceProvider.of(EmailProvider.class).getExtension(providerName);
            if (provider == null) {
                log.warn("[Grok] 未找到邮箱提供商: {}", providerName);
                return null;
            }
            return provider.createEmail();
        } catch (Exception e) {
            log.error("[Grok] 创建邮箱异常: {}", e.getMessage());
            return null;
        }
    }

    private boolean sendVerificationCode(String email, String proxyUrl) {
        try {
            String url = SITE_URL + "/auth_mgmt.AuthManagement/CreateEmailValidationCode";
            byte[] data = encodeGrpcMessage(1, email);
            Map<String, String> headers = new HashMap<>();
            headers.put("content-type", "application/grpc-web+proto");
            headers.put("x-grpc-web", "1");
            headers.put("x-user-agent", "connect-es/2.1.1");
            headers.put("origin", SITE_URL);
            headers.put("referer", SITE_URL + "/sign-up");

            String resp = HttpClientFactory.of(url)
                    .headers(headers)
                    .proxy(proxyUrl)
                    .body(data)
                    .post()
                    .getBodyString();
            return resp != null;
        } catch (Exception e) {
            log.warn("[Grok] 发送验证码异常: {}", e.getMessage());
            return false;
        }
    }

    private String pollVerificationCode(String email, String providerName) {
        try {
            EmailProvider provider = ServiceProvider.of(EmailProvider.class).getExtension(providerName);
            if (provider == null) {
                return null;
            }
            for (int i = 0; i < MAX_EMAIL_POLL; i++) {
                try {
                    EmailInfo firstEmail = provider.fetchFirstEmail(email);
                    if (firstEmail != null && firstEmail.getBody() != null) {
                        String otp = extractOtp(firstEmail.getBody());
                        if (otp != null) {
                            return otp;
                        }
                        otp = extractOtp(firstEmail.getSubject());
                        if (otp != null) {
                            return otp;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[Grok] 收取邮件异常: {}", e.getMessage());
                }
                sleepRandom(EMAIL_POLL_INTERVAL_MS / 2, EMAIL_POLL_INTERVAL_MS);
            }
        } catch (Exception e) {
            log.error("[Grok] 轮询验证码异常: {}", e.getMessage());
        }
        return null;
    }

    private String solveTurnstile(String proxyUrl) {
        if (captchaSolver == null) {
            log.warn("[Grok] 未配置验证码求解器");
            return null;
        }
        return captchaSolver.solve(SITE_URL, SITE_KEY, proxyUrl);
    }

    private Account submitRegistration(String email, String code, String turnstileToken, String proxyUrl) {
        try {
            String password = randomPassword(12);
            String url = SITE_URL + "/sign-up";

            Map<String, String> headers = new HashMap<>();
            headers.put("content-type", "application/json");
            headers.put("accept", "application/json");
            headers.put("origin", SITE_URL);
            headers.put("referer", SITE_URL + "/sign-up");

            String payload = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"turnstileToken\":\"%s\",\"emailCode\":\"%s\"}",
                email, password, turnstileToken, code
            );

            String resp = HttpClientFactory.of(url)
                    .headers(headers)
                    .proxy(proxyUrl)
                    .body(payload)
                    .post()
                    .getBodyString();

            if (resp != null && resp.contains("sso")) {
                com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp);
                String ssoToken = extractSsoToken(root);
                if (ssoToken != null) {
                    return new Account(email, password, ssoToken, Account.PlatformType.GROK);
                }
            }
            log.warn("[Grok] 注册响应异常: {}", resp);
            return null;
        } catch (Exception e) {
            log.error("[Grok] 提交注册异常: {}", e.getMessage());
            return null;
        }
    }

    private String extractSsoToken(com.fasterxml.jackson.databind.JsonNode root) {
        if (root.has("sso")) {
            return root.path("sso").asText();
        }
        if (root.has("data") && root.path("data").has("sso")) {
            return root.path("data").path("sso").asText();
        }
        return null;
    }

    private byte[] encodeGrpcMessage(int fieldId, String value) {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        int length = valueBytes.length;
        byte[] payload = new byte[1 + 1 + length];
        payload[0] = (byte) ((fieldId << 3) | 2);
        payload[1] = (byte) length;
        System.arraycopy(valueBytes, 0, payload, 2, length);
        byte[] result = new byte[1 + 4 + payload.length];
        result[0] = 0;
        result[1] = (byte) ((payload.length >> 24) & 0xFF);
        result[2] = (byte) ((payload.length >> 16) & 0xFF);
        result[3] = (byte) ((payload.length >> 8) & 0xFF);
        result[4] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, result, 5, payload.length);
        return result;
    }

    private static final Pattern OTP_PATTERN = Pattern.compile("\\b([A-Z0-9]{3})-?([A-Z0-9]{3})\\b");
    private static final Pattern NUM_OTP_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    public static String extractOtp(String text) {
        if (text == null) return null;
        Matcher m = OTP_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1) + m.group(2);
        }
        Matcher nm = NUM_OTP_PATTERN.matcher(text);
        return nm.find() ? nm.group(1) : null;
    }

    private static String randomPassword(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    private static void sleepRandom(long minMs, long maxMs) {
        long sleep = minMs + (long) (Math.random() * (maxMs - minMs));
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

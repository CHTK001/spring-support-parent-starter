package com.chua.starter.account.mail.temp;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.manage.service.ManageAccountService;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 临时邮箱编排服务。
 *
 * <p>账号仍然落在统一的 {@code sys_account} 表里，只多一个
 * {@code config.tempProvider} 标记：列表、归属隔离、默认账号、删除墓碑等
 * 全部复用账号模块既有逻辑，不新增表也不新增账号种类。</p>
 *
 * <p>凭据只存一次：邮箱地址放 {@code username}，mail.tm 密码放加密的
 * {@code secret}，读接口照旧只回 {@code hasSecret}。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TempMailService {

    /**
     * 临时邮箱标记键
     */
    public static final String CONFIG_PROVIDER = "tempProvider";

    /**
     * 临时邮箱服务标识
     */
    public static final String PROVIDER = "mail-tm";

    /**
     * 账号厂商列展示值
     */
    public static final String VENDOR = "mail-tm";

    /**
     * 本地租期：24 小时（mail.tm 侧不会自动过期，租期用于界面倒计时与清理）
     */
    private static final long TTL_MILLIS = 24L * 60 * 60 * 1000;

    /**
     * 随机源
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 统一账号服务
     */
    private final ManageAccountService accountService;

    /**
     * mail.tm 客户端
     */
    private final TempMailClient client = new TempMailClient();

    /**
     * 领取一个临时邮箱。
     *
     * @param name 自定义名称（可空，默认用邮箱地址）
     * @return 落库后的账号（不含明文凭据）
     */
    public SysAccount claim(String name) {
        String domain = client.pickDomain();
        // [P3C A20 豁免]
        String local = "tmp" + randomToken(10).toLowerCase(Locale.ROOT);
        String address = local + "@" + domain;
        // [P3C A20 豁免]
        String password = randomToken(20);
        String remoteId = client.createAccount(address, password);

        long now = System.currentTimeMillis();
        Map<String, Object> config = new LinkedHashMap<>(4);
        config.put(CONFIG_PROVIDER, PROVIDER);
        config.put("tempAccountId", remoteId);
        config.put("tempDomain", domain);
        config.put("expireAt", now + TTL_MILLIS);

        SysAccount input = new SysAccount();
        // [P3C A20 豁免]
        input.setCode("temp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        input.setName(StringUtils.isBlank(name) ? address : name.trim());
        input.setKind(AccountKind.EMAIL);
        input.setVendor(VENDOR);
        input.setUsername(address);
        input.setSecret(password);
        input.setEndpoint(TempMailClient.BASE_URL);
        input.setConfig(config);
        input.setStatus(1);
        SysAccount saved = accountService.create(input);
        log.info("[temp-mail] 领取临时邮箱: id={}, address={}", saved.getAccountId(), address);
        return saved;
    }

    /**
     * 列出当前用户可见的临时邮箱（已过期的顺手清理）。
     *
     * @return 临时邮箱账号
     */
    public List<SysAccount> list() {
        List<SysAccount> result = new ArrayList<>();
        List<SysAccount> candidates = accountService.list(AccountKind.EMAIL, null, null);
        for (SysAccount account : candidates) {
            if (!isTempMail(account)) {
                continue;
            }
            if (isExpired(account)) {
                destroyQuietly(account);
                continue;
            }
            result.add(account);
        }
        log.info("[temp-mail] 候选={} 命中={}", candidates.size(), result.size());
        return result;
    }

    /**
     * 销毁临时邮箱：先删远端，再逻辑删除本地账号。
     *
     * @param accountId 账号标识
     * @return 是否删除成功
     */
    public boolean destroy(String accountId) {
        SysAccount account = accountService.get(accountId);
        if (account == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "账号不存在或无权访问");
        }
        if (!isTempMail(account)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号不是临时邮箱");
        }
        destroyRemote(account);
        return accountService.delete(accountId);
    }

    /**
     * 是否临时邮箱账号。
     *
     * @param account 账号
     * @return 是返回 {@code true}
     */
    public static boolean isTempMail(SysAccount account) {
        return account != null && StringUtils.isNotBlank(configString(account, CONFIG_PROVIDER));
    }

    /**
     * 账号配置里的字符串值。
     *
     * @param account 账号
     * @param key     键
     * @return 值；没有返回 {@code null}
     */
    public static String configString(SysAccount account, String key) {
        if (account == null || account.getConfig() == null) {
            return null;
        }
        Object value = account.getConfig().get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 账号配置里的长整型值。
     *
     * @param account 账号
     * @param key     键
     * @return 值；没有或非法返回 {@code null}
     */
    public static Long configLong(SysAccount account, String key) {
        String text = configString(account, key);
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 是否已过租期。
     *
     * @param account 账号
     * @return 过期返回 {@code true}
     */
    public static boolean isExpired(SysAccount account) {
        Long expireAt = configLong(account, "expireAt");
        return expireAt != null && expireAt > 0 && expireAt < System.currentTimeMillis();
    }

    /**
     * 删除远端账号（尽力而为，失败只记日志）。
     *
     * @param account 账号
     */
    private void destroyQuietly(SysAccount account) {
        try {
            destroyRemote(account);
            accountService.delete(account.getAccountId());
        } catch (Exception e) {
            log.debug("[temp-mail] 清理过期账号失败: id={}, msg={}", account.getAccountId(), e.getMessage());
        }
    }

    /**
     * 删除 mail.tm 侧账号。
     *
     * @param account 账号
     */
    private void destroyRemote(SysAccount account) {
        String remoteId = configString(account, "tempAccountId");
        String address = account.getUsername();
        if (StringUtils.isBlank(remoteId) || StringUtils.isBlank(address)) {
            return;
        }
        String password = accountService.resolveSecret(account.getAccountId());
        if (StringUtils.isBlank(password)) {
            return;
        }
        String token = client.token(address, password);
        client.deleteAccount(token, remoteId);
    }

    /**
     * 随机小写字母数字串。
     *
     * @param length 长度
     * @return 随机串
     */
    private String randomToken(int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }
}

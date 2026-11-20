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
 * GuerrillaMail 临时邮箱编排服务。
 *
 * <p>与 {@link TempMailService} 同一套口径：账号仍落在统一的 {@code sys_account}
 * 表里，靠 {@code config.tempProvider} 标记区分供应商，不新增表也不新增账号种类。
 * GuerrillaMail 没有密码：地址前缀即命名空间，收信侧用「新会话 + 同前缀」即可
 * 恢复邮箱，因此 {@code secret} 留空。</p>
 *
 * <p>服务端邮件仅保留约 1 小时，但地址可随时重建，本地租期仍按 24 小时口径
 * （与其它临时邮箱一致，用于界面倒计时与过期清理）。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuerrillaMailService {

    /**
     * 临时邮箱服务标识
     */
    public static final String PROVIDER = "guerrilla-mail";

    /**
     * 账号厂商列展示值
     */
    public static final String VENDOR = "guerrilla-mail";

    /**
     * 本地租期：24 小时
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
     * GuerrillaMail 客户端
     */
    private final GuerrillaMailClient client = new GuerrillaMailClient();

    /**
     * 领取一个 GuerrillaMail 临时邮箱。
     *
     * @param name 自定义名称（可空，默认用邮箱地址）
     * @return 落库后的账号（不含明文凭据）
     */
    public SysAccount claim(String name) {
        GuerrillaMailClient.Session session = client.createSession();
        // [P3C A20 豁免]
        String local = "tmp" + randomToken(10).toLowerCase(Locale.ROOT);
        String address = client.bindUser(session.getSidToken(), local);

        long now = System.currentTimeMillis();
        Map<String, Object> config = new LinkedHashMap<>(4);
        config.put(TempMailService.CONFIG_PROVIDER, PROVIDER);
        config.put("tempLocal", local);
        config.put("expireAt", now + TTL_MILLIS);

        SysAccount input = new SysAccount();
        // [P3C A20 豁免]
        input.setCode("temp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        input.setName(StringUtils.isBlank(name) ? address : name.trim());
        input.setKind(AccountKind.EMAIL);
        input.setVendor(VENDOR);
        input.setUsername(address);
        input.setEndpoint(GuerrillaMailClient.BASE_URL);
        input.setConfig(config);
        input.setStatus(1);
        SysAccount saved = accountService.create(input);
        log.info("[guerrilla-mail] 领取临时邮箱: id={}, address={}", saved.getAccountId(), address);
        return saved;
    }

    /**
     * 列出当前用户可见的 GuerrillaMail 临时邮箱（已过期的顺手清理）。
     *
     * @return 临时邮箱账号
     */
    public List<SysAccount> list() {
        List<SysAccount> result = new ArrayList<>();
        List<SysAccount> candidates = accountService.list(AccountKind.EMAIL, null, null);
        for (SysAccount account : candidates) {
            if (!owns(account)) {
                continue;
            }
            if (TempMailService.isExpired(account)) {
                destroyQuietly(account);
                continue;
            }
            result.add(account);
        }
        log.info("[guerrilla-mail] 候选={} 命中={}", candidates.size(), result.size());
        return result;
    }

    /**
     * 销毁临时邮箱（服务端邮件约 1 小时自动过期，这里只做本地逻辑删除）。
     *
     * @param accountId 账号标识
     * @return 是否删除成功
     */
    public boolean destroy(String accountId) {
        SysAccount account = accountService.get(accountId);
        if (account == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "账号不存在或无权访问");
        }
        if (!owns(account)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号不是 guerrilla-mail 临时邮箱");
        }
        return accountService.delete(accountId);
    }

    /**
     * 该账号是否归本服务管（tempProvider 精确匹配）。
     *
     * @param account 账号
     * @return 归本服务管返回 {@code true}
     */
    public boolean owns(SysAccount account) {
        return TempMailService.isTempMail(account)
                && PROVIDER.equalsIgnoreCase(TempMailService.providerOf(account));
    }

    /**
     * 指定账号是否归本服务管。
     *
     * @param accountId 账号标识
     * @return 归本服务管返回 {@code true}
     */
    public boolean ownsAccount(String accountId) {
        SysAccount account = accountService.get(accountId);
        return account != null && owns(account);
    }

    /**
     * 清理过期账号（本地逻辑删除，失败只记日志）。
     *
     * @param account 账号
     */
    private void destroyQuietly(SysAccount account) {
        try {
            accountService.delete(account.getAccountId());
        } catch (Exception e) {
            log.debug("[guerrilla-mail] 清理过期账号失败: id={}, msg={}", account.getAccountId(), e.getMessage());
        }
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

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * temp-mail.io 临时邮箱编排服务。
 *
 * <p>与 {@link TempMailService} 同一套口径：账号仍落在统一的 {@code sys_account}
 * 表里，靠 {@code config.tempProvider} 标记区分供应商，不新增表也不新增账号种类。
 * 建号返回的 token 加密进 {@code secret}，读接口照旧只回 {@code hasSecret}。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TempMailIoService {

    /**
     * 临时邮箱服务标识
     */
    public static final String PROVIDER = "temp-mail-io";

    /**
     * 账号厂商列展示值
     */
    public static final String VENDOR = "temp-mail-io";

    /**
     * 本地租期：24 小时（用于界面倒计时与过期清理）
     */
    private static final long TTL_MILLIS = 24L * 60 * 60 * 1000;

    /**
     * 统一账号服务
     */
    private final ManageAccountService accountService;

    /**
     * temp-mail.io 客户端
     */
    private final TempMailIoClient client = new TempMailIoClient();

    /**
     * 领取一个 temp-mail.io 临时邮箱。
     *
     * @param name 自定义名称（可空，默认用邮箱地址）
     * @return 落库后的账号（不含明文凭据）
     */
    public SysAccount claim(String name) {
        TempMailIoClient.NewEmail newEmail = client.newEmail();

        long now = System.currentTimeMillis();
        Map<String, Object> config = new LinkedHashMap<>(4);
        config.put(TempMailService.CONFIG_PROVIDER, PROVIDER);
        config.put("expireAt", now + TTL_MILLIS);

        SysAccount input = new SysAccount();
        // [P3C A20 豁免]
        input.setCode("temp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        input.setName(StringUtils.isBlank(name) ? newEmail.getEmail() : name.trim());
        input.setKind(AccountKind.EMAIL);
        input.setVendor(VENDOR);
        input.setUsername(newEmail.getEmail());
        input.setSecret(newEmail.getToken());
        input.setEndpoint(TempMailIoClient.BASE_URL);
        input.setConfig(config);
        input.setStatus(1);
        SysAccount saved = accountService.create(input);
        log.info("[temp-mail-io] 领取临时邮箱: id={}, address={}", saved.getAccountId(), newEmail.getEmail());
        return saved;
    }

    /**
     * 列出当前用户可见的 temp-mail.io 临时邮箱（已过期的顺手清理）。
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
        log.info("[temp-mail-io] 候选={} 命中={}", candidates.size(), result.size());
        return result;
    }

    /**
     * 销毁临时邮箱：先删远端（尽力而为），再逻辑删除本地账号。
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
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号不是 temp-mail-io 临时邮箱");
        }
        destroyRemoteQuietly(account);
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
     * 删除 temp-mail.io 侧邮箱（尽力而为，失败只记日志，不阻塞本地删除）。
     *
     * @param account 账号
     */
    private void destroyRemoteQuietly(SysAccount account) {
        try {
            String address = account.getUsername();
            String token = accountService.resolveSecret(account.getAccountId());
            if (StringUtils.isBlank(address) || StringUtils.isBlank(token)) {
                return;
            }
            client.deleteEmail(address, token);
        } catch (Exception e) {
            log.debug("[temp-mail-io] 删除远端邮箱失败（忽略）: id={}, msg={}",
                    account.getAccountId(), e.getMessage());
        }
    }

    /**
     * 清理过期账号（尽力而为，失败只记日志）。
     *
     * @param account 账号
     */
    private void destroyQuietly(SysAccount account) {
        try {
            destroyRemoteQuietly(account);
            accountService.delete(account.getAccountId());
        } catch (Exception e) {
            log.debug("[temp-mail-io] 清理过期账号失败: id={}, msg={}", account.getAccountId(), e.getMessage());
        }
    }
}

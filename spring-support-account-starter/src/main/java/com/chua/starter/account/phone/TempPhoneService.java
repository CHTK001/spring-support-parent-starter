package com.chua.starter.account.phone;

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
import java.util.Map;
import java.util.UUID;

/**
 * 临时手机号编排服务。
 *
 * <p>账号落在统一的 {@code sys_account} 表里，kind={@code TEMP_PHONE}，
 * {@code config.tempPhoneProvider} 做标记：列表、归属隔离、删除墓碑等
 * 全部复用账号模块既有逻辑，不新增表。</p>
 *
 * <p>号码由服务端生成，写进 {@code username}；本地目录轮询器
 * （{@link com.chua.starter.account.schedule.SmsDirectoryPoller}）按 {@code username} 匹配落入短信目录的文件。
 * 无凭据，{@code secret} 留空。租期 24 小时，过期在列表时顺手清理。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TempPhoneService {

    /**
     * 临时手机号标记键
     */
    public static final String CONFIG_PROVIDER = "tempPhoneProvider";

    /**
     * 临时手机号服务标识
     */
    public static final String PROVIDER = "temp-phone";

    /**
     * 账号厂商列展示值
     */
    public static final String VENDOR = "temp-phone";

    /**
     * 本地租期：24 小时（用于界面倒计时与过期清理）
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
     * 领取一个临时手机号。
     *
     * @param name 自定义名称（可空，默认用号码）
     * @return 落库后的账号（不含明文凭据）
     */
    public SysAccount claim(String name) {
        String phone = randomPhone();
        long now = System.currentTimeMillis();
        Map<String, Object> config = new LinkedHashMap<>(4);
        config.put(CONFIG_PROVIDER, PROVIDER);
        config.put("expireAt", now + TTL_MILLIS);
        SysAccount input = new SysAccount();
        // [P3C A20 豁免]
        input.setCode("temp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        input.setName(StringUtils.isBlank(name) ? phone : name.trim());
        input.setKind(AccountKind.TEMP_PHONE);
        input.setVendor(VENDOR);
        input.setUsername(phone);
        input.setConfig(config);
        input.setStatus(1);
        SysAccount saved = accountService.create(input);
        log.info("[temp-phone] 领取临时手机号: id={}, phone={}", saved.getAccountId(), phone);
        return saved;
    }

    /**
     * 列出当前用户可见的临时手机号（已过期的顺手清理）。
     *
     * @return 临时手机号账号
     */
    public List<SysAccount> list() {
        List<SysAccount> result = new ArrayList<>();
        List<SysAccount> candidates = accountService.list(AccountKind.TEMP_PHONE, null, null);
        for (SysAccount account : candidates) {
            if (!isTempPhone(account)) {
                continue;
            }
            if (isExpired(account)) {
                destroyQuietly(account);
                continue;
            }
            result.add(account);
        }
        log.info("[temp-phone] 候选={} 命中={}", candidates.size(), result.size());
        return result;
    }

    /**
     * 销毁临时手机号（本地逻辑删除；内存短信随号码失去入口）。
     *
     * @param accountId 账号标识
     * @return 是否删除成功
     */
    public boolean destroy(String accountId) {
        SysAccount account = accountService.get(accountId);
        if (account == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "账号不存在或无权访问");
        }
        if (!isTempPhone(account)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号不是临时手机号");
        }
        return accountService.delete(accountId);
    }

    /**
     * 是否临时手机号账号。
     *
     * @param account 账号
     * @return 是返回 {@code true}
     */
    public static boolean isTempPhone(SysAccount account) {
        return account != null && AccountKind.TEMP_PHONE.equals(account.getKind())
                && StringUtils.isNotBlank(configString(account, CONFIG_PROVIDER));
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
     * 清理过期账号（尽力而为，失败只记日志）。
     *
     * @param account 账号
     */
    private void destroyQuietly(SysAccount account) {
        try {
            accountService.delete(account.getAccountId());
        } catch (Exception e) {
            log.debug("[temp-phone] 清理过期账号失败: id={}, msg={}", account.getAccountId(), e.getMessage());
        }
    }

    /**
     * 随机生成一个段位合法的国内手机号（1 + [3-9] + 9 位数字）。
     *
     * @return 手机号
     */
    private String randomPhone() {
        StringBuilder builder = new StringBuilder(11);
        builder.append('1').append("3456789".charAt(RANDOM.nextInt(7)));
        for (int i = 0; i < 9; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}

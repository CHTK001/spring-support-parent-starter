package com.chua.starter.account.phone;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.manage.service.ManageAccountService;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 号码池临时手机号编排服务。
 *
 * <p>与 {@link TempPhoneService} 同一套口径：账号落在统一的 {@code sys_account}
 * 表里，kind={@code TEMP_PHONE}，靠 {@code config.tempPhoneProvider} 标记区分来源。
 * 差别只在号码来源：号码从配置 {@code plugin.account.temp-phone-pool.numbers}
 * （逗号分隔）里按序领取，同一号码在租期内不会被重复领出，适合需要对指定号码
 * 做验证码联调的场景。</p>
 *
 * <p>收信仍走本地目录轮询（{@link com.chua.starter.account.schedule.SmsDirectoryPoller}）：
 * 往短信目录丢 {@code to=池内号码} 的 JSON 文件即可到账。无凭据，{@code secret} 留空。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TempPhonePoolService {

    /**
     * 临时手机号服务标识
     */
    public static final String PROVIDER = "temp-phone-pool";

    /**
     * 账号厂商列展示值
     */
    public static final String VENDOR = "temp-phone-pool";

    /**
     * 本地租期：24 小时（用于界面倒计时与过期清理）
     */
    private static final long TTL_MILLIS = 24L * 60 * 60 * 1000;

    /**
     * 统一账号服务
     */
    private final ManageAccountService accountService;

    /**
     * 号码池（逗号分隔，{@code plugin.account.temp-phone-pool.numbers}）
     */
    @Value("${plugin.account.temp-phone-pool.numbers:}")
    private String poolNumbers;

    /**
     * 从号码池领取一个临时手机号。
     *
     * @param name 自定义名称（可空，默认用号码）
     * @return 落库后的账号（不含明文凭据）
     */
    public SysAccount claim(String name) {
        List<String> pool = pool();
        if (pool.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "号码池未配置：请设置 plugin.account.temp-phone-pool.numbers（逗号分隔的号码清单）");
        }
        Set<String> used = new HashSet<>();
        for (SysAccount account : accountService.list(AccountKind.TEMP_PHONE, null, null)) {
            if (TempPhoneService.isTempPhone(account) && !TempPhoneService.isExpired(account)
                    && StringUtils.isNotBlank(account.getUsername())) {
                used.add(account.getUsername().trim());
            }
        }
        String phone = null;
        for (String candidate : pool) {
            if (!used.contains(candidate)) {
                phone = candidate;
                break;
            }
        }
        if (phone == null) {
            throw new ApiException(ApiErrorCode.CONFLICT, "号码池已用尽（共 " + pool.size() + " 个号码）");
        }

        long now = System.currentTimeMillis();
        Map<String, Object> config = new LinkedHashMap<>(4);
        config.put(TempPhoneService.CONFIG_PROVIDER, PROVIDER);
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
        log.info("[temp-phone-pool] 领取临时手机号: id={}, phone={}", saved.getAccountId(), phone);
        return saved;
    }

    /**
     * 列出当前用户可见的号码池临时手机号（已过期的顺手清理，并释放号码）。
     *
     * @return 临时手机号账号
     */
    public List<SysAccount> list() {
        List<SysAccount> result = new ArrayList<>();
        List<SysAccount> candidates = accountService.list(AccountKind.TEMP_PHONE, null, null);
        for (SysAccount account : candidates) {
            if (!owns(account)) {
                continue;
            }
            if (TempPhoneService.isExpired(account)) {
                destroyQuietly(account);
                continue;
            }
            result.add(account);
        }
        log.info("[temp-phone-pool] 候选={} 命中={}", candidates.size(), result.size());
        return result;
    }

    /**
     * 销毁临时手机号（本地逻辑删除；号码回到池中可再次领取）。
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
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号不是号码池临时手机号");
        }
        return accountService.delete(accountId);
    }

    /**
     * 该账号是否归本服务管（tempPhoneProvider 精确匹配）。
     *
     * @param account 账号
     * @return 归本服务管返回 {@code true}
     */
    public boolean owns(SysAccount account) {
        return TempPhoneService.isTempPhone(account)
                && PROVIDER.equalsIgnoreCase(TempPhoneService.providerOf(account));
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
     * 解析号码池配置：去空格、去重、保持配置顺序。
     *
     * @return 号码列表
     */
    private List<String> pool() {
        Set<String> pool = new LinkedHashSet<>();
        if (StringUtils.isBlank(poolNumbers)) {
            return new ArrayList<>();
        }
        for (String part : poolNumbers.split("[,，]")) {
            String number = part.trim();
            if (StringUtils.isNotBlank(number)) {
                pool.add(number);
            }
        }
        return new ArrayList<>(pool);
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
            log.debug("[temp-phone-pool] 清理过期账号失败: id={}, msg={}", account.getAccountId(), e.getMessage());
        }
    }
}

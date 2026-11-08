package com.chua.starter.account.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.manage.mapper.ManageAccountMapper;
import com.chua.starter.account.manage.model.AccountBatchFailure;
import com.chua.starter.account.manage.model.AccountBatchResult;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.ManageAccountService;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.spi.AccountVerifier;
import com.chua.starter.account.support.AccountUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 统一账号服务实现。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>落库前加密敏感值，读取后清空敏感字段，只回传 {@code hasSecret}；</li>
 *   <li>按归属隔离：个人账号只对本人可见，{@code owner} 为空的部署级账号对所有登录用户可见；</li>
 *   <li>校验能力走 {@link AccountVerifier} SPI，账号模块不反向依赖设备 / 邮件 / 短信模块。</li>
 * </ul>
 *
 * <p><b>命名说明</b>：类名带 {@code Manage} 前缀，避免与
 * {@code spring-api-support-common-system-starter} 的
 * {@code com.chua.starter.system.service.impl.SysAccountServiceImpl} 撞 Bean 名
 * （Spring 组件扫描按简单类名生成 Bean 名，重名会让应用启动直接失败）。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManageAccountServiceImpl implements ManageAccountService {

    /**
     * 校验状态：未知（未安装校验能力或从未校验）
     */
    private static final String VERIFY_UNKNOWN = "UNKNOWN";

    /**
     * 校验状态：有效
     */
    private static final String VERIFY_VALID = "VALID";

    /**
     * 校验状态：无效
     */
    private static final String VERIFY_INVALID = "INVALID";

    /**
     * 账号校验信息长度上限（超过截断）
     */
    private static final int MAX_VERIFY_MESSAGE_LENGTH = 500;

    /**
     * 账号映射器
     */
    private final ManageAccountMapper accountMapper;

    /**
     * 敏感值编解码器
     */
    private final AccountSecretCodec secretCodec;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SysAccount> list(String kind, String keyword, String channelKey) {
        LambdaQueryWrapper<SysAccount> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(kind)) {
            wrapper.eq(SysAccount::getKind, kind.trim());
        }
        if (StringUtils.isNotBlank(channelKey)) {
            wrapper.eq(SysAccount::getChannelKey, channelKey.trim());
        }
        if (StringUtils.isNotBlank(keyword)) {
            String like = keyword.trim();
            wrapper.and(w -> w.like(SysAccount::getName, like)
                    .or().like(SysAccount::getCode, like)
                    .or().like(SysAccount::getUsername, like));
        }
        applyOwnership(wrapper);
        wrapper.orderByDesc(SysAccount::getUpdatedAt).orderByDesc(SysAccount::getCreatedAt);
        List<SysAccount> accounts = accountMapper.selectList(wrapper);
        accounts.forEach(this::mask);
        return accounts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SysAccount get(String accountId) {
        return mask(requireAccount(accountId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SysAccount getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        SysAccount account = accountMapper.selectOne(Wrappers.<SysAccount>lambdaQuery()
                .eq(SysAccount::getCode, code.trim())
                .last("limit 1"));
        return mask(account);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccount create(SysAccount input) {
        if (input == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "账号内容不能为空");
        }
        String kind = requireKind(input.getKind());
        String code = requireText(input.getCode(), "账号编码");
        String name = requireText(input.getName(), "账号名称");
        if (existsCode(code, null)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "账号编码已存在: " + code);
        }
        long now = System.currentTimeMillis();
        SysAccount account = new SysAccount();
        account.setAccountId(UUID.randomUUID().toString().replace("-", ""));
        account.setCode(code);
        account.setName(name);
        account.setKind(kind);
        account.setVendor(trimToNull(input.getVendor()));
        account.setChannelKey(trimToNull(input.getChannelKey()));
        account.setUsername(trimToNull(input.getUsername()));
        account.setSecret(secretCodec.encrypt(trimToNull(input.getSecret())));
        account.setEndpoint(trimToNull(input.getEndpoint()));
        account.setConfig(input.getConfig());
        account.setOwner(resolveOwner(input.getOwner()));
        account.setStatus(input.getStatus() == null ? 1 : input.getStatus());
        account.setDefaultAccount(Boolean.TRUE.equals(input.getDefaultAccount()));
        account.setVerifyStatus(VERIFY_UNKNOWN);
        account.setDeleted(0);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accountMapper.insert(account);
        if (Boolean.TRUE.equals(account.getDefaultAccount())) {
            clearOtherDefaults(account);
        }
        log.info("[account] 新增账号: id={}, code={}, kind={}, owner={}",
                account.getAccountId(), account.getCode(), account.getKind(), account.getOwner());
        return mask(account);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccount update(SysAccount input) {
        if (input == null || StringUtils.isBlank(input.getAccountId())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "账号标识不能为空");
        }
        SysAccount existing = requireAccount(input.getAccountId());
        assertWritable(existing);
        String code = requireText(input.getCode(), "账号编码");
        String name = requireText(input.getName(), "账号名称");
        String kind = requireKind(input.getKind());
        if (existsCode(code, existing.getAccountId())) {
            throw new ApiException(ApiErrorCode.CONFLICT, "账号编码已存在: " + code);
        }
        existing.setCode(code);
        existing.setName(name);
        existing.setKind(kind);
        existing.setVendor(trimToNull(input.getVendor()));
        existing.setChannelKey(trimToNull(input.getChannelKey()));
        existing.setUsername(trimToNull(input.getUsername()));
        existing.setEndpoint(trimToNull(input.getEndpoint()));
        existing.setConfig(input.getConfig());
        existing.setStatus(input.getStatus() == null ? existing.getStatus() : input.getStatus());
        existing.setDefaultAccount(Boolean.TRUE.equals(input.getDefaultAccount()));
        // 前端回传掩码或留空表示「不修改」，只有明文才覆盖
        String secret = trimToNull(input.getSecret());
        if (secret != null && !secretCodec.isMasked(secret)) {
            existing.setSecret(secretCodec.encrypt(secret));
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        accountMapper.updateById(existing);
        if (Boolean.TRUE.equals(existing.getDefaultAccount())) {
            clearOtherDefaults(existing);
        }
        log.info("[account] 更新账号: id={}, code={}", existing.getAccountId(), existing.getCode());
        return mask(existing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String accountId) {
        SysAccount existing = requireAccount(accountId);
        assertWritable(existing);
        int rows = accountMapper.deleteById(accountId);
        log.info("[account] 删除账号: id={}, rows={}", accountId, rows);
        return rows > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> verify(String accountId) {
        return doVerify(accountId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountBatchResult batchStatus(List<String> accountIds, int status) {
        int target = status == 0 ? 0 : 1;
        return runBatch(accountIds, accountId -> {
            SysAccount existing = requireAccount(accountId);
            assertWritable(existing);
            existing.setStatus(target);
            existing.setUpdatedAt(System.currentTimeMillis());
            accountMapper.updateById(existing);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountBatchResult batchVerify(List<String> accountIds) {
        return runBatch(accountIds, this::doVerify);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountBatchResult batchDelete(List<String> accountIds) {
        return runBatch(accountIds, accountId -> {
            SysAccount existing = requireAccount(accountId);
            assertWritable(existing);
            accountMapper.deleteById(accountId);
        });
    }

    /**
     * 校验账号并把结果落库（批量校验也走这里，避免逐条起事务代理）。
     *
     * @param accountId 账号标识
     * @return {@code {valid, status, message}}
     */
    private Map<String, Object> doVerify(String accountId) {
        SysAccount account = requireAccount(accountId);
        assertWritable(account);
        Map<String, Object> detail = null;
        String plainSecret = secretCodec.decrypt(account.getSecret());
        AccountVerifier verifier = findVerifier(account.getKind());
        if (verifier != null) {
            try {
                detail = verifier.verify(mask(copyOf(account)), plainSecret);
            } catch (Exception e) {
                log.warn("[account] 校验账号失败: id={}, kind={}, msg={}",
                        accountId, account.getKind(), e.getMessage());
                detail = new LinkedHashMap<>(2);
                detail.put("valid", false);
                detail.put("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        boolean valid = detail != null && Boolean.TRUE.equals(detail.get("valid"));
        String status;
        String message;
        if (detail == null) {
            status = VERIFY_UNKNOWN;
            message = "未安装该账号种类的校验能力";
        } else {
            status = valid ? VERIFY_VALID : VERIFY_INVALID;
            Object raw = detail.get("message");
            message = raw == null ? (valid ? "校验通过" : "校验失败") : raw.toString();
        }
        long now = System.currentTimeMillis();
        account.setVerifyStatus(status);
        account.setVerifyAt(now);
        account.setVerifyMessage(truncate(message, MAX_VERIFY_MESSAGE_LENGTH));
        accountMapper.updateById(account);
        Map<String, Object> payload = new LinkedHashMap<>(3);
        payload.put("valid", valid);
        payload.put("status", status);
        payload.put("message", message);
        return payload;
    }

    /**
     * 逐条执行批量动作，单条失败只记明细、不回滚其它条目。
     *
     * @param accountIds 账号标识清单
     * @param action     单条动作
     * @return 批量结果
     */
    private AccountBatchResult runBatch(List<String> accountIds, Consumer<String> action) {
        List<String> ids = normalizeIds(accountIds);
        List<AccountBatchFailure> failures = new ArrayList<>();
        int succeeded = 0;
        for (String accountId : ids) {
            try {
                action.accept(accountId);
                succeeded++;
            } catch (ApiException e) {
                failures.add(new AccountBatchFailure(accountId, e.getErrorCode().getCode(), e.getMessage()));
            } catch (Exception e) {
                log.warn("[account] 批量操作单条失败: id={}, msg={}", accountId, e.getMessage());
                failures.add(new AccountBatchFailure(accountId, ApiErrorCode.INTERNAL_ERROR.getCode(),
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        log.info("[account] 批量操作完成: total={}, succeeded={}, failed={}",
                ids.size(), succeeded, failures.size());
        return new AccountBatchResult(ids.size(), succeeded, failures.size(), failures);
    }

    /**
     * 去空白、去重、去空值。
     *
     * @param accountIds 原始清单
     * @return 规范化后的清单
     */
    private List<String> normalizeIds(List<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String accountId : accountIds) {
            String value = trimToNull(accountId);
            if (value != null) {
                unique.add(value);
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveSecret(String accountId) {
        if (StringUtils.isBlank(accountId)) {
            return null;
        }
        SysAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return null;
        }
        return secretCodec.decrypt(account.getSecret());
    }

    /**
     * 按标识取账号，不存在时抛 404。
     *
     * @param accountId 账号标识
     * @return 账号（敏感字段未清空）
     */
    private SysAccount requireAccount(String accountId) {
        if (StringUtils.isBlank(accountId)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "账号标识不能为空");
        }
        SysAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "账号不存在: " + accountId);
        }
        return account;
    }

    /**
     * 限制当前用户只能改自己创建的账号；部署级账号（{@code owner} 为空）所有人可改。
     *
     * @param account 账号
     */
    private void assertWritable(SysAccount account) {
        String owner = account.getOwner();
        if (StringUtils.isBlank(owner)) {
            return;
        }
        String currentUserId = AccountUserContext.currentUserId();
        if (!owner.equals(currentUserId)) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权操作该账号");
        }
    }

    /**
     * 归属隔离：个人账号只对本人可见，部署级账号所有人可见。
     *
     * @param wrapper 查询条件
     */
    private void applyOwnership(LambdaQueryWrapper<SysAccount> wrapper) {
        String currentUserId = AccountUserContext.currentUserId();
        if (StringUtils.isBlank(currentUserId)) {
            wrapper.isNull(SysAccount::getOwner);
            return;
        }
        wrapper.and(w -> w.isNull(SysAccount::getOwner).or().eq(SysAccount::getOwner, currentUserId));
    }

    /**
     * 解析账号归属：显式指定优先，否则归属当前登录用户；未登录时视为部署级共享账号。
     *
     * @param requested 请求体中的归属
     * @return 最终归属
     */
    private String resolveOwner(String requested) {
        String owner = trimToNull(requested);
        if (owner != null) {
            return owner;
        }
        return AccountUserContext.currentUserId();
    }

    /**
     * 同种类下只保留一个默认账号。
     *
     * @param account 刚被设为默认的账号
     */
    private void clearOtherDefaults(SysAccount account) {
        LambdaUpdateWrapper<SysAccount> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(SysAccount::getKind, account.getKind())
                .eq(SysAccount::getDefaultAccount, true)
                .ne(SysAccount::getAccountId, account.getAccountId())
                .set(SysAccount::getDefaultAccount, false)
                .set(SysAccount::getUpdatedAt, System.currentTimeMillis());
        if (StringUtils.isBlank(account.getOwner())) {
            wrapper.isNull(SysAccount::getOwner);
        } else {
            wrapper.eq(SysAccount::getOwner, account.getOwner());
        }
        accountMapper.update(null, wrapper);
    }

    /**
     * 判断编码是否已被占用。
     *
     * @param code      账号编码
     * @param excludeId 排除的账号标识（更新场景）
     * @return true 表示已存在
     */
    private boolean existsCode(String code, String excludeId) {
        LambdaQueryWrapper<SysAccount> wrapper = Wrappers.<SysAccount>lambdaQuery()
                .eq(SysAccount::getCode, code);
        if (excludeId != null) {
            wrapper.ne(SysAccount::getAccountId, excludeId);
        }
        return accountMapper.selectCount(wrapper) > 0;
    }

    /**
     * 查找支持该种类的校验实现。
     *
     * @param kind 账号种类
     * @return 校验实现；未安装时返回 {@code null}
     */
    private AccountVerifier findVerifier(String kind) {
        for (AccountVerifier verifier : ServiceProvider.of(AccountVerifier.class).list().values()) {
            if (verifier != null && verifier.supports(kind)) {
                return verifier;
            }
        }
        return null;
    }

    /**
     * 清空敏感字段，保留「是否已配置」标记。
     *
     * @param account 账号
     * @return 同一实例
     */
    private SysAccount mask(SysAccount account) {
        if (account == null) {
            return null;
        }
        account.setHasSecret(StringUtils.isNotBlank(account.getSecret()));
        account.setSecret(null);
        return account;
    }

    /**
     * 复制账号，避免校验实现篡改持久化对象。
     *
     * @param source 原账号
     * @return 副本
     */
    private SysAccount copyOf(SysAccount source) {
        SysAccount copy = new SysAccount();
        copy.setAccountId(source.getAccountId());
        copy.setCode(source.getCode());
        copy.setName(source.getName());
        copy.setKind(source.getKind());
        copy.setVendor(source.getVendor());
        copy.setChannelKey(source.getChannelKey());
        copy.setUsername(source.getUsername());
        copy.setSecret(source.getSecret());
        copy.setEndpoint(source.getEndpoint());
        copy.setConfig(source.getConfig());
        copy.setOwner(source.getOwner());
        copy.setStatus(source.getStatus());
        copy.setDefaultAccount(source.getDefaultAccount());
        copy.setVerifyStatus(source.getVerifyStatus());
        copy.setVerifyAt(source.getVerifyAt());
        copy.setVerifyMessage(source.getVerifyMessage());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    /**
     * 校验并规范化账号种类。
     *
     * @param kind 原始种类
     * @return 规范化后的种类
     */
    private String requireKind(String kind) {
        String value = trimToNull(kind);
        if (value == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "账号种类不能为空");
        }
        value = value.toUpperCase();
        if (!AccountKind.supported(value)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的账号种类: " + kind);
        }
        return value;
    }

    /**
     * 必填文本。
     *
     * @param value 值
     * @param label 字段名
     * @return 去空白后的值
     */
    private String requireText(String value, String label) {
        String text = trimToNull(value);
        if (text == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, label + "不能为空");
        }
        return text;
    }

    /**
     * 去空白并转 {@code null}。
     *
     * @param value 值
     * @return 结果
     */
    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    /**
     * 截断超长文本。
     *
     * @param value 文本
     * @param max   最大长度
     * @return 截断结果
     */
    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}

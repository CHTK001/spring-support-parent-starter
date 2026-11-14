package com.chua.starter.account.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.AccountKindCatalog;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.manage.mapper.ManageAccountMapper;
import com.chua.starter.account.manage.model.AccountBatchFailure;
import com.chua.starter.account.manage.model.AccountBatchResult;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.ManageAccountService;
import com.chua.starter.account.manage.service.SysAccountSecretService;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.manage.store.SysAccountSecret;
import com.chua.starter.account.spi.AccountKindProvider;
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
     * 账号编码列宽（{@code sys_account_code varchar(128)}）
     */
    private static final int MAX_CODE_LENGTH = 128;

    /**
     * 账号映射器
     */
    private final ManageAccountMapper accountMapper;

    /**
     * 敏感值编解码器
     */
    private final AccountSecretCodec secretCodec;

    /**
     * 密钥库服务（账号内联凭据收录用）
     */
    private final SysAccountSecretService secretService;

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
        SysAccount account = requireAccount(accountId);
        assertVisible(account);
        return mask(account);
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
        // 种类专属默认值先补，再做种类必填校验；validate 拿本次提交的明文，不读账号里的密文
        AccountKindCatalog.applyDefaults(account);
        AccountKindCatalog.validate(account, trimToNull(input.getSecret()));
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
        String plainSecret = secret != null && !secretCodec.isMasked(secret) ? secret : null;
        if (plainSecret != null) {
            existing.setSecret(secretCodec.encrypt(plainSecret));
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        // 更新不改默认值（applyDefaults 只在新增时补），只跑种类校验；
        // 未修改凭据时传 null，避免把掩码当明文交给实现
        AccountKindCatalog.validate(existing, plainSecret);
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
        releaseCode(existing);
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
            releaseCode(existing);
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
        // 与调用方一致：引用密钥库时校验的是密钥里那把，而不是账号里的旧副本
        String plainSecret = resolveSecret(accountId);
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
        // 引用密钥库时以密钥为准：换密钥只需改一处，所有引用它的账号一起生效
        Object refId = account.getConfig() == null ? null : account.getConfig().get("secretId");
        String referencedId = refId == null ? null : String.valueOf(refId);
        if (StringUtils.isNotBlank(referencedId)) {
            String plain = secretService.resolveSecret(referencedId);
            if (StringUtils.isNotBlank(plain)) {
                return plain;
            }
            log.warn("[account] 账号引用的密钥取不到明文，回退账号自身凭据: accountId={}, secretId={}",
                    accountId, referencedId);
        }
        return secretCodec.decrypt(account.getSecret());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccountSecret collectSecret(String accountId) {
        SysAccount account = requireAccount(accountId);
        assertVisible(account);
        // 已引用且密钥仍可见：直接返回，避免重复收录
        Object refId = account.getConfig() == null ? null : account.getConfig().get("secretId");
        String referencedId = refId == null ? null : String.valueOf(refId);
        if (StringUtils.isNotBlank(referencedId)) {
            try {
                return secretService.requireVisible(referencedId);
            } catch (ApiException ignore) {
                log.warn("[account] 账号引用的密钥已失效，重新收录: accountId={}, secretId={}",
                        accountId, referencedId);
            }
        }
        String plain = secretCodec.decrypt(account.getSecret());
        if (StringUtils.isBlank(plain)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "账号尚未配置凭据，无法收录到密钥管理");
        }
        String displayName = StringUtils.defaultString(account.getName(), account.getCode());
        SysAccountSecret input = new SysAccountSecret();
        input.setName(displayName);
        input.setProvider(StringUtils.defaultString(account.getVendor(), "custom"));
        input.setSecretType(secretTypeOf(account.getKind()));
        input.setAccessKey(account.getUsername());
        input.setSecret(plain);
        input.setOwner(account.getOwner());
        input.setStatus(account.getStatus());
        input.setRemark("来自账号：" + StringUtils.defaultString(displayName, accountId));
        SysAccountSecret created = secretService.create(input);
        // 账号改为引用新密钥（保留账号自身密文，回滚/兼容都不受影响）
        Map<String, Object> config = account.getConfig() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(account.getConfig());
        config.put("secretId", created.getSecretId());
        account.setConfig(config);
        account.setUpdatedAt(System.currentTimeMillis());
        accountMapper.updateById(account);
        log.info("[account] 账号凭据已收录到密钥库: accountId={}, secretId={}",
                accountId, created.getSecretId());
        return created;
    }

    /**
     * 账号种类 → 密钥类型（与前端 {@code SECRET_TYPE_BY_KIND} 一致）。
     *
     * <p>映射由 {@code AccountKindProvider#secretType()} 声明，新增种类不必再改这里。</p>
     *
     * @param kind 账号种类
     * @return sms / mail / api
     */
    private String secretTypeOf(String kind) {
        return AccountKindCatalog.secretTypeOf(kind);
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
     * 归属可见性：个人账号只对本人可见，部署级账号（{@code owner} 为空）所有人可见。
     *
     * <p>列表查询已经按归属过滤，详情接口必须做同一条断言：否则知道
     * {@code accountId} 的他人仍可读到账号元数据；收信箱接口也走这里，
     * 漏掉这条断言等于把他人信箱一起暴露。</p>
     *
     * @param account 账号
     */
    private void assertVisible(SysAccount account) {
        String owner = account.getOwner();
        if (StringUtils.isBlank(owner)) {
            return;
        }
        if (!owner.equals(AccountUserContext.currentUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权查看该账号");
        }
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
     * 删除前释放账号编码占用。
     *
     * <p>{@code sys_account_code} 建的是<b>物理唯一索引</b>，而删除是逻辑删除：
     * 行还在，编码就还被占着。若直接重发同一编码，{@code existsCode} 查不到
     * （{@code @TableLogic} 自动过滤已删行），插入时却撞唯一索引，最终 500。
     * 因此删除时先把编码改写为带墓碑后缀的历史值，把编码让出来。</p>
     *
     * @param account 待删除账号
     */
    private void releaseCode(SysAccount account) {
        String suffix = "#del#" + account.getAccountId();
        String code = StringUtils.isBlank(account.getCode()) ? "" : account.getCode();
        int keep = Math.max(0, MAX_CODE_LENGTH - suffix.length());
        String tombstone = (code.length() > keep ? code.substring(0, keep) : code) + suffix;
        SysAccount patch = new SysAccount();
        patch.setAccountId(account.getAccountId());
        patch.setCode(tombstone);
        patch.setUpdatedAt(System.currentTimeMillis());
        accountMapper.updateById(patch);
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
     * <p>受支持种类由 {@code AccountKindProvider} SPI 声明，新增种类不必再改这里；
     * 未注册任何实现时由 {@code AccountKindCatalog} 回落到内置常量。</p>
     *
     * @param kind 原始种类
     * @return 规范化后的种类
     */
    private String requireKind(String kind) {
        String value = AccountKindProvider.normalize(kind);
        if (value == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "账号种类不能为空");
        }
        if (!AccountKindCatalog.supported(value)) {
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

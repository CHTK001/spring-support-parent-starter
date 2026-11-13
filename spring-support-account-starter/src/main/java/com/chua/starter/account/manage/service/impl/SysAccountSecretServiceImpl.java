package com.chua.starter.account.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.manage.mapper.SysAccountSecretMapper;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.SysAccountSecretService;
import com.chua.starter.account.manage.store.SysAccountSecret;
import com.chua.starter.account.support.AccountUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 账号密钥库服务实现。
 *
 * <p>归属口径与账号 / Cookie 一致：{@code owner} 为空表示部署级共享，
 * 非空只对本人可见可改。Secret 落库加密，读接口只回 {@code hasSecret}。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAccountSecretServiceImpl implements SysAccountSecretService {

    /**
     * 密钥映射器
     */
    private final SysAccountSecretMapper secretMapper;

    /**
     * 敏感值编解码器
     */
    private final AccountSecretCodec secretCodec;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SysAccountSecret> list(String provider, String keyword) {
        LambdaQueryWrapper<SysAccountSecret> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(provider)) {
            wrapper.eq(SysAccountSecret::getProvider, provider.trim());
        }
        if (StringUtils.isNotBlank(keyword)) {
            String like = keyword.trim();
            wrapper.and(w -> w.like(SysAccountSecret::getName, like)
                    .or().like(SysAccountSecret::getAccessKey, like)
                    .or().like(SysAccountSecret::getRemark, like));
        }
        applyOwnership(wrapper);
        wrapper.orderByDesc(SysAccountSecret::getUpdatedAt).orderByDesc(SysAccountSecret::getCreatedAt);
        List<SysAccountSecret> secrets = secretMapper.selectList(wrapper);
        secrets.forEach(this::mask);
        return secrets;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SysAccountSecret> listByType(String secretType) {
        LambdaQueryWrapper<SysAccountSecret> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(secretType)) {
            wrapper.eq(SysAccountSecret::getSecretType, secretType.trim());
        }
        applyOwnership(wrapper);
        wrapper.orderByDesc(SysAccountSecret::getUpdatedAt).orderByDesc(SysAccountSecret::getCreatedAt);
        List<SysAccountSecret> secrets = secretMapper.selectList(wrapper);
        secrets.forEach(this::mask);
        return secrets;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SysAccountSecret get(String secretId) {
        SysAccountSecret secret = requireSecret(secretId);
        assertVisible(secret);
        return mask(secret);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccountSecret create(SysAccountSecret input) {
        if (input == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "密钥内容不能为空");
        }
        long now = System.currentTimeMillis();
        SysAccountSecret secret = new SysAccountSecret();
        secret.setSecretId(UUID.randomUUID().toString().replace("-", ""));
        secret.setName(requireText(input.getName(), "密钥名称"));
        secret.setProvider(trimToNull(input.getProvider()));
        secret.setSecretType(trimToNull(input.getSecretType()));
        secret.setAccessKey(trimToNull(input.getAccessKey()));
        secret.setSecret(secretCodec.encrypt(trimToNull(input.getSecret())));
        secret.setRegion(trimToNull(input.getRegion()));
        secret.setEndpoint(trimToNull(input.getEndpoint()));
        secret.setOwner(resolveOwner(input.getOwner()));
        secret.setStatus(input.getStatus() == null ? 1 : input.getStatus());
        secret.setRemark(trimToNull(input.getRemark()));
        secret.setDeleted(0);
        secret.setCreatedAt(now);
        secret.setUpdatedAt(now);
        secretMapper.insert(secret);
        log.info("[account] 新增密钥: id={}, name={}, provider={}, owner={}",
                secret.getSecretId(), secret.getName(), secret.getProvider(), secret.getOwner());
        return mask(secret);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccountSecret update(SysAccountSecret input) {
        if (input == null || StringUtils.isBlank(input.getSecretId())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "密钥标识不能为空");
        }
        SysAccountSecret existing = requireSecret(input.getSecretId());
        assertWritable(existing);
        existing.setName(requireText(input.getName(), "密钥名称"));
        existing.setProvider(trimToNull(input.getProvider()));
        existing.setSecretType(trimToNull(input.getSecretType()));
        existing.setAccessKey(trimToNull(input.getAccessKey()));
        existing.setRegion(trimToNull(input.getRegion()));
        existing.setEndpoint(trimToNull(input.getEndpoint()));
        existing.setStatus(input.getStatus() == null ? existing.getStatus() : input.getStatus());
        existing.setRemark(trimToNull(input.getRemark()));
        String value = trimToNull(input.getSecret());
        if (value != null && !secretCodec.isMasked(value)) {
            existing.setSecret(secretCodec.encrypt(value));
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        secretMapper.updateById(existing);
        log.info("[account] 更新密钥: id={}", existing.getSecretId());
        return mask(existing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String secretId) {
        SysAccountSecret existing = requireSecret(secretId);
        assertWritable(existing);
        int rows = secretMapper.deleteById(secretId);
        log.info("[account] 删除密钥: id={}, rows={}", secretId, rows);
        return rows > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveSecret(String secretId) {
        if (StringUtils.isBlank(secretId)) {
            return null;
        }
        SysAccountSecret secret = secretMapper.selectById(secretId);
        return secret == null ? null : secretCodec.decrypt(secret.getSecret());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SysAccountSecret requireVisible(String secretId) {
        SysAccountSecret secret = requireSecret(secretId);
        assertVisible(secret);
        return secret;
    }

    /**
     * 按标识取密钥，不存在时抛 404。
     *
     * @param secretId 密钥标识
     * @return 密钥（Secret 未清空）
     */
    private SysAccountSecret requireSecret(String secretId) {
        if (StringUtils.isBlank(secretId)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "密钥标识不能为空");
        }
        SysAccountSecret secret = secretMapper.selectById(secretId);
        if (secret == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "密钥不存在: " + secretId);
        }
        return secret;
    }

    /**
     * 归属可见性：个人密钥只对本人可见，部署级密钥所有人可见。
     *
     * @param secret 密钥
     */
    private void assertVisible(SysAccountSecret secret) {
        String owner = secret.getOwner();
        if (StringUtils.isBlank(owner)) {
            return;
        }
        if (!owner.equals(AccountUserContext.currentUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权查看该密钥");
        }
    }

    /**
     * 限制当前用户只能改自己创建的密钥；部署级密钥所有人可改。
     *
     * @param secret 密钥
     */
    private void assertWritable(SysAccountSecret secret) {
        String owner = secret.getOwner();
        if (StringUtils.isBlank(owner)) {
            return;
        }
        if (!owner.equals(AccountUserContext.currentUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权操作该密钥");
        }
    }

    /**
     * 归属隔离：个人密钥只对本人可见，部署级密钥所有人可见。
     *
     * @param wrapper 查询条件
     */
    private void applyOwnership(LambdaQueryWrapper<SysAccountSecret> wrapper) {
        String currentUserId = AccountUserContext.currentUserId();
        if (StringUtils.isBlank(currentUserId)) {
            wrapper.isNull(SysAccountSecret::getOwner);
            return;
        }
        wrapper.and(w -> w.isNull(SysAccountSecret::getOwner)
                .or().eq(SysAccountSecret::getOwner, currentUserId));
    }

    /**
     * 解析归属：显式指定优先，否则归属当前登录用户；未登录时视为部署级共享。
     *
     * @param requested 请求体中的归属
     * @return 最终归属
     */
    private String resolveOwner(String requested) {
        String owner = trimToNull(requested);
        return owner != null ? owner : AccountUserContext.currentUserId();
    }

    /**
     * 清空 Secret，保留「是否已配置」标记。
     *
     * @param secret 密钥
     * @return 同一实例
     */
    private SysAccountSecret mask(SysAccountSecret secret) {
        if (secret == null) {
            return null;
        }
        secret.setHasSecret(StringUtils.isNotBlank(secret.getSecret()));
        secret.setSecret(null);
        return secret;
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
     * 去空白，空串返回 {@code null}。
     *
     * @param value 值
     * @return 去空白后的值
     */
    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}

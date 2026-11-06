package com.chua.starter.account.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.manage.mapper.SysAccountAppKeyMapper;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.SysAccountAppKeyService;
import com.chua.starter.account.manage.store.SysAccountAppKey;
import com.chua.starter.account.support.AccountUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 账号模块外部访问密钥服务实现。
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAccountAppKeyServiceImpl implements SysAccountAppKeyService {

    /**
     * 访问 Key 前缀
     */
    private static final String APP_KEY_PREFIX = "ak_";

    /**
     * 访问 Secret 前缀
     */
    private static final String SECRET_PREFIX = "sk_";

    /**
     * 随机源
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 访问 Secret 随机长度（Base64URL 无填充）
     */
    private static final int SECRET_TOKEN_LENGTH = 24;

    /**
     * 访问 Key 随机长度（Base64URL 无填充）
     */
    private static final int APP_KEY_LENGTH = 12;

    /**
     * 密钥映射器
     */
    private final SysAccountAppKeyMapper appKeyMapper;

    /**
     * 敏感值编解码器
     */
    private final AccountSecretCodec secretCodec;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SysAccountAppKey> list(String keyword) {
        LambdaQueryWrapper<SysAccountAppKey> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(keyword)) {
            String like = keyword.trim();
            wrapper.and(w -> w.like(SysAccountAppKey::getName, like)
                    .or().like(SysAccountAppKey::getAppKey, like));
        }
        applyOwnership(wrapper);
        wrapper.orderByDesc(SysAccountAppKey::getUpdatedAt).orderByDesc(SysAccountAppKey::getCreatedAt);
        List<SysAccountAppKey> keys = appKeyMapper.selectList(wrapper);
        keys.forEach(this::mask);
        return keys;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccountAppKey create(SysAccountAppKey input) {
        if (input == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "密钥内容不能为空");
        }
        String name = requireText(input.getName(), "密钥名称");
        String plainSecret = randomToken(SECRET_PREFIX, SECRET_TOKEN_LENGTH);
        long now = System.currentTimeMillis();
        SysAccountAppKey key = new SysAccountAppKey();
        key.setAppKeyId(UUID.randomUUID().toString().replace("-", ""));
        key.setName(name);
        key.setAppKey(nextAppKey());
        key.setSecret(secretCodec.encrypt(plainSecret));
        key.setOwner(resolveOwner(input.getOwner()));
        key.setStatus(input.getStatus() == null ? 1 : input.getStatus());
        key.setExpireAt(input.getExpireAt());
        key.setIpWhitelist(trimToNull(input.getIpWhitelist()));
        key.setRemark(trimToNull(input.getRemark()));
        key.setDeleted(0);
        key.setCreatedAt(now);
        key.setUpdatedAt(now);
        appKeyMapper.insert(key);
        log.info("[account] 新增访问密钥: id={}, appKey={}, owner={}",
                key.getAppKeyId(), key.getAppKey(), key.getOwner());
        mask(key);
        key.setPlainSecret(plainSecret);
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccountAppKey update(SysAccountAppKey input) {
        if (input == null || StringUtils.isBlank(input.getAppKeyId())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "密钥标识不能为空");
        }
        SysAccountAppKey existing = requireKey(input.getAppKeyId());
        assertWritable(existing);
        existing.setName(requireText(input.getName(), "密钥名称"));
        existing.setStatus(input.getStatus() == null ? existing.getStatus() : input.getStatus());
        existing.setExpireAt(input.getExpireAt());
        existing.setIpWhitelist(trimToNull(input.getIpWhitelist()));
        existing.setRemark(trimToNull(input.getRemark()));
        existing.setUpdatedAt(System.currentTimeMillis());
        appKeyMapper.updateById(existing);
        log.info("[account] 更新访问密钥: id={}", existing.getAppKeyId());
        return mask(existing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccountAppKey rotate(String appKeyId) {
        SysAccountAppKey existing = requireKey(appKeyId);
        assertWritable(existing);
        String plainSecret = randomToken(SECRET_PREFIX, SECRET_TOKEN_LENGTH);
        existing.setSecret(secretCodec.encrypt(plainSecret));
        existing.setUpdatedAt(System.currentTimeMillis());
        appKeyMapper.updateById(existing);
        log.info("[account] 轮换访问密钥: id={}", existing.getAppKeyId());
        mask(existing);
        existing.setPlainSecret(plainSecret);
        return existing;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String appKeyId) {
        SysAccountAppKey existing = requireKey(appKeyId);
        assertWritable(existing);
        int rows = appKeyMapper.deleteById(appKeyId);
        log.info("[account] 删除访问密钥: id={}, rows={}", appKeyId, rows);
        return rows > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean authenticate(String appKey, String secret, String remoteIp) {
        if (StringUtils.isBlank(appKey) || StringUtils.isBlank(secret)) {
            return false;
        }
        SysAccountAppKey key = appKeyMapper.selectOne(Wrappers.<SysAccountAppKey>lambdaQuery()
                .eq(SysAccountAppKey::getAppKey, appKey.trim())
                .last("limit 1"));
        if (key == null || !Integer.valueOf(1).equals(key.getStatus())) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (key.getExpireAt() != null && key.getExpireAt() > 0 && key.getExpireAt() <= now) {
            log.warn("[account] 访问密钥已过期: appKey={}", appKey);
            return false;
        }
        if (!ipAllowed(key.getIpWhitelist(), remoteIp)) {
            log.warn("[account] 访问密钥 IP 不在白名单: appKey={}, ip={}", appKey, remoteIp);
            return false;
        }
        String expected = secretCodec.decrypt(key.getSecret());
        if (expected == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        SysAccountAppKey touch = new SysAccountAppKey();
        touch.setAppKeyId(key.getAppKeyId());
        touch.setLastUsedAt(now);
        appKeyMapper.updateById(touch);
        return true;
    }

    /**
     * 按标识取密钥，不存在时抛 404。
     *
     * @param appKeyId 密钥标识
     * @return 密钥（敏感字段未清空）
     */
    private SysAccountAppKey requireKey(String appKeyId) {
        if (StringUtils.isBlank(appKeyId)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "密钥标识不能为空");
        }
        SysAccountAppKey key = appKeyMapper.selectById(appKeyId);
        if (key == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "密钥不存在: " + appKeyId);
        }
        return key;
    }

    /**
     * 限制当前用户只能改自己创建的密钥；部署级密钥所有人可改。
     *
     * @param key 密钥
     */
    private void assertWritable(SysAccountAppKey key) {
        String owner = key.getOwner();
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
    private void applyOwnership(LambdaQueryWrapper<SysAccountAppKey> wrapper) {
        String currentUserId = AccountUserContext.currentUserId();
        if (StringUtils.isBlank(currentUserId)) {
            wrapper.isNull(SysAccountAppKey::getOwner);
            return;
        }
        wrapper.and(w -> w.isNull(SysAccountAppKey::getOwner)
                .or().eq(SysAccountAppKey::getOwner, currentUserId));
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
     * 生成不重复的访问 Key。
     *
     * @return 访问 Key
     */
    private String nextAppKey() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = randomToken(APP_KEY_PREFIX, APP_KEY_LENGTH);
            if (appKeyMapper.selectCount(Wrappers.<SysAccountAppKey>lambdaQuery()
                    .eq(SysAccountAppKey::getAppKey, candidate)) == 0) {
                return candidate;
            }
        }
        throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "生成访问 Key 失败，请重试");
    }

    /**
     * 生成随机令牌。
     *
     * @param prefix 前缀
     * @param bytes  随机字节数
     * @return 前缀 + Base64URL（无填充）
     */
    private String randomToken(String prefix, int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /**
     * 校验 IP 白名单。
     *
     * @param whitelist 逗号分隔，支持 {@code *} 与 {@code 192.168.*} 前缀
     * @param remoteIp  调用方 IP
     * @return true 表示允许
     */
    private boolean ipAllowed(String whitelist, String remoteIp) {
        if (StringUtils.isBlank(whitelist)) {
            return true;
        }
        if (StringUtils.isBlank(remoteIp)) {
            return false;
        }
        for (String entry : whitelist.split(",")) {
            String pattern = entry.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            if ("*".equals(pattern) || pattern.equalsIgnoreCase(remoteIp)) {
                return true;
            }
            if (pattern.endsWith("*") && remoteIp.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清空密文，避免接口回显。
     *
     * @param key 密钥
     * @return 同一实例
     */
    private SysAccountAppKey mask(SysAccountAppKey key) {
        if (key == null) {
            return null;
        }
        key.setSecret(null);
        return key;
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
}

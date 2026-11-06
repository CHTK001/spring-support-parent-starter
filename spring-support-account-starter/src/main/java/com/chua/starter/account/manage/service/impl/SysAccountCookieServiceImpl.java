package com.chua.starter.account.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.manage.mapper.SysAccountCookieMapper;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.SysAccountCookieService;
import com.chua.starter.account.manage.store.SysAccountCookie;
import com.chua.starter.account.support.AccountUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 账号模块 Cookie 池服务实现。
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAccountCookieServiceImpl implements SysAccountCookieService {

    /**
     * Cookie 映射器
     */
    private final SysAccountCookieMapper cookieMapper;

    /**
     * 敏感值编解码器
     */
    private final AccountSecretCodec secretCodec;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SysAccountCookie> list(String domain, String source, String keyword) {
        LambdaQueryWrapper<SysAccountCookie> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(domain)) {
            wrapper.eq(SysAccountCookie::getDomain, domain.trim());
        }
        if (StringUtils.isNotBlank(source)) {
            wrapper.eq(SysAccountCookie::getSource, source.trim());
        }
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(SysAccountCookie::getName, keyword.trim());
        }
        applyOwnership(wrapper);
        wrapper.orderByDesc(SysAccountCookie::getUpdatedAt).orderByDesc(SysAccountCookie::getCreatedAt);
        List<SysAccountCookie> cookies = cookieMapper.selectList(wrapper);
        cookies.forEach(this::mask);
        return cookies;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SysAccountCookie get(String cookieId) {
        return mask(requireCookie(cookieId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccountCookie create(SysAccountCookie input) {
        if (input == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Cookie 内容不能为空");
        }
        long now = System.currentTimeMillis();
        SysAccountCookie cookie = new SysAccountCookie();
        cookie.setCookieId(UUID.randomUUID().toString().replace("-", ""));
        cookie.setName(requireText(input.getName(), "Cookie 名称"));
        cookie.setDomain(trimToNull(input.getDomain()));
        cookie.setPath(StringUtils.isBlank(input.getPath()) ? "/" : input.getPath().trim());
        cookie.setCookieValue(secretCodec.encrypt(trimToNull(input.getCookieValue())));
        cookie.setOwner(resolveOwner(input.getOwner()));
        cookie.setStatus(input.getStatus() == null ? 1 : input.getStatus());
        cookie.setExpireAt(input.getExpireAt());
        cookie.setSource(trimToNull(input.getSource()));
        cookie.setRemark(trimToNull(input.getRemark()));
        cookie.setDeleted(0);
        cookie.setCreatedAt(now);
        cookie.setUpdatedAt(now);
        cookieMapper.insert(cookie);
        log.info("[account] 新增 Cookie: id={}, name={}, domain={}, owner={}",
                cookie.getCookieId(), cookie.getName(), cookie.getDomain(), cookie.getOwner());
        return mask(cookie);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccountCookie update(SysAccountCookie input) {
        if (input == null || StringUtils.isBlank(input.getCookieId())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Cookie 标识不能为空");
        }
        SysAccountCookie existing = requireCookie(input.getCookieId());
        assertWritable(existing);
        existing.setName(requireText(input.getName(), "Cookie 名称"));
        existing.setDomain(trimToNull(input.getDomain()));
        existing.setPath(StringUtils.isBlank(input.getPath()) ? "/" : input.getPath().trim());
        existing.setStatus(input.getStatus() == null ? existing.getStatus() : input.getStatus());
        existing.setExpireAt(input.getExpireAt());
        existing.setSource(trimToNull(input.getSource()));
        existing.setRemark(trimToNull(input.getRemark()));
        String value = trimToNull(input.getCookieValue());
        if (value != null && !secretCodec.isMasked(value)) {
            existing.setCookieValue(secretCodec.encrypt(value));
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        cookieMapper.updateById(existing);
        log.info("[account] 更新 Cookie: id={}", existing.getCookieId());
        return mask(existing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String cookieId) {
        SysAccountCookie existing = requireCookie(cookieId);
        assertWritable(existing);
        int rows = cookieMapper.deleteById(cookieId);
        log.info("[account] 删除 Cookie: id={}, rows={}", cookieId, rows);
        return rows > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveValue(String cookieId) {
        if (StringUtils.isBlank(cookieId)) {
            return null;
        }
        SysAccountCookie cookie = cookieMapper.selectById(cookieId);
        return cookie == null ? null : secretCodec.decrypt(cookie.getCookieValue());
    }

    /**
     * 按标识取 Cookie，不存在时抛 404。
     *
     * @param cookieId Cookie 标识
     * @return Cookie（内容未清空）
     */
    private SysAccountCookie requireCookie(String cookieId) {
        if (StringUtils.isBlank(cookieId)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Cookie 标识不能为空");
        }
        SysAccountCookie cookie = cookieMapper.selectById(cookieId);
        if (cookie == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "Cookie 不存在: " + cookieId);
        }
        return cookie;
    }

    /**
     * 限制当前用户只能改自己创建的 Cookie；部署级 Cookie 所有人可改。
     *
     * @param cookie Cookie
     */
    private void assertWritable(SysAccountCookie cookie) {
        String owner = cookie.getOwner();
        if (StringUtils.isBlank(owner)) {
            return;
        }
        if (!owner.equals(AccountUserContext.currentUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权操作该 Cookie");
        }
    }

    /**
     * 归属隔离：个人 Cookie 只对本人可见，部署级 Cookie 所有人可见。
     *
     * @param wrapper 查询条件
     */
    private void applyOwnership(LambdaQueryWrapper<SysAccountCookie> wrapper) {
        String currentUserId = AccountUserContext.currentUserId();
        if (StringUtils.isBlank(currentUserId)) {
            wrapper.isNull(SysAccountCookie::getOwner);
            return;
        }
        wrapper.and(w -> w.isNull(SysAccountCookie::getOwner)
                .or().eq(SysAccountCookie::getOwner, currentUserId));
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
     * 清空内容，保留「是否已配置」标记。
     *
     * @param cookie Cookie
     * @return 同一实例
     */
    private SysAccountCookie mask(SysAccountCookie cookie) {
        if (cookie == null) {
            return null;
        }
        cookie.setHasValue(StringUtils.isNotBlank(cookie.getCookieValue()));
        cookie.setCookieValue(null);
        return cookie;
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

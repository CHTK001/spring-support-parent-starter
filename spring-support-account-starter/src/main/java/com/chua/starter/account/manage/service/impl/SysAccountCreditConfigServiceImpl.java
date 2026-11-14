package com.chua.starter.account.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.manage.mapper.SysAccountCreditConfigMapper;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.SysAccountCreditConfigService;
import com.chua.starter.account.manage.store.SysAccountCreditConfig;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.support.CreditQueries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 上游余额查询配置服务实现。
 *
 * <p>配置落本模块自己的表，查询交给 datasearch 的
 * {@code CreditQueryProvider} SPI：本类只负责「配置怎么存」与「拼成一次请求」，
 * 各家接口的地址 / 字段口径都在 SPI 实现里，后续加一家不用改这里。</p>
 *
 * <p>{@code token} 落库加密，读接口只回 {@code hasToken}；编辑时留空或掩码表示不修改。</p>
 *
 * @author CH
 * @since 4.0.0.82
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAccountCreditConfigServiceImpl implements SysAccountCreditConfigService {

    /**
     * 查询实现标识：厂商官方口径（地址与表达式都可由内置口径补齐）
     */
    private static final String KIND_HOST = "host";

    /**
     * 配置映射器
     */
    private final SysAccountCreditConfigMapper configMapper;

    /**
     * 敏感值编解码器
     */
    private final AccountSecretCodec secretCodec;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SysAccountCreditConfig> list() {
        LambdaQueryWrapper<SysAccountCreditConfig> wrapper = Wrappers.lambdaQuery();
        wrapper.orderByDesc(SysAccountCreditConfig::getUpdatedAt);
        List<SysAccountCreditConfig> rows = configMapper.selectList(wrapper);
        rows.forEach(this::mask);
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SysAccountCreditConfig get(String providerId) {
        return mask(configMapper.selectById(requireProvider(providerId)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAccountCreditConfig save(String providerId, SysAccountCreditConfig input) {
        String id = requireProvider(providerId);
        SysAccountCreditConfig body = input == null ? new SysAccountCreditConfig() : input;
        assertUsable(body);
        long now = System.currentTimeMillis();
        SysAccountCreditConfig existing = configMapper.selectById(id);
        SysAccountCreditConfig target = existing == null ? new SysAccountCreditConfig() : existing;
        target.setProviderId(id);
        target.setKind(trimToNull(body.getKind()));
        target.setUrl(trimToNull(body.getUrl()));
        target.setMethod(upperToNull(body.getMethod()));
        target.setTokenHeader(trimToNull(body.getTokenHeader()));
        target.setExpression(trimToNull(body.getExpression()));
        target.setBilling(lowerToNull(body.getBilling()));
        target.setStatus(body.getStatus() == null
                ? (existing == null ? Integer.valueOf(1) : existing.getStatus()) : body.getStatus());
        target.setRemark(trimToNull(body.getRemark()));
        String token = trimToNull(body.getToken());
        if (token != null && !secretCodec.isMasked(token)) {
            target.setToken(secretCodec.encrypt(token));
        }
        String accessKey = trimToNull(body.getAccessKey());
        if (accessKey != null && !secretCodec.isMasked(accessKey)) {
            target.setAccessKey(secretCodec.encrypt(accessKey));
        }
        String secretKey = trimToNull(body.getSecretKey());
        if (secretKey != null && !secretCodec.isMasked(secretKey)) {
            target.setSecretKey(secretCodec.encrypt(secretKey));
        }
        if (existing == null) {
            target.setDeleted(0);
            target.setCreatedAt(now);
        }
        target.setUpdatedAt(now);
        if (existing == null) {
            configMapper.insert(target);
        } else {
            configMapper.updateById(target);
        }
        log.info("[account] 保存余额查询配置: provider={}, kind={}, url={}",
                id, target.getKind(), target.getUrl());
        return mask(target);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String providerId) {
        String id = requireProvider(providerId);
        int rows = configMapper.deleteById(id);
        log.info("[account] 删除余额查询配置: provider={}, rows={}", id, rows);
        return rows > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CreditQuery queryOf(String providerId) {
        if (StringUtils.isBlank(providerId)) {
            return null;
        }
        SysAccountCreditConfig row = configMapper.selectById(providerId.trim());
        if (row == null || row.getStatus() == null || row.getStatus() != 1) {
            return null;
        }
        return toQuery(row.getProviderId(), row,
                secretCodec.decrypt(row.getToken()),
                secretCodec.decrypt(row.getAccessKey()),
                secretCodec.decrypt(row.getSecretKey()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CreditResult test(SysAccountCreditConfig input) {
        if (input == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "配置内容不能为空");
        }
        String providerId = requireProvider(input.getProviderId());
        assertUsable(input);
        SysAccountCreditConfig saved = configMapper.selectById(providerId);
        // 试查不落库：令牌 / AK / SK 留空或掩码时沿用已存密文（解密后传进查询）
        String token = resolvePlain(input.getToken(), saved == null ? null : saved.getToken());
        String accessKey = resolvePlain(input.getAccessKey(), saved == null ? null : saved.getAccessKey());
        String secretKey = resolvePlain(input.getSecretKey(), saved == null ? null : saved.getSecretKey());
        CreditResult result = CreditQueries.query(toQuery(providerId, input, token, accessKey, secretKey));
        log.info("[account] 试查余额: provider={}, ok={}, error={}",
                providerId, result.isOk(), result.getError());
        return result;
    }

    /**
     * 把配置转成查询请求。
     *
     * @param providerId 上游 id
     * @param row        配置（可含请求字段）
     * @param token      令牌明文（可空）
     * @param accessKey  Access Key 明文（可空，AK/SK 签名类用）
     * @param secretKey  Secret Key 明文（可空，AK/SK 签名类用）
     * @return 查询请求
     */
    private CreditQuery toQuery(String providerId, SysAccountCreditConfig row,
                                String token, String accessKey, String secretKey) {
        CreditQuery query = new CreditQuery();
        query.setKind(trimToNull(row.getKind()));
        query.setProviderId(providerId);
        query.setUrl(trimToNull(row.getUrl()));
        query.setMethod(trimToNull(row.getMethod()));
        query.setToken(token);
        query.setTokenHeader(trimToNull(row.getTokenHeader()));
        query.setAccessKey(accessKey);
        query.setSecretKey(secretKey);
        query.setExpression(trimToNull(row.getExpression()));
        query.setBilling(trimToNull(row.getBilling()));
        return query;
    }

    /**
     * 取某个敏感字段的明文：表单给了明文（非掩码）就用表单值，否则取已存值并解密。
     *
     * @param input  表单值
     * @param stored 已存值（可能是密文，可空）
     * @return 明文；都没给时返回 {@code null}
     */
    private String resolvePlain(String input, String stored) {
        String text = trimToNull(input);
        if (text != null && !secretCodec.isMasked(text)) {
            return text;
        }
        return secretCodec.decrypt(stored);
    }

    /**
     * 保存 / 试查前判断这条配置是否能查。
     *
     * <p>通用表达式要求地址与表达式都给出；厂商口径（{@code host}）可以只填上游 id，
     * 地址与字段由内置口径补齐，页面给了就覆盖。</p>
     *
     * @param config 配置
     */
    private void assertUsable(SysAccountCreditConfig config) {
        String kind = config.getKind() == null ? "" : config.getKind().trim();
        boolean host = KIND_HOST.equalsIgnoreCase(kind);
        if (StringUtils.isBlank(config.getUrl()) && !host) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "余额接口地址不能为空");
        }
        if (StringUtils.isBlank(config.getExpression()) && !host) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "字段表达式不能为空（例如 $data.total_available / 500000）");
        }
    }

    /**
     * 校验并归一上游 id。
     *
     * @param providerId 上游 id
     * @return 去空白后的 id
     */
    private String requireProvider(String providerId) {
        String id = trimToNull(providerId);
        if (id == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "上游 provider id 不能为空");
        }
        return id;
    }

    /**
     * 清空令牌，保留「是否已配置」标记。
     *
     * @param config 配置
     * @return 同一实例
     */
    private SysAccountCreditConfig mask(SysAccountCreditConfig config) {
        if (config == null) {
            return null;
        }
        config.setHasToken(StringUtils.isNotBlank(config.getToken()));
        config.setToken(null);
        config.setHasAccessKey(StringUtils.isNotBlank(config.getAccessKey()));
        config.setAccessKey(null);
        config.setHasSecretKey(StringUtils.isNotBlank(config.getSecretKey()));
        config.setSecretKey(null);
        return config;
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

    /**
     * 转大写，空串返回 {@code null}。
     *
     * @param value 值
     * @return 大写值
     */
    private String upperToNull(String value) {
        String text = trimToNull(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    /**
     * 转小写，空串返回 {@code null}。
     *
     * @param value 值
     * @return 小写值
     */
    private String lowerToNull(String value) {
        String text = trimToNull(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }
}

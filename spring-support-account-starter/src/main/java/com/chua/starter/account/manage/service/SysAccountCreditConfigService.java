package com.chua.starter.account.manage.service;

import com.chua.starter.account.manage.store.SysAccountCreditConfig;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;

import java.util.List;

/**
 * 上游余额查询配置服务。
 *
 * <p>页面在这里维护「某上游的余额去哪查」，网关额度卡读不到时由
 * {@code MagpieDbCreditProvider} 取 {@link #queryOf(String)} 的结果去查。</p>
 *
 * @author CH
 * @since 4.0.0.82
 */
public interface SysAccountCreditConfigService {

    /**
     * 全部配置（令牌已清空，只留 {@code hasToken}）。
     *
     * @return 配置列表，按更新时间倒序
     */
    List<SysAccountCreditConfig> list();

    /**
     * 单条配置。
     *
     * @param providerId 上游 id
     * @return 配置；不存在返回 {@code null}
     */
    SysAccountCreditConfig get(String providerId);

    /**
     * 新增或覆盖一条配置（按 provider id upsert）。
     *
     * <p>{@code token} 留空或为掩码表示保留原值。</p>
     *
     * @param providerId 上游 id
     * @param input      配置内容
     * @return 落库后的配置（令牌已清空）
     */
    SysAccountCreditConfig save(String providerId, SysAccountCreditConfig input);

    /**
     * 删除一条配置（逻辑删除）。
     *
     * @param providerId 上游 id
     * @return 是否删除成功
     */
    boolean delete(String providerId);

    /**
     * 取出可直接执行的查询请求（令牌已解密）。
     *
     * <p>没有配置或配置停用时返回 {@code null}，调用方据此判断「不接管」。</p>
     *
     * @param providerId 上游 id
     * @return 查询请求；无可用配置返回 {@code null}
     */
    CreditQuery queryOf(String providerId);

    /**
     * 试查一次：不落库，直接按传入内容（令牌可留空，表示用已存的）执行查询。
     *
     * @param input 配置内容
     * @return 查询结果；失败时 {@code error} 有值
     */
    CreditResult test(SysAccountCreditConfig input);
}

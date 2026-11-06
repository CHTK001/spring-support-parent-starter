package com.chua.starter.account.manage.service;

import com.chua.starter.account.manage.store.SysAccountCookie;

import java.util.List;

/**
 * 账号模块 Cookie 池服务。
 *
 * <p>免登录渠道（如 Trae / DuckDuckGo）与会话型站点的 Cookie 统一存放于此，
 * 由渠道实现按 {@code domain} / {@code source} 读取；内容加密落库，接口不回显。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface SysAccountCookieService {

    /**
     * 查询当前用户可见的 Cookie。
     *
     * @param domain  作用域域名（可空）
     * @param source  来源（可空）
     * @param keyword 名称模糊词（可空）
     * @return Cookie 列表（不含内容，仅 {@code hasValue}）
     */
    List<SysAccountCookie> list(String domain, String source, String keyword);

    /**
     * 按标识查询 Cookie（不含内容）。
     *
     * @param cookieId Cookie 标识
     * @return Cookie；不存在返回 {@code null}
     */
    SysAccountCookie get(String cookieId);

    /**
     * 新增 Cookie。
     *
     * @param input 内容（{@code cookieValue} 为明文，服务层负责加密）
     * @return 落库后的 Cookie
     */
    SysAccountCookie create(SysAccountCookie input);

    /**
     * 更新 Cookie；{@code cookieValue} 留空或掩码时保留原值。
     *
     * @param input 必须带 {@code cookieId}
     * @return 更新后的 Cookie
     */
    SysAccountCookie update(SysAccountCookie input);

    /**
     * 逻辑删除 Cookie。
     *
     * @param cookieId Cookie 标识
     * @return 是否删除成功
     */
    boolean delete(String cookieId);

    /**
     * 解密 Cookie 内容，供渠道实现内部调用。
     *
     * @param cookieId Cookie 标识
     * @return 明文内容；不存在或未配置时返回 {@code null}
     */
    String resolveValue(String cookieId);
}

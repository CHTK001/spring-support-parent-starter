package com.chua.starter.account.spi;

/**
 * 账号类型。
 *
 * <p>区分同一渠道上两种语义完全不同的账号：</p>
 * <ul>
 *   <li>{@link #FIXED}：固定账号。长期绑定 / 授权获得凭据，可刷新、可探活、可持久化，
 *       对应本模块统一账号（{@code sys_account}）与 AI 自己的连接表。</li>
 *   <li>{@link #TEMPORARY}：临时账号。一次性的随机虚拟身份（接码 / 收信），用完即弃，
 *       对应渠道管理的临时身份能力。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
public enum AccountType {

    /**
     * 固定账号：长期绑定的凭据。
     */
    FIXED,

    /**
     * 临时账号：一次性的随机虚拟身份。
     */
    TEMPORARY
}

package com.chua.starter.datasearch.credit.spi;

import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;

/**
 * 余额查询扩展接口。
 *
 * <p>各家「能查到余额」的地址、鉴权方式、返回结构都不一样，这里把差异收进实现：
 * 调用方只给 {@link CreditQuery}，实现负责拼地址、带令牌、取字段。新增一家只要加一个
 * 实现并在 {@code META-INF/services} 里登记，其它模块无需改动。</p>
 *
 * <p>实现约定：查不到不许抛异常，返回 {@link CreditResult#fail(String, String)}。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
public interface CreditQueryProvider {

    /**
     * 实现标识，与 {@link CreditQuery#getKind()} 对应。
     *
     * @return 实现标识
     */
    String name();

    /**
     * 判断本次请求是否归本实现处理。
     *
     * @param query 请求
     * @return 能处理时为 true
     */
    default boolean supports(CreditQuery query) {
        return query != null && name().equalsIgnoreCase(query.getKind());
    }

    /**
     * 执行余额查询。
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    CreditResult query(CreditQuery query);

    /**
     * 「账号登录」入口页地址。
     *
     * <p>余额令牌可以由用户在官方登录页里登录后自动截获的实现（如 Agnes）提供它；
     * 页面据此决定是否展示「账号登录」按钮。默认不支持。</p>
     *
     * @return 登录页地址；不支持账号登录时为 null
     */
    default String loginPageUrl() {
        return null;
    }

    /**
     * 「账号登录」入口页地址（按站点根地址解析）。
     *
     * <p>自建 / 多站点部署的平台（如 sub2api）登录页地址取决于供应商行里配置的
     * 上游地址，写不成常量：实现按该地址推出登录页。登录页固定的实现
     * （如 DeepSeek / Agnes）无需关心站点地址，默认回落到 {@link #loginPageUrl()}。</p>
     *
     * @param baseUrl 供应商行配置的上游根地址（可能带 {@code /api/v1} 之类接口前缀）
     * @return 登录页地址；不支持账号登录或地址不足以推出登录页时为 null
     */
    default String loginPageUrl(String baseUrl) {
        return loginPageUrl();
    }

    /**
     * 「账号登录」成功后，余额密钥所在的 Cookie 名。
     *
     * <p>余额密钥是登录态 Cookie 的实现（如 Moss）提供它：内嵌浏览器在页面
     * Cookie 里读到该字段即认为登录成功，其值自动回填「余额密钥」。
     * 默认不走 Cookie，由页面响应体截获令牌（如 Agnes）。</p>
     *
     * @return Cookie 名；不走 Cookie 截获时为 null
     */
    default String loginCookieName() {
        return null;
    }

    /**
     * 「账号登录」要截获的响应地址特征（正则，忽略大小写）。
     *
     * <p>余额令牌不在 {@code access_token} 字段里的实现（如 DeepSeek 的令牌在
     * {@code data.biz_data.user.token}）用它圈定要截获的登录响应：内嵌浏览器把地址
     * 命中该特征的响应体暂存起来。为 null 时按默认口径截获（响应体含
     * {@code access_token} 的登录 / 令牌 / 会话类响应）。</p>
     *
     * @return 响应地址特征；用默认口径时为 null
     */
    default String loginCaptureUrlPattern() {
        return null;
    }

    /**
     * 「账号登录」响应体里余额令牌的 JSON 路径（点分，如 {@code data.biz_data.user.token}）。
     *
     * <p>配合 {@link #loginCaptureUrlPattern()} 使用：截获的响应体按该路径取令牌，
     * 路径上的每一段都按对象字段下钻。为 null 时在响应体里递归找
     * {@code access_token} 字段（并用兜底正则再试一次）。</p>
     *
     * @return 令牌 JSON 路径；用默认口径时为 null
     */
    default String loginTokenJsonPath() {
        return null;
    }

    /**
     * 「账号登录」成功后，余额令牌所在的 localStorage 键名。
     *
     * <p>有些平台（如 SenseNova）的令牌交换响应体里只有不透明串，真正的 JWT 会由
     * 前端 SDK 写进页面 localStorage：内嵌浏览器轮询读取该键即认为登录成功，其值
     * 自动回填「余额密钥」。值为 JSON 串时按 {@code token} / {@code access_token}
     * 字段再解一层。默认不走 localStorage。</p>
     *
     * @return localStorage 键名；不走 localStorage 截获时为 null
     */
    default String loginStorageKey() {
        return null;
    }

    /**
     * 是否支持余额令牌静默续期。
     *
     * <p>可续期的前提：令牌由页面登录态派生（Cookie / localStorage），带令牌回访
     * 上游门户时页面会话仍然有效，平台轮换令牌后能按登录截获钩子读到新值。
     * 默认不支持——这类实现的令牌失效后只能人工重新点「登录」。</p>
     *
     * @return 支持静默续期时为 true
     */
    default boolean supportsTokenRenewal() {
        return false;
    }

    /**
     * 静默续期的回访页地址（按站点根地址解析）。
     *
     * <p>续期时内嵌无头浏览器预置旧令牌后打开本页：页面 SDK 认会话，若平台在
     * 回访时轮换令牌，新令牌按登录截获钩子（Cookie / localStorage / 响应截获）
     * 读到并回填。默认回落登录页；实现给出更合适的已登录落地页（控制台首页等）
     * 时覆写它——登录页在会话有效时可能原地不动，不利于触发令牌轮换。</p>
     *
     * @param baseUrl 供应商行配置的上游根地址
     * @return 回访页地址；无法推出时为 null（回落登录页语义由调用方处理）
     */
    default String renewPageUrl(String baseUrl) {
        return loginPageUrl(baseUrl);
    }
}

package com.chua.starter.oauth.server.support.parser;

import com.chua.common.support.lang.code.ReturnResult;

/**
 * 鉴权信息
 *
 * @author CH
 */
public interface Authorization {
    /**
     * 是否存在token/cookie
     *
     * @return 是否存在token/cookie
     */
    default boolean hasTokenOrCookie() {
        return hasCookie() || hasToken();
    }

    /**
     * 是否存在cookie
     *
     * @return 是否存在cookie
     */
    boolean hasCookie();

    /**
     * 是否存在token
     *
     * @return 是否存在token
     */
    boolean hasToken();

    /**
     * 是否存在 ak, sk
     *
     * @return 是否存在 ak, sk
     */
    boolean hasKey();

    /**
     * 检测信息
     *
     * @return 检测信息
     */
    boolean check();

    /**
     * 认证
     *
     * @return 结果
     */
    ReturnResult<String> authentication();

    /**
     * 刷新缓存
     *
     * @return 刷新缓存
     */
    ReturnResult<String> upgrade();
}

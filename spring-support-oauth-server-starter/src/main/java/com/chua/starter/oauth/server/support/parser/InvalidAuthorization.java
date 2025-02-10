package com.chua.starter.oauth.server.support.parser;

import com.chua.common.support.lang.code.ReturnResult;

/**
 * 无效鉴权信息
 *
 * @author CH
 */
public final class InvalidAuthorization implements Authorization {

    public static final InvalidAuthorization INSTANCE = new InvalidAuthorization();

    @Override
    public boolean hasCookie() {
        return false;
    }

    @Override
    public boolean hasToken() {
        return false;
    }

    @Override
    public boolean hasKey() {
        return false;
    }

    @Override
    public boolean check() {
        return false;
    }

    @Override
    public ReturnResult<String> authentication(boolean encipher) {
        return ReturnResult.noAuth();
    }

    @Override
    public ReturnResult<String> upgrade(String address, String cookieName) {
        return ReturnResult.noAuth();
    }

    @Override
    public boolean hasRefreshToken() {
        return false;
    }
}

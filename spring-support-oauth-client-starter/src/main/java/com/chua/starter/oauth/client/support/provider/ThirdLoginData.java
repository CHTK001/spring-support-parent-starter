package com.chua.starter.oauth.client.support.provider;

import jakarta.validation.constraints.NotNull;

/**
 * 登录信息
 *
 * @author CH
 */
public record ThirdLoginData(@NotNull(message = "loginCode不能为空") String loginCode,
                              @NotNull(message = "callback不能为空") String callback,
                              @NotNull(message = "登录类型不能为空") String loginType,
                              String thirdType) {
}

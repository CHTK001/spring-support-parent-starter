package com.chua.starter.oauth.client.support.provider;

import jakarta.validation.constraints.NotNull;

/**
 * 登录信息
 *
 * @author CH
 * @param "loginCode不能为空" "login编码不能为空"
 * @return thirdlogin数据的结果
 */
public record ThirdLoginData(@NotNull(message = "loginCode不能为空") String loginCode,
                              @NotNull(message = "callback不能为空") String callback,
                              @NotNull(message = "登录类型不能为空") String loginType,
                              String thirdType) {
}

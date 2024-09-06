package com.chua.starter.oauth.client.support.provider;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 登录信息
 *
 * @author CH
 */
@Data
public class ThirdLoginData {

    @NotNull(message = "loginCode不能为空")
    private String loginCode;
    @NotNull(message = "callback不能为空")
    private String callback;
    @NotNull(message = "登录类型不能为空")
    private String loginType;

    private String thirdType;

}

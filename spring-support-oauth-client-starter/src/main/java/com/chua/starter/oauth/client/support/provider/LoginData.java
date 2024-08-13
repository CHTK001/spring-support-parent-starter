package com.chua.starter.oauth.client.support.provider;

import com.chua.starter.common.support.annotations.RequestParamMapping;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 登录信息
 *
 * @author CH
 */
@Data
public class LoginData {

    @NotNull(message = "账号不能为空")
    private String username;
    @NotNull(message = "密码不能为空")
    private String password;
    @NotNull(message = "校验码不能为空")
    @RequestParamMapping(name = {"verifyCode", "verifyCodeKey"})
    private String verifyCodeKey;
    private String verifyCodeUlid;
    private String loginType;
}

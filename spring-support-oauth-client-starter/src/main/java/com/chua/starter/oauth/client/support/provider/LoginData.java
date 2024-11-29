package com.chua.starter.oauth.client.support.provider;

import com.chua.starter.common.support.annotations.RequestParamMapping;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 登录信息
 *
 * @author CH
 */
@Data
@ApiModel(description = "登录信息")
public class LoginData {

    /**
     * 账号
     */
    @ApiModelProperty(value = "账号")
    @NotNull(message = "账号不能为空")
    private String username;

    /**
     * 密码
     */
    @ApiModelProperty(value = "密码(md5加密)")
    @NotNull(message = "密码不能为空")
    private String password;
    /**
     * 校验码
     */
    @ApiModelProperty(value = "校验码")
    @NotNull(message = "校验码不能为空")
    @RequestParamMapping(name = {"verifyCode", "verifyCodeKey"})
    private String verifyCodeKey;
    /**
     * 校验码
     */
    @ApiModelProperty(value = "校验码标识, 非必填")
    private String verifyCodeUlid;
    /**
     * 登录类型
     */
    @ApiModelProperty(value = "登录类型, 网页：WEB, 微信:WX")
    private String loginType;
}

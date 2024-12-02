package com.chua.starter.oauth.client.support.provider;

import com.chua.starter.common.support.annotations.RequestParamMapping;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 登录信息
 *
 * @author CH
 */
@Data
@ApiModel(description = "登录信息")
@Schema(description = "登录信息")
public class LoginData {

    /**
     * 账号
     */
    @ApiModelProperty(value = "账号")
    @Schema(description = "账号")
    @NotNull(message = "账号不能为空")
    private String username;

    /**
     * 密码
     */
    @ApiModelProperty(value = "密码(md5加密)")
    @NotNull(message = "密码不能为空")
    @Schema(description = "密码(md5加密)")
    private String password;
    /**
     * 校验码
     */
    @ApiModelProperty(value = "校验码; 由服务器决定是否必填")
    @NotNull(message = "校验码不能为空")
    @Schema(description = "校验码; 由服务器决定是否必填")
    @RequestParamMapping(name = {"verifyCode", "verifyCodeKey"})
    private String verifyCodeKey;
    /**
     * 校验码
     */
    @ApiModelProperty(value = "校验码标识, 非必填")
    @Schema(description = "校验码标识, 非必填")
    private String verifyCodeUlid;
    /**
     * 登录类型
     */
    @Schema(description = "登录类型, 网页：WEB, 微信:WX, 后管: SYSTEM, 小程序: MINI_APP等")
    @ApiModelProperty(value = "登录类型, 网页：WEB, 微信:WX, 后管: SYSTEM, 小程序: MINI_APP等")
    private String loginType;
}

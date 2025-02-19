package com.chua.starter.oauth.client.support.provider;

import com.chua.starter.common.support.annotations.RequestParamMapping;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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
    @ApiModelProperty(value = "账号/微信code/微信openId")
    @Schema(description = "账号/微信code/微信openId")
    private String username;

    /**
     * 密码
     */
    @ApiModelProperty(value = "密码(md5加密)/微信getphonenumber(code)")
    @Schema(description = "密码(md5加密)/微信getphonenumber(code")
    private String password;
    /**
     * 校验码
     */
    @ApiModelProperty(value = "校验码; 由服务器决定是否必填")
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
    @ApiModelProperty(value = "登录类型, 网页：WEB, 微信:WX, 后管: SYSTEM, 小程序: WX_MINI_APP, WX_MINI_APP_VISITOR等")
    private String loginType;


    /**
     * 账号类型
     */
    @ApiModelProperty(value = "账号类型, 用于区分同一种登录方式")
    @Schema(description = "账号类型, 用于区分同一种登录方式")
    private String accountType;


    /**
     * 租户ID
     */
    @ApiModelProperty(value = "租户ID")
    @Schema(description = "租户ID")
    private String tenantId;
    /**
     * 谷歌验证码
     */
//    @ApiModelProperty(value = "谷歌/其它验证码")
//    @Schema(description = "谷歌/其它验证码")
//    private String totpCode;
    /**
     * 登录凭证
     */
    @ApiModelProperty(value = "登录凭证")
    @Schema(description = "登录凭证(加密数据)")
    private String wxEncryptedData;

    /**
     * 登录凭证
     */
    @ApiModelProperty(value = "登录凭证")
    @Schema(description = "登录凭证(Iv)")
    private String wxIv;


    /**
     * 登录凭证
     */
    @ApiModelProperty(value = "登录凭证")
    @Schema(description = "登录凭证(会话Key)")
    private String wxSessionKey;
}

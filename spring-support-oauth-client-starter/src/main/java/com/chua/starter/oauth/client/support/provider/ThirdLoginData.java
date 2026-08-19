package com.chua.starter.oauth.client.support.provider;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 登录信息
 *
 * @author CH
 */
@Data
public class ThirdLoginData {

    @NotNull(message = "loginCode不能为空")
    /** Login代码 */
    private String loginCode;
    @NotNull(message = "callback不能为空")
    /** Callback */
    private String callback;
    @NotNull(message = "登录类型不能为空")
    /** Login类型 */
    private String loginType;

    /** Third类型 */
    private String thirdType;

}

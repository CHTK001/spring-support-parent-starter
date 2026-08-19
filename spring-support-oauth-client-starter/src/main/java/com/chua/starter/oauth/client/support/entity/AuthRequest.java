package com.chua.starter.oauth.client.support.entity;

import lombok.Data;

/**
 * 身份验证请求
 *
 * @since 2023/09/09
 * @author CH
 */
@Data
public class AuthRequest {

    /** 数据 */
    private String data;
    /** Username */
    private String username;
    /** 类型 */
    private String type;
    /** Passwd */
    private String passwd;
    /** 代码 */
    private String code;
    /** Redirect_url */
    private String redirect_url;
    /** 模式 */
    private String mode;
    /** Encipher */
    private boolean encipher = true;
    /** IFremember */
    private String ifRemember;
}

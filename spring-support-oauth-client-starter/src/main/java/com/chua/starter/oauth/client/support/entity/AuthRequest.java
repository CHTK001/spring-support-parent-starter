package com.chua.starter.oauth.client.support.entity;

import lombok.Data;

/**
 * 身份验证请求
 *
 * @author CH
 * @since 2023/09/09
 */
@Data
public class AuthRequest {

    private String data;
    private String username;
    private String type;
    private String passwd;
    private String code;
    private String redirect_url;
    private String mode;
    private boolean encipher = true;
    private String ifRemember;
}

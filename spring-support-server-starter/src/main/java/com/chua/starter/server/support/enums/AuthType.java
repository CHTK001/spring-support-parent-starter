package com.chua.starter.server.support.enums;

/**
 * 登录身份认证类型
 *
 * @author CH
 * @since 4.0.0.42
 */
public enum AuthType {

    /**
     * 用户名密码
    */
    PASSWORD,

    /**
     * SSH 公私钥（引用密钥库）
    */
    PUBLIC_KEY,

    /**
     * 免密登录：目标主机已配置平台公钥（authorized_keys）或允许空口令，
     * 平台侧仅登记登录用户名，不存密码/私钥；SSH 连接层以空口令身份尝试。
    */
    NONE
}

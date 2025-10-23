package com.chua.starter.oauth.client.support.entity;

import lombok.Builder;
import lombok.Data;

/**
 * 浪潮用户信息
 * @author CH
 * @since 2025/10/21 15:35
 */
@Data
public class WaveUserInfoV2Response {


    /**
     * 用户信息
     */
    private User user;

    /**
     * 租户信息
     */
    private Tenant tenant;


    @Data
    @Builder
    public static class User {

        /**
         * 用户sub
         */
        private String sub;

        /**
         * 用户appId
         */
        private String usersAppId;

        /**
         * 用户iss
         */
        private String iss;

        /**
         * 登录账号
         */
        private String loginAccountName;

        /**
         * 用户名称
         */
        private String loginLoginname;

        /**
         * 用户中文名称
         */
        private String loginUname;

        /**
         * 用户生效时间
         */
        private Long nbf;

        /**
         * 用户UID
         */
        private String loginUid;

        /**
         * 用户IP
         */
        private String clientIp;
        /**
         * 用户失效时间
         */
        private Long exp;

        /**
         * 用户账号ID
         */
        private String loginAccountId;

        /**
         * jti
         */
        private String jti;

        /**
         * 用户appId
         */
        private String loginAppId;
    }

    @Data
    @Builder
    public static class Tenant {

        private String tenantID;
        private String tenantPassword;
        private String tenantAccount;
    }
}

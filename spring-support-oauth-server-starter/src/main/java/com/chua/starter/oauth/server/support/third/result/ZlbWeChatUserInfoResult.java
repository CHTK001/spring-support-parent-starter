package com.chua.starter.oauth.server.support.third.result;

import lombok.Data;

/**
 * 浙里办微信小程序单点登录用户信息
 *
 * @author CH
 */
@Data
public class ZlbWeChatUserInfoResult {

    /**
     * 主键
     */
    private String userId;
    /**
     * 用户类型
     */
    private String userType;
    /**
     * 用户信息详情
     */
    private PersonInfo personInfo;

    /**
     * 用户信息详情
     */
    @Data
    public static class PersonInfo {
        /**
         * 主键
         */
        private String userId;

        /**
         * 个人姓名
         */
        private String userName;

        /**
         * 证件类型
         */
        private String idType;

        /**
         * 证件编号
         */
        private String idNo;
        /**
         * 手机号
         */
        private String phone;

        /**
         * 邮箱
         */
        private String email;
        /**
         * 性别
         */
        private String gender;
        /**
         * 生日
         */
        private String birthday;
    }
}

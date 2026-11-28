package com.chua.starter.oauth.client.support.provider;

import lombok.Data;

import java.util.Set;

/**
 * 用户登录视图对象
 *
 * @author haoxr
 * @since 2022/1/14
 * @author CH
 */
@Data
public class UserInfoVO {

    /**
     * 用户标识
    */
    private String userId;

    /**
     * 用户SEX
    */
    private Integer userSex;

    /**
     * 用户记号笔
    */
    private String userMarker;

    /**
     * 用户名称
    */
    private String userName;
    /**
     * 用户real名称
    */
    private String userRealName;

    /**
     * Nickname
    */
    private String nickname;
    /**
     * 用户gender
    */
    private String userGender;
    /**
     * 用户mobile
    */
    private String userMobile;
    /**
     * 用户email
    */
    private String userEmail;
    /**
     * 用户地址
    */
    private String userAddress;

    /**
     * Avatar
    */
    private String avatar;

    /**
     * 角色
    */
    private Set<String> roles;

    /**
     * Perms
    */
    private Set<String> perms;

}


package com.chua.starter.oauth.client.support.provider;

import lombok.Data;

import java.util.Set;

/**
 * 用户登录视图对象
 *
 * @author haoxr
 * @since 2022/1/14
 */
@Data
public class UserInfoVO {

    private String userId;

    private Integer userSex;

    private String userMarker;

    private String userName;
    private String userRealName;

    private String nickname;
    private String userGender;
    private String userMobile;
    private String userEmail;
    private String userAddress;

    private String avatar;

    private Set<String> roles;

    private Set<String> perms;

}

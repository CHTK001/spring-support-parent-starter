package com.chua.starter.oauth.client.support.oauth;

import com.chua.common.support.utils.BeanUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.oauth.client.support.user.UserResume;

/**
 * OAuth 权限服务
 *
 * @author CH
 */
public class OauthAuthService implements AuthService {

    @Override
    /** 获取CurrentUser */
    public CurrentUser getCurrentUser() {
        UserResume userInfo = com.chua.starter.oauth.client.support.execute.AuthSessionUtils.getUserInfo(UserResume.class);
        if (null == userInfo) {
            return null;
        }
        return BeanUtils.copyProperties(userInfo, CurrentUser.class);
    }
}

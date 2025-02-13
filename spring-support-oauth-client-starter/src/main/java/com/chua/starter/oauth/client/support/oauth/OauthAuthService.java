package com.chua.starter.oauth.client.support.oauth;

import com.chua.common.support.bean.BeanUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.user.UserResult;

/**
 * 权限服务
 * @author CH
 */
public class OauthAuthService implements AuthService {

    @Override
    public CurrentUser getCurrentUser() {
        UserResult userInfo = RequestUtils.getUserInfo(UserResult.class);
        if(null == userInfo) {
            return null;
        }
        return BeanUtils.copyProperties(userInfo, CurrentUser.class);
    }
}
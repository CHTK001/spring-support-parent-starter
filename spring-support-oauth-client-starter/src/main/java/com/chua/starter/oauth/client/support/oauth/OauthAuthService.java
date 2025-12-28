package com.chua.starter.oauth.client.support.oauth;

import com.chua.common.support.bean.BeanUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.util.Map;

/**
 * 权限服务
 * @author CH
 */
public class OauthAuthService implements AuthService {

    final static Map<UserResume, CurrentUser> CURRENT_USER = new ConcurrentReferenceHashMap<>(1024);

    @Override
    public CurrentUser getCurrentUser() {
        UserResume userInfo = RequestUtils.getUserInfo(UserResume.class);
        if(null == userInfo) {
            return null;
        }
        return CURRENT_USER.computeIfAbsent(userInfo, (key) -> BeanUtils.copyProperties(userInfo, CurrentUser.class));
    }
}

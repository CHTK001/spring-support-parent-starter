package com.chua.starter.oauth.client.support.oauth;

import com.chua.common.support.utils.BeanUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.user.UserResult;

/**
 * o认证 权限服务
 *
 * <p>身份优先取会话里的 {@code userInfo}；会话为空时回落到令牌解析结果。
 * 业务应用（如系统管理端）由 SSO 服务端的认证过滤器做无状态鉴权，
 * 令牌只落在请求里、不写会话，只读会话会让 {@code @Permission} 一律判成匿名
 * （表现为「权限不足，需要：xxx」而同一请求的 {@code @UserValue} 正常）。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public class OauthAuthService implements AuthService {

    @Override
    /**
     * 获取当前用户
     * @return 返回值
    */
    public CurrentUser getCurrentUser() {
        UserResume userInfo = com.chua.starter.oauth.client.support.execute.AuthSessionUtils.getUserInfo(UserResume.class);
        if (null != userInfo) {
            return BeanUtils.copyProperties(userInfo, CurrentUser.class);
        }
        // 会话里没有身份：按令牌再解析一次（会话鉴权与令牌鉴权并存的应用都走这里）
        UserResult userResult = AuthClientExecute.INSTANCE.getSafeUserResult();
        if (null == userResult) {
            return null;
        }
        return BeanUtils.copyProperties(userResult, CurrentUser.class);
    }
}

package com.chua.starter.tencent.support.user;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.oauth.client.support.user.UserInfoService;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.user.UserResult;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.util.StringUtils;

/**
 * 微信小程序用户信息服务
 *
 * @since 2026/07/20
 * @author CH
 */
@Spi("WECHAT_MINI")
public class WechatMiniUserInfoService implements UserInfoService {

    @Override
    /**
     * 校验Login
     * @param address 字符串
     * @param credential 字符串
     * @param ext 对象
     * @param principal 字符串
     * @return 返回值
    */
    public UserResume checkLogin(String principal, String credential, String address, Object ext) {
        String openid = principal;
        if (!StringUtils.hasText(openid)) {
            /**
             * 用户resume。
             * @param 不能为空" 不能为空"
             * @return 用户resume的结果
             */
            return new UserResume("openid 不能为空");
        }

        UserResume resume = new UserResume();
        resume.setOpenId(openid);
        resume.setLoginType("WECHAT_MINI");

        if (ext instanceof AuthUser authUser) {
            resume.setUsername(StringUtils.hasText(authUser.getUsername()) ? authUser.getUsername() : openid);
            resume.setUnionId(StringUtils.hasText(authUser.getUuid()) ? authUser.getUuid() : openid);
        } else {
            resume.setUsername(openid);
            resume.setUnionId(openid);
        }

        if (StringUtils.hasText(credential)) {
            resume.setPhone(credential);
        }

        return resume;
    }

    @Override
    /**
     * Upgrade
     * @param userResult 参数
     * @return 返回值
    */
    public UserResume upgrade(UserResult userResult) {
        return null;
    }
}

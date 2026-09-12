package com.chua.starter.aliyun.support.user;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.oauth.client.support.user.UserInfoService;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.user.UserResult;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
* 支付宝用户信息服务
*
* @since 2026/07/20
* @author CH
 */
@Spi("ALIPAY")
public class AlipayUserInfoService implements UserInfoService {

    @Override
    /** 校验Login */
    public UserResume checkLogin(String principal, String credential, String address, Object ext) {
        String alipayUserId = principal;
        if (!StringUtils.hasText(alipayUserId)) {
            /**
            * 用户resume。
            * @param "支付宝用户ID不能为空" "支付宝用户标识不能为空"
            * @return 用户resume的结果
             */
            return new UserResume("支付宝用户ID不能为空");
        }

        UserResume resume = new UserResume();
        resume.setOpenId(alipayUserId);
        resume.setLoginType("ALIPAY");

        if (ext instanceof AuthUser authUser) {
            resume.setUsername(StringUtils.hasText(authUser.getUsername()) ? authUser.getUsername() : alipayUserId);
            resume.setNickName(StringUtils.hasText(authUser.getNickname()) ? authUser.getNickname() : resume.getUsername());
            if (StringUtils.hasText(authUser.getEmail())) {
                Map<String, Object> extInfo = new HashMap<>();
                extInfo.put("email", authUser.getEmail());
                resume.setExt(extInfo);
            }
        } else {
            resume.setUsername(alipayUserId);
        }

        if (StringUtils.hasText(credential)) {
            resume.setPhone(credential);
        }

        return resume;
    }

    @Override
    /** Upgrade */
    public UserResume upgrade(UserResult userResult) {
        return null;
    }
}

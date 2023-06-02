package com.chua.starter.oauth.server.support.check;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.Md5Utils;
import com.chua.starter.common.support.logger.Logger;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.oauth.client.support.annotation.UserLoginType;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import com.chua.starter.oauth.server.support.service.UserInfoService;
import com.chua.starter.oauth.server.support.token.TokenResolver;
import com.google.common.base.Strings;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 登录检测
 *
 * @author CH
 */
public class LoginCheck {

    @Resource
    private AuthServerProperties authServerProperties;

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 是否匹配类型
     *
     * @param userInfoService 服务
     * @param authType        类型
     * @return 是否匹配类型
     */
    private boolean isMatch(UserInfoService userInfoService, String authType) {
        Class<? extends UserInfoService> aClass = userInfoService.getClass();
        UserLoginType userLoginType = aClass.getDeclaredAnnotation(UserLoginType.class);
        if (null == userLoginType) {
            return true;
        }
        String value = userLoginType.value();
        return null != value && value.equals(authType);
    }


    /**
     * 登入校验
     *
     * @param address  地址
     * @param username 用户名
     * @param passwd   密码
     */
    @Logger(value = "登陆模块", action = "登录", content =
            "T(com.chua.common.support.lang.date.DateTime).now().toStandard() + " +
                    "' 账号在( '" +
                    "+ #args[0] + " +
                    "' )' " +
                    " + #args[1] + " +
                    "'登录系统(状态: '" +
                    " + #result['code'] + " +
                    "'  ' " +
                    "  #result['msg'] + " +
                    "') 登录方式('" +
                    " + #args[3] + " +
                    "' ) '")
    public ReturnResult<LoginResult> doLogin(String address, String username, String passwd, String authType, Object ext) {
        UserResult userResult = null;
        Map<String, UserInfoService> beansOfType = applicationContext.getBeansOfType(UserInfoService.class);
        for (Map.Entry<String, UserInfoService> entry : beansOfType.entrySet()) {
            UserInfoService userInfoService = entry.getValue();
            if (!isMatch(userInfoService, authType)) {
                continue;
            }

            userResult = userInfoService.checkLogin(username, passwd, address, ext);
            if (null != userResult && Strings.isNullOrEmpty(userResult.getMessage())) {
                Class<?> userClass = ClassUtils.getUserClass(userInfoService.getClass());
                userResult.setBeanType(userClass.getTypeName());
                userResult.setAuthType(authType);
                userResult.setUid(Md5Utils.getInstance().getMd5String(
                        userResult.getId() +
                        userResult.getBeanType() +
                        username +
                        userResult.getAuthType()));
                break;
            }
        }


        if (null == userResult) {
            return ReturnResult.noAuth();
        }

        if (!Strings.isNullOrEmpty(userResult.getMessage())) {
            return ReturnResult.noAuth(userResult.getMessage());
        }

        TokenResolver tokenManagement = ServiceProvider.of(TokenResolver.class).getExtension(authServerProperties.getTokenManagement());
        ReturnResult<LoginResult> token = tokenManagement.createToken(address, userResult, authType);
        Integer code = token.getCode();
        if (null != code && code == 200) {
            LoginResult loginResult = token.getData();
            loginResult.setUserResult(userResult);
        }
        return token;
    }
}

package com.chua.starter.oauth.client.support.provider;

import com.chua.common.support.annotations.Ignore;
import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json5;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.google.common.base.Strings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.chua.common.support.constant.Constants.CAPTCHA_SESSION_KEY;
import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;
import static com.chua.common.support.lang.code.ReturnCode.USERNAME_OR_PASSWORD_ERROR;
import static com.chua.starter.common.support.constant.Constant.ADMIN;
import static com.chua.starter.common.support.utils.RequestUtils.getIpAddress;

/**
 * @author CH
 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "plugin.oauth.temp.open", havingValue = "true", matchIfMissing = true)
public class UserStatisticProvider {


    @Autowired
    private AuthClientProperties authProperties;
    /**
     * 登录
     *
     * @param loginData     登录信息
     * @param request       请求
     * @param response      响应
     * @param bindingResult 参数处理
     * @return 结果
     */
    @PostMapping("/login")
    @Ignore
    public ReturnResult<LoginResult> login(@Valid @RequestBody LoginData loginData,
                                     HttpServletRequest request,
                                     HttpServletResponse response,
                                     BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.failure(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }

        String code = loginData.getVerifyCodeKey();
        String sessionKey = Optional.ofNullable(request.getSession().getAttribute(CAPTCHA_SESSION_KEY)).orElse("").toString();
        if (Strings.isNullOrEmpty(code) || !code.equalsIgnoreCase(sessionKey)) {
            return ReturnResult.failure(REQUEST_PARAM_ERROR, "校验码错误");
        }

        String address = getIpAddress(request);
        AuthClientExecute clientExecute = AuthClientExecute.getInstance();
        LoginAuthResult accessToken = clientExecute.getAccessToken(loginData.getUsername(), loginData.getPassword(), AuthType.WEB,
                ImmutableBuilder.<String, Object>builderOfMap().put("address", address).build()
        );
        if (null == accessToken) {
            return ReturnResult.failure(USERNAME_OR_PASSWORD_ERROR, "账号或者密码不正确");
        }

        if (accessToken.getCode() != 200) {
            return ReturnResult.failure(REQUEST_PARAM_ERROR, accessToken.getMessage());
        }

        LoginResult loginResult = LoginResult.builder()
                .userInfo(getUserLoginInfo(accessToken.getToken()))
                .tokenType("Bearer")
                .accessToken(accessToken.getToken())
                .build();
        return ReturnResult.success(loginResult);
    }

    /**
     * 获取用户登录信息
     *
     * @param token 代币
     * @return {@link UserInfoVO}
     */
    public UserInfoVO getUserLoginInfo(String token) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null || StringUtils.isBlank(token)) {
            return new UserInfoVO();
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;
        attributes.getRequest().setAttribute(authProperties.getTokenName(), token);
        return getUserLoginInfo();
    }

    /**
     * 获取用户登录信息
     *
     * @return {@link UserInfoVO}
     */
    public UserInfoVO getUserLoginInfo() {
        UserResult userLoginInfo = AuthClientExecute.getInstance().getUserResult();
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setUserName(userLoginInfo.getUsername());
        userInfoVO.setUserRealName(userLoginInfo.getOption("userRealName"));
        userInfoVO.setNickname(userLoginInfo.getNickName());
        userInfoVO.setUserId(MapUtils.getString(userLoginInfo.getExt(), "userId"));
        userInfoVO.setUserMobile(MapUtils.getString(userLoginInfo.getExt(), "userMobile"));
        userInfoVO.setUserGender(MapUtils.getString(userLoginInfo.getExt(), "userGender"));
        userInfoVO.setUserEmail(MapUtils.getString(userLoginInfo.getExt(), "userEmail"));
        userInfoVO.setUserAddress(MapUtils.getString(userLoginInfo.getExt(), "userAddress"));
        userInfoVO.setUserSex(MapUtils.getInteger(userLoginInfo.getExt(), "userGender"));
        userInfoVO.setUserMarker(MapUtils.getString(userLoginInfo.getExt(), "userMarker"));
        userInfoVO.setAvatar(MapUtils.getString(userLoginInfo.getExt(), "userAvatar"));
        userInfoVO.setRoles(Optional.ofNullable(userLoginInfo.getRoles()).orElse(Collections.emptySet()));
        userInfoVO.setPerms(Optional.ofNullable(userLoginInfo.getPermission()).orElse(Collections.emptySet()));
        return userInfoVO;
    }

    /**
     * 注销
     *
     * @param uid      uid
     * @param request  要求
     * @param response 回答
     * @return {@link Result}
     */
    @DeleteMapping("/logout")
    public ReturnResult logout(@UserValue("token") String uid, HttpServletRequest request, HttpServletResponse response) {
        if (StringUtils.isEmpty(uid)) {
            return ReturnResult.success("注销成功");
        }
        AuthClientExecute clientExecute = AuthClientExecute.getInstance();
        LoginAuthResult accessToken = clientExecute.logout(uid, "WEB", LogoutType.LOGOUT);
        if (null == accessToken) {
            return ReturnResult.fail("退出失败请稍后重试");
        }

        if (accessToken.getCode() != 200) {
            return ReturnResult.fail(accessToken.getMessage());
        }

        CookieUtil.remove(request, response, "x-oauth-cookie");
        return ReturnResult.success("注销成功");
    }
    /**
     * 保存用户首页布局
     *
     * @param sysUserSetting 保存用户首页布局
     * @return 保存用户首页布局
     */
    @PostMapping("/grid")
    public ReturnResult<Boolean> grid(@RequestBody JsonObject sysUserSetting,
                                      @UserValue("userDashboardGrid") String userDashboardGrid,
                                      @UserValue("userId") Long userId,
                                      @UserValue("username") String username,
                                      @UserValue("roles") Set<String> roles
    ) {
        return ReturnResult.illegal("暂不支持服务器保存");
    }
    /**
     * 我菜单
     *
     * @param userId        用户id
     * @param userGrid      用户网格
     * @param userDashboard 用户面板
     * @param username      用户名
     * @param roles         角色
     * @return {@link Result}<{@link UserMenuResult}>
     */
    @GetMapping("/menus/my/**")
    public ReturnResult<UserMenuResult> myMenu(@UserValue("userId") String userId,
                                         @UserValue("userDashboardGrid") String userGrid,
                                         @UserValue("userDashboard") String userDashboard,
                                         @UserValue("username") String username,
                                         @UserValue("roles") Set<String> roles) {
        if(roles.isEmpty() && !ADMIN.equalsIgnoreCase(username)) {
            return ReturnResult.fail("联系管理员分配权限");
        }
        UserMenuResult userMenuResult = new UserMenuResult();
        userMenuResult.setPermissions(AuthClientExecute.getInstance().getUserResult().getPermission());
        Environment environment = SpringBeanUtils.getEnvironment();
        try (InputStream resourceAsStream = UserStatisticProvider.class.getResourceAsStream(StringUtils.defaultString(authProperties.getTemp().getMenuPath(), "/menu.json5"));){
            List<RouteVO> routeVOS =
                    Json5.fromJsonList(IoUtils.toString(resourceAsStream, StandardCharsets.UTF_8), RouteVO.class);
            List<RouteVO> result = new ArrayList<>(routeVOS.size());
            for (RouteVO routeVO : routeVOS) {
                String condition = routeVO.getCondition();
                if(StringUtils.isEmpty(condition)) {
                    result.add(routeVO);
                    continue;
                }

                Boolean aBoolean = false;
                if(condition.startsWith("#login.role.")) {
                    String substring = condition.substring(11);
                    aBoolean = ArrayUtils.containsAny(roles, substring.split(","), true);
                } else {
                    aBoolean = environment.getProperty(condition, Boolean.class);
                }
                if(null == aBoolean || aBoolean) {
                    result.add(routeVO);
                }

            }
            userMenuResult.setMenu(result);
        } catch (Exception ignored) {
            userMenuResult.setMenu(Collections.emptyList());
        }
        try {
            userMenuResult.setDashboardGrid(Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(userGrid));
        } catch (Exception ignored) {
        }
        userMenuResult.setDashboard(userDashboard);
        return ReturnResult.success(userMenuResult);
    }
}

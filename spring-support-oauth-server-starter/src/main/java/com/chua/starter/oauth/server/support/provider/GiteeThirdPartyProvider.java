package com.chua.starter.oauth.server.support.provider;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.condition.OnBeanCondition;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import com.chua.starter.oauth.server.support.properties.CasProperties;
import com.chua.starter.oauth.server.support.properties.ThirdPartyProperties;
import com.chua.starter.oauth.server.support.resolver.LoggerResolver;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthGiteeRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static com.chua.starter.common.support.result.ReturnCode.OK;
import static com.chua.starter.common.support.result.ReturnCode.SYSTEM_NO_OAUTH;

/**
 * 三方地址
 *
 * @author CH
 */
@Controller
@Conditional(OnBeanCondition.class)
@RequestMapping("${plugin.auth.server.context-path:/gitee}")
public class GiteeThirdPartyProvider implements InitializingBean {

    @Resource
    CasProperties casProperties;
    @Resource
    private ThirdPartyProperties thirdPartyProperties;
    private AuthGiteeRequest authRequest;
    @Resource
    private LoggerResolver loggerResolver;
    @Resource
    private LoginCheck loginCheck;
    @Value("${plugin.auth.server.context-path:}")
    private String contextPath;
    @Resource
    private AuthServerProperties authServerProperties;

    /**
     * gitee页面
     *
     * @return 登录页
     */
    @GetMapping("/gitee-index")
    public String thirdIndex(AuthCallback authCallback, HttpServletResponse response) {
        AuthResponse<AuthUser> authResponse = authRequest.login(authCallback);
        AuthUser data = authResponse.getData();
        if (null == data) {
            if (StringUtils.isNotBlank(casProperties.getNewLoginUrl())) {
                try {
                    return "redirect:" + contextPath + "/login?redirect_url=" +
                            URLEncoder.encode("", "UTF-8") + "&msg=" + URLEncoder.encode("操作超时", "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return "redirect:" + contextPath + "/login?redirect_url=" + "&msg=操作超时";
                }
            }
            try {
                return "redirect:" + casProperties.getNewLoginUrl() + "?redirect_url=" +
                        URLEncoder.encode("", "UTF-8") + "&msg=" + URLEncoder.encode("操作超时", "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return "redirect:" + casProperties.getNewLoginUrl() + "?redirect_url=" + "&msg=操作超时";
            }
        }
        ReturnResult<LoginResult> result = loginCheck.doLogin(data.getLocation(), data.getUsername(), null, "gitee", data);
        loggerResolver.register("gitee", result.getCode(), "认证服务器离线", null);
        if (SYSTEM_NO_OAUTH.getCode().equals(result.getCode())) {
            if (StringUtils.isNotBlank(casProperties.getNewLoginUrl())) {
                try {
                    return "redirect:" + contextPath + "/login?redirect_url=" +
                            URLEncoder.encode("", "UTF-8") + "&msg=" + URLEncoder.encode(result.getData().toString(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return "redirect:" + contextPath + "/login?redirect_url=" + "&msg=" + result.getData().toString();
                }
            }
            try {
                return "redirect:" + casProperties.getNewLoginUrl() + "?redirect_url=" +
                        URLEncoder.encode("", "UTF-8") + "&msg=" + URLEncoder.encode(result.getData().toString(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return "redirect:" + casProperties.getNewLoginUrl() + "?redirect_url=" + "&msg=" + result.getData().toString();
            }
        }

        LoginResult loginResult = result.getData();
        loggerResolver.register("gitee", OK.getCode(), "登录成功", null);
        CookieUtil.set(response, authServerProperties.getCookieName(), loginResult.getToken(), true);
        if (StringUtils.isNotBlank(casProperties.getNewLoginUrl())) {
            return "third-index";
        }
        return "redirect:" + casProperties.getNewIndexUrl();
    }
    /**
     * gitee页面
     *
     * @return 登录页
     */
    @ResponseBody
    @GetMapping("loginCodeType")
    public String gitee(@RequestParam("redirect_url") String url) {
        return authRequest.authorize(AuthStateUtils.createState());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.authRequest = new AuthGiteeRequest(AuthConfig.builder()
                .clientId(thirdPartyProperties.getGitee().getClientId())
                .clientSecret(thirdPartyProperties.getGitee().getClientSecret())
                .redirectUri(thirdPartyProperties.getGitee().getRedirectUri())
                .build());
    }
}

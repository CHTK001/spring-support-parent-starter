package com.chua.starter.oauth.server.support.gitee;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.condition.OnBeanCondition;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import com.chua.starter.oauth.server.support.properties.CasProperties;
import com.chua.starter.oauth.server.support.properties.ThirdPartyLoginProperties;
import com.chua.starter.oauth.server.support.resolver.LoggerResolver;
import com.chua.starter.oauth.server.support.token.TokenResolver;
import me.zhyd.oauth.cache.AuthDefaultStateCache;
import me.zhyd.oauth.cache.AuthStateCache;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthGiteeRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * gitee绑定账号
 *
 * @author CH
 */
@Controller
@Conditional(OnBeanCondition.class)
@RequestMapping("${plugin.auth.server.context-path:/gitee/login}")
public class GiteeLoginProvider implements InitializingBean , ApplicationContextAware {

    private GiteeService giteeService;
    @Resource
    CasProperties casProperties;
    @Resource
    private ThirdPartyLoginProperties thirdPartyLoginProperties;
    private AuthGiteeRequest authRequest;
    @Resource
    private LoggerResolver loggerResolver;
    @Resource
    private LoginCheck loginCheck;
    @Value("${plugin.auth.server.context-path:}")
    private String contextPath;
    @Resource
    private AuthServerProperties authServerProperties;
    private AuthStateCache authStateCache = AuthDefaultStateCache.INSTANCE;

    /**
     * gitee页面
     *
     * @return 登录页
     */
    @GetMapping("/callback")
    public RedirectView thirdIndex(AuthCallback authCallback, RedirectAttributes attributes, HttpServletResponse response) {
        AuthResponse<AuthUser> authResponse = authRequest.login(authCallback);
        RedirectView view = new RedirectView();
        view.setUrl("/");
        AuthUser data = authResponse.getData();
        if (null == data) {
            return view;
        }
        String state = authCallback.getState();
        String callback = authStateCache.get(state + "_callback");
        if(null != giteeService) {
            boolean doLogin = giteeService.doLogin(data);
            if(doLogin) {
                view.setUrl(StringUtils.defaultString(callback, "/"));
            }
        }
        return view;
    }
    /**
     * gitee页面
     *
     * @return 登录页
     */
    @ResponseBody
    @GetMapping("loginCodeType")
    public String gitee(@RequestParam("loginCode") String loginCode, String callback) {
        String state = AuthStateUtils.createState();
        authStateCache.cache(state + "_info", loginCode);
        try {
            authStateCache.cache(state + "_callback", URLDecoder.decode(callback, "UTF-8"));
        } catch (UnsupportedEncodingException ignored) {
        }
        return authRequest.authorize(AuthStateUtils.createState());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.authRequest = new AuthGiteeRequest(AuthConfig.builder()
                .clientId(thirdPartyLoginProperties.getGitee().getClientId())
                .clientSecret(thirdPartyLoginProperties.getGitee().getClientSecret())
                .redirectUri(thirdPartyLoginProperties.getGitee().getRedirectUri())
                .build(), authStateCache);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            giteeService = applicationContext.getBean(GiteeService.class);
        } catch (BeansException ignored) {
        }
    }
}

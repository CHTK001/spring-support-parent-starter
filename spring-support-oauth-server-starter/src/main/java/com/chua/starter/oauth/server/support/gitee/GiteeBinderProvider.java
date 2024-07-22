package com.chua.starter.oauth.server.support.gitee;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.condition.OnBeanCondition;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import com.chua.starter.oauth.server.support.properties.CasProperties;
import com.chua.starter.oauth.server.support.properties.ThirdPartyBinderProperties;
import com.chua.starter.oauth.server.support.resolver.LoggerResolver;
import com.chua.starter.oauth.server.support.token.TokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * gitee绑定账号
 *
 * @author CH
 */
@Controller
@Conditional(OnBeanCondition.class)
@RequiredArgsConstructor
@RequestMapping("${plugin.auth.server.context-path:/gitee/bind}")
public class GiteeBinderProvider implements InitializingBean , ApplicationContextAware {

    private GiteeService giteeService;
    final  CasProperties casProperties;
    final  ThirdPartyBinderProperties thirdPartyBinderProperties;
    private AuthGiteeRequest authRequest;

    private final AuthStateCache authStateCache = AuthDefaultStateCache.INSTANCE;
    final  LoggerResolver loggerResolver;
    final  LoginCheck loginCheck;
    @Value("${plugin.auth.server.context-path:}")
    private String contextPath;
    final  AuthServerProperties authServerProperties;

    /**
     * gitee页面
     *
     * @return 登录页
     */
    @GetMapping("/callback")
    public RedirectView thirdIndex(AuthCallback authCallback, RedirectAttributes attributes, HttpServletRequest request, HttpServletResponse response) {
        RedirectView view = new RedirectView();
        view.setUrl("/");
//        view.addStaticAttribute("Set-Cookie", CookieUtil.toString(request.getCookies()));
        AuthResponse<AuthUser> authResponse = authRequest.login(authCallback);
        AuthUser data = authResponse.getData();
        if (null == data) {
            return view;
        }
        String state = authCallback.getState();
        String token = authStateCache.get(state + "_info");
        String callback = authStateCache.get(state + "_callback");
        if(StringUtils.isBlank(token)) {
            return view;
        }

        String tokenManagement = authServerProperties.getTokenManagement();
        TokenResolver tokenResolver = ServiceProvider.of(TokenResolver.class).getExtension(tokenManagement);
        ReturnResult<UserResult> result = tokenResolver.resolve(null, token);
        if(null != giteeService) {
            giteeService.binder(data, result.getData());
        }
        view.setUrl(callback);
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
        authStateCache.cache(state + "_callback", URLDecoder.decode(callback, StandardCharsets.UTF_8));
        return authRequest.authorize(state);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.authRequest = new AuthGiteeRequest(AuthConfig.builder()
                .clientId(thirdPartyBinderProperties.getGitee().getClientId())
                .clientSecret(thirdPartyBinderProperties.getGitee().getClientSecret())
                .redirectUri(thirdPartyBinderProperties.getGitee().getRedirectUri())
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

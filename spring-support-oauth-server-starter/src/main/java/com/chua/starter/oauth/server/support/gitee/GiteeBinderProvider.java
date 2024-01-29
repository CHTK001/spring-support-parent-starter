package com.chua.starter.oauth.server.support.gitee;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.condition.OnBeanCondition;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import com.chua.starter.oauth.server.support.properties.CasProperties;
import com.chua.starter.oauth.server.support.properties.ThirdPartyBinderProperties;
import com.chua.starter.oauth.server.support.resolver.LoggerResolver;
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

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * gitee绑定账号
 *
 * @author CH
 */
@Controller
@Conditional(OnBeanCondition.class)
@RequestMapping("${plugin.auth.server.context-path:/gitee/bind}")
public class GiteeBinderProvider implements InitializingBean , ApplicationContextAware {

    private GiteeService giteeService;
    @Resource
    CasProperties casProperties;
    @Resource
    private ThirdPartyBinderProperties thirdPartyBinderProperties;
    private AuthGiteeRequest authRequest;

    private AuthStateCache authStateCache = AuthDefaultStateCache.INSTANCE;
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
    @GetMapping("/callback")
    @ResponseBody
    public String thirdIndex(AuthCallback authCallback, RedirectAttributes attributes, HttpServletRequest request, HttpServletResponse response) {
        AuthResponse<AuthUser> authResponse = authRequest.login(authCallback);
        AuthUser data = authResponse.getData();
        if (null == data) {
            return "操作超时";
        }
        String state = authCallback.getState();
        String string = authStateCache.get(state + "_info");
        if(StringUtils.isBlank(string)) {
            return "请重新登录";
        }
        if(null != giteeService) {
            giteeService.binder(data, string);
        }

        return "绑定失败";
    }
    /**
     * gitee页面
     *
     * @return 登录页
     */
    @ResponseBody
    @GetMapping("loginCodeType")
    public String gitee(@RequestParam("loginCode") String loginCode) {
        String state = AuthStateUtils.createState();
        authStateCache.cache(state + "_info", loginCode);
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

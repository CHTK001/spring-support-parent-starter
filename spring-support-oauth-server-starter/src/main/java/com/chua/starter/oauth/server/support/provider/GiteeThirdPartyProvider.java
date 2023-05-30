package com.chua.starter.oauth.server.support.provider;

import com.chua.starter.oauth.server.support.condition.OnBeanCondition;
import com.chua.starter.oauth.server.support.properties.ThirdPartyProperties;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.request.AuthGiteeRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;

/**
 * 三方地址
 * @author CH
 */
@Controller
@Conditional(OnBeanCondition.class)
@RequestMapping("${plugin.auth.server.context-path: /gitee}")
public class GiteeThirdPartyProvider implements InitializingBean {

    @Resource
    private ThirdPartyProperties thirdPartyProperties;
    private AuthGiteeRequest authRequest;

    /**
     * gitee页面
     *
     * @return 登录页
     */
    @ResponseBody
    @GetMapping("/gitee-index")
    public String thirdIndex(AuthCallback authCallback) {
        return "third-index";
    }
    /**
     * gitee页面
     *
     * @return 登录页
     */
    @ResponseBody
    @GetMapping()
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
